(ns blaze.db.impl.index
  "This namespace contains query functions."
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.plan :as plan]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.search-param-registry :as sr]))

(defn- resolve-search-clause
  [registry type [param & values :as clause] lenient?]
  (let [values (cond->> values (< 1 (count values)) (into [] (distinct)))]
    (if (empty? values)
      (ac/completed-future (ba/incorrect (format "Clause `%s` isn't valid." clause)))
      (if-ok [[search-param modifier] (sr/parse registry type param)
              _ (search-param/validate-modifier search-param modifier)]
        (-> (search-param/compile-values search-param modifier values)
            (ac/then-apply (fn [cv] [search-param modifier values cv])))
        #(ac/completed-future (if lenient? nil %))))))

(defn- resolve-sort-clause
  [registry type [_ param direction :as clause]]
  (cond
    (not (#{:asc :desc} direction))
    (ba/incorrect (format "Clause `%s` isn't valid." clause))

    (not (#{"_id" "_lastUpdated" "birthdate"} param))
    (ba/incorrect (format "Unknown search-param `%s` in sort clause." param))

    (and (= "_id" param) (= :desc direction))
    (ba/unsupported "Unsupported sort direction `desc` for search param `_id`.")

    :else
    (let [[search-param] (sr/parse registry type param)]
      [search-param (name direction) [] []])))

(defn- resolve-search-clause-only [registry type clause lenient?]
  (if (identical? :sort (first clause))
    (ac/completed-future (ba/incorrect "Sort clauses are only allowed at first position."))
    (resolve-search-clause registry type clause lenient?)))

(defn- multiple-clauses? [disjunction]
  (vector? (first disjunction)))

(defn- postprocessing?
  "Returns true if matches of `clause` need postprocessing.

  Postprocess filters are created without a db, because they only close
  over it."
  [[search-param modifier values compiled-values]]
  (some? (p/-postprocess-matches search-param nil modifier values
                                 compiled-values)))

(defn- validate-disjunction
  "Returns `clauses` or an unsupported anomaly if one of multiple `clauses`
  needs postprocessing.

  Postprocess filters only refine the matches of their own clause. Matches of a
  disjunction can come from any of its clauses, so postprocessing them would
  need to re-match every clause."
  [clauses]
  (if-let [[{:keys [code]}] (when (< 1 (count clauses))
                              (some #(when (postprocessing? %) %) clauses))]
    (ba/unsupported
     (format "Unsupported search param `%s` in a disjunction with other clauses."
             code))
    clauses))

(defn- resolve-disjunction [registry type disjunction lenient?]
  (let [clause-futures
        (mapv #(resolve-search-clause-only registry type % lenient?)
              (cond-> disjunction (not (multiple-clauses? disjunction)) vector))]
    (-> (ac/all-of clause-futures)
        (ac/then-apply
         (fn [_] (validate-disjunction (into [] (keep ac/join) clause-futures)))))))

(defn resolve-search-params* [registry type clauses lenient?]
  (let [disjunction-futures
        (mapv #(resolve-disjunction registry type % lenient?) clauses)]
    (-> (ac/all-of disjunction-futures)
        (ac/then-apply
         (fn [_]
           (into [] (comp (map ac/join) (remove empty?)) disjunction-futures))))))

(defn resolve-search-params
  "Resolves all search parameter `clauses` for resource `type` against
  `registry` in parallel.

  Returns a CompletableFuture that completes with a map of
  `:search-clauses` and optionally `:sort-clause` or completes exceptionally
  with an anomaly if any clause references an unknown search parameter and
  `lenient?` is false.

  When `lenient?` is true, clauses that reference unknown search
  parameters are silently dropped instead of causing a failure."
  [registry type clauses lenient?]
  (if (identical? :sort (ffirst clauses))
    (if-ok [sort-clause (resolve-sort-clause registry type (first clauses))]
      (-> (resolve-search-params* registry type (rest clauses) lenient?)
          (ac/then-apply
           (fn [search-clauses]
             (cond-> {:sort-clause sort-clause}
               (seq search-clauses)
               (assoc :search-clauses search-clauses)))))
      ac/completed-future)
    (-> (resolve-search-params* registry type clauses lenient?)
        (ac/then-apply
         (fn [search-clauses]
           (cond-> {}
             (seq search-clauses)
             (assoc :search-clauses search-clauses)))))))

(defn- matcher [db [search-param modifier _ values]]
  (search-param/matcher search-param db modifier values))

(defn other-clauses-resource-handle-filter*
  [db disjunction]
  (if (= 1 (count disjunction))
    (matcher db (first disjunction))
    (coll/disjunction-filter (mapv (partial matcher db) disjunction))))

(defn other-clauses-resource-handle-filter
  "Creates a filter transducer over resource handles for all clauses in
  `conjunction` by possibly composing multiple filter transducers for each
  clause."
  [db conjunction]
  (transduce
   (map (partial other-clauses-resource-handle-filter* db))
   comp
   conjunction))

(defn- single-version-id-matcher [db tid [search-param modifier _ values]]
  (search-param/single-version-id-matcher search-param db tid modifier values))

(defn- other-clauses-single-version-id-filter*
  [db tid disjunction]
  (if (= 1 (count disjunction))
    (single-version-id-matcher db tid (first disjunction))
    (coll/disjunction-filter
     (mapv (partial single-version-id-matcher db tid) disjunction))))

(defn- other-clauses-single-version-id-filter
  [db tid conjunction]
  (transduce
   (map (partial other-clauses-single-version-id-filter* db tid))
   comp
   conjunction))

(defn- other-clauses-index-handle-filter
  "Creates a filter transducer over index handles for all clauses in
  `conjunction` by possibly composing multiple filter transducers for each
  clause."
  [db tid conjunction]
  (comp
   (mapcat ih/to-single-version-ids)
   (other-clauses-single-version-id-filter db tid conjunction)
   u/by-id-grouper))

(defn- index-handles*
  ([db tid [search-param modifier _ compiled-values]]
   (search-param/index-handles search-param db tid modifier
                               compiled-values))
  ([db tid [search-param modifier _ compiled-values] start-id]
   (search-param/index-handles search-param db tid modifier
                               compiled-values start-id)))

(defn- index-handles [db tid clauses]
  (if (= 1 (count clauses))
    (index-handles* db tid (first clauses))
    (coll/eduction
     (mapcat (partial index-handles* db tid))
     clauses)))

(defn- sorted-index-handles
  ([db tid [search-param modifier]]
   (condp = modifier
     "asc" (search-param/sorted-index-handles search-param db tid :asc)
     "desc" (search-param/sorted-index-handles search-param db tid :desc)))
  ([db tid [search-param modifier] start-id]
   (condp = modifier
     "asc" (search-param/sorted-index-handles search-param db tid :asc start-id)
     "desc" (search-param/sorted-index-handles search-param db tid :desc start-id))))

(defn- ordered-index-handles**
  ([db tid [search-param modifier _ compiled-values]]
   (search-param/ordered-index-handles search-param db tid modifier
                                       compiled-values))
  ([db tid [search-param modifier _ compiled-values] start-id]
   (search-param/ordered-index-handles search-param db tid modifier
                                       compiled-values start-id)))

(defn- ordered-index-handles*
  ([db tid disjunction]
   (if (= 1 (count disjunction))
     (ordered-index-handles** db tid (first disjunction))
     (let [f #(ordered-index-handles** db tid %)]
       (u/union-index-handles (map f disjunction)))))
  ([db tid disjunction start-id]
   (if (= 1 (count disjunction))
     (ordered-index-handles** db tid (first disjunction) start-id)
     (let [f #(ordered-index-handles** db tid % start-id)]
       (u/union-index-handles (map f disjunction))))))

(defn- ordered-index-handles
  ([db tid conjunction]
   (if (= 1 (count conjunction))
     (ordered-index-handles* db tid (first conjunction))
     (let [f #(ordered-index-handles* db tid %)]
       (u/intersection-index-handles (map f conjunction)))))
  ([db tid conjunction start-id]
   (if (= 1 (count conjunction))
     (ordered-index-handles* db tid (first conjunction) start-id)
     (let [f #(ordered-index-handles* db tid % start-id)]
       (u/intersection-index-handles (map f conjunction))))))

(defn- postprocess-matches** [db [search-param modifier values compiled-values]]
  (p/-postprocess-matches search-param db modifier values compiled-values))

(defn- postprocess-matches* [db disjunction]
  (transduce (keep (partial postprocess-matches** db)) comp disjunction))

(defn- postprocess-matches [db conjunction]
  (transduce (keep (partial postprocess-matches* db)) comp conjunction))

(defn- supports-ordered-index-handles
  [db tid [search-param modifier _ compiled-values]]
  (p/-supports-ordered-index-handles search-param db tid modifier
                                     compiled-values))

(defn- group-by-ordered-index-handle-support
  "Returns two groups, true and false."
  [db tid clauses]
  (group-by (partial every? (partial supports-ordered-index-handles db tid)) clauses))

(defn- type-query-plan*
  "Splits `clauses` into two groups. The first group of clauses should be
  scanned, while the second should be seeked.

  For the scan clauses, statistics are used. Clauses with a large estimated scan
  sizes are promoted to seek."
  [db tid clauses]
  (let [{ordered-support-clauses true other-clauses false}
        (group-by-ordered-index-handle-support db tid clauses)]
    (cond
      (= 1 (count ordered-support-clauses))
      [ordered-support-clauses other-clauses]

      (seq ordered-support-clauses)
      (let [{small-clauses :small large-clauses :large}
            (plan/group-by-estimated-scan-size db tid ordered-support-clauses)]
        [small-clauses
         (into (or large-clauses []) other-clauses)])

      :else
      [nil other-clauses])))

(defn- resource-handle-mapper*
  ([db tid all-clauses]
   (comp (u/resource-handle-xf db tid)
         (postprocess-matches db all-clauses)))
  ([db tid all-clauses other-clauses]
   (comp (other-clauses-index-handle-filter db tid other-clauses)
         (resource-handle-mapper* db tid all-clauses))))

(defn- resource-handle-mapper [db tid all-clauses other-clauses]
  (if (seq other-clauses)
    (resource-handle-mapper* db tid all-clauses other-clauses)
    (resource-handle-mapper* db tid all-clauses)))

(defn ordered-resource-handles
  ([db tid scan-clauses other-clauses]
   (coll/eduction
    (resource-handle-mapper db tid scan-clauses other-clauses)
    (ordered-index-handles db tid scan-clauses)))
  ([db tid scan-clauses other-clauses start-id]
   (coll/eduction
    (resource-handle-mapper db tid scan-clauses other-clauses)
    (ordered-index-handles db tid scan-clauses start-id))))

(defn- unordered-resource-handles
  ([db tid [first-clause & other-clauses :as clauses]]
   (coll/eduction
    (comp (resource-handle-mapper db tid clauses other-clauses)
          u/distinct-by-id)
    (index-handles db tid first-clause)))
  ([db tid [first-clause & other-clauses :as clauses] start-id]
   (if (and (= 1 (count first-clause))
            (= 1 (count (peek (first first-clause)))))
     (coll/eduction
      (comp (resource-handle-mapper db tid clauses other-clauses)
            u/distinct-by-id)
      (index-handles* db tid (first first-clause) start-id))
     (coll/eduction
      (u/drop-while-not-start-id start-id)
      (unordered-resource-handles db tid clauses)))))

(defn type-query
  "Returns a reducible collection of resource handles from `db` of type
  with `tid` that satisfy `clauses`, optionally starting with `start-id`."
  {:arglists '([db tid clauses] [db tid clauses start-id])}
  ([db tid {:keys [sort-clause search-clauses]}]
   (if sort-clause
     (coll/eduction
      (comp (resource-handle-mapper db tid search-clauses search-clauses)
            u/distinct-by-id)
      (sorted-index-handles db tid sort-clause))
     (let [[scan-clauses other-clauses] (type-query-plan* db tid search-clauses)]
       (if (seq scan-clauses)
         (ordered-resource-handles db tid scan-clauses other-clauses)
         (unordered-resource-handles db tid other-clauses)))))
  ([db tid {:keys [sort-clause search-clauses]} start-id]
   (if sort-clause
     (coll/eduction
      (comp (resource-handle-mapper db tid search-clauses search-clauses)
            u/distinct-by-id
            (u/drop-while-not-start-id start-id))
      (sorted-index-handles db tid sort-clause start-id))
     (let [[scan-clauses other-clauses] (type-query-plan* db tid search-clauses)]
       (if (seq scan-clauses)
         (ordered-resource-handles db tid scan-clauses other-clauses start-id)
         (unordered-resource-handles db tid other-clauses start-id))))))

(defn- clause-stats* [[{:keys [code]} modifier values]]
  {:code code
   :modifier modifier
   :values values})

(defn- clause-stats [clauses]
  (coll/eduction (map clause-stats*) clauses))

(defn type-query-plan
  {:arglists '([db tid clauses])}
  [db tid {:keys [sort-clause search-clauses]}]
  (if sort-clause
    {:query-type :type
     :scan-type :ordered
     :scan-clauses [(clause-stats* sort-clause)]
     :seek-clauses (into [] (mapcat clause-stats) search-clauses)}
    (let [[scan-clauses other-clauses] (type-query-plan* db tid search-clauses)]
      (if (seq scan-clauses)
        {:query-type :type
         :scan-type :ordered
         :scan-clauses (into [] (mapcat clause-stats) scan-clauses)
         :seek-clauses (into [] (mapcat clause-stats) other-clauses)}
        {:query-type :type
         :scan-type :unordered
         :scan-clauses (mapv clause-stats* (first other-clauses))
         :seek-clauses (into [] (mapcat clause-stats) (rest other-clauses))}))))

(defn- sum-future-counts [xform coll]
  (let [futures (into [] xform coll)]
    (do-sync [_ (ac/all-of futures)]
      (transduce (map ac/join) + futures))))

(defn- chunk-counter
  "Splits inputs into chunks, processes `chunk-xf` for each chunk before it will
  be counted.

  Emits futures of counts for each chunk."
  [chunk-xf]
  (comp
   (coll/partition-all 10000)
   (map #(ac/supply-async (fn [] (count (coll/eduction chunk-xf %)))))))

(defn type-query-total
  "Returns a CompletableFuture that will complete with the count of the
  matching resource handles."
  {:arglists '([db tid clauses])}
  [db tid search-clauses]
  (if (seq search-clauses)
    (let [[scan-clauses other-clauses] (type-query-plan* db tid search-clauses)]
      (if (seq scan-clauses)
        (sum-future-counts
         (chunk-counter
          (resource-handle-mapper db tid scan-clauses other-clauses))
         (ordered-index-handles db tid scan-clauses))
        (ac/completed-future
         (count (unordered-resource-handles db tid other-clauses)))))
    (ac/completed-future (p/-type-total db tid))))

(defn compartment-clauses
  [search-param-registry compartment-code search-param-codes type]
  (let [clauses [(mapv #(vector % (str compartment-code "/0")) search-param-codes)]]
    (-> (resolve-search-params search-param-registry type clauses false)
        (ac/then-apply :search-clauses))))

(defn- supports-ordered-compartment-index-handles [[search-param modifier values]]
  (p/-supports-ordered-compartment-index-handles search-param modifier values))

(defn compartment-query-plan* [search-clauses]
  (let [{scan-clauses true other-clauses false}
        (group-by (partial every? supports-ordered-compartment-index-handles) search-clauses)]
    [scan-clauses other-clauses]))

(defn- ordered-compartment-index-handles**
  ([db compartment tid [search-param modifier _ compiled-values]]
   (search-param/ordered-compartment-index-handles
    search-param db compartment tid modifier compiled-values))
  ([db compartment tid [search-param modifier _ compiled-values] start-id]
   (search-param/ordered-compartment-index-handles
    search-param db compartment tid modifier compiled-values start-id)))

(defn- ordered-compartment-index-handles*
  ([db compartment tid disjunction]
   (if (= 1 (count disjunction))
     (ordered-compartment-index-handles** db compartment tid (first disjunction))
     (let [f #(ordered-compartment-index-handles** db compartment tid %)]
       (u/union-index-handles (map f disjunction)))))
  ([db compartment tid disjunction start-id]
   (if (= 1 (count disjunction))
     (ordered-compartment-index-handles** db compartment tid (first disjunction) start-id)
     (let [f #(ordered-compartment-index-handles** db compartment tid % start-id)]
       (u/union-index-handles (map f disjunction))))))

(defn- ordered-compartment-index-handles
  ([db compartment tid conjunction]
   (if (= 1 (count conjunction))
     (ordered-compartment-index-handles* db compartment tid (first conjunction))
     (let [f #(ordered-compartment-index-handles* db compartment tid %)]
       (u/intersection-index-handles (map f conjunction)))))
  ([db compartment tid conjunction start-id]
   (if (= 1 (count conjunction))
     (ordered-compartment-index-handles* db compartment tid (first conjunction) start-id)
     (let [f #(ordered-compartment-index-handles* db compartment tid % start-id)]
       (u/intersection-index-handles (map f conjunction))))))

(defn compartment-query
  "Returns a reducible collection of resource handles from `db` in
  `compartment` of type with `tid` that satisfy `scan-clauses` and possible
  empty `other-clauses`, optionally starting with `start-id`."
  ([db compartment tid scan-clauses other-clauses]
   (coll/eduction
    (resource-handle-mapper db tid scan-clauses other-clauses)
    (ordered-compartment-index-handles db compartment tid scan-clauses)))
  ([db compartment tid scan-clauses other-clauses start-id]
   (coll/eduction
    (resource-handle-mapper db tid scan-clauses other-clauses)
    (ordered-compartment-index-handles db compartment tid scan-clauses start-id))))

(defn compartment-query-plan [scan-clauses other-clauses]
  (cond->
   {:query-type :compartment
    :seek-clauses (into [] (mapcat clause-stats) other-clauses)}
    (seq scan-clauses)
    (assoc :scan-type :ordered
           :scan-clauses (into [] (mapcat clause-stats) scan-clauses))))
