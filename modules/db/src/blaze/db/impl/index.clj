(ns blaze.db.impl.index
  "This namespace contains query functions."
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.compartment.resource :as cr]
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
      (ba/incorrect (format "Clause `%s` isn't valid." clause))
      (if-ok [[search-param modifier] (sr/parse registry type param)
              _ (search-param/validate-modifier search-param modifier)]
        (when-ok [compiled-values (search-param/compile-values search-param modifier values)]
          [search-param modifier values compiled-values])
        #(if lenient? nil %)))))

(defn- resolve-sort-clause
  [registry type [_ param direction :as clause]]
  (cond
    (not (#{:asc :desc} direction))
    (ba/incorrect (format "Clause `%s` isn't valid." clause))

    (not (#{"_id" "_lastUpdated"} param))
    (ba/incorrect (format "Unknown search-param `%s` in sort clause." param))

    (and (= "_id" param) (= :desc direction))
    (ba/unsupported "Unsupported sort direction `desc` for search param `_id`.")

    :else
    (let [[search-param] (sr/parse registry type param)]
      [search-param (name direction) [] []])))

(defn- resolve-search-clause-only [registry type clause lenient?]
  (if (identical? :sort (first clause))
    (ba/incorrect "Sort clauses are only allowed at first position.")
    (resolve-search-clause registry type clause lenient?)))

(defn- multiple-clauses? [disjunction]
  (vector? (first disjunction)))

(defn resolve-search-params* [registry type clauses lenient?]
  (transduce
   (comp
    (map
     (fn [disjunction]
       (transduce
        (comp (keep #(resolve-search-clause-only registry type % lenient?))
              (halt-when ba/anomaly?))
        conj
        []
        (cond-> disjunction (not (multiple-clauses? disjunction)) vector))))
    (filter seq)
    (halt-when ba/anomaly?))
   conj
   []
   clauses))

(defn resolve-search-params [registry type clauses lenient?]
  (if (identical? :sort (ffirst clauses))
    (when-ok [sort-clause (resolve-sort-clause registry type (first clauses))
              search-clauses (resolve-search-params* registry type (rest clauses) lenient?)]
      (cond-> {:sort-clause sort-clause}
        (seq search-clauses)
        (assoc :search-clauses search-clauses)))
    (when-ok [search-clauses (resolve-search-params* registry type clauses lenient?)]
      (cond-> {}
        (seq search-clauses)
        (assoc :search-clauses search-clauses)))))

(defn- matcher [batch-db [search-param modifier _ values]]
  (search-param/matcher search-param batch-db modifier values))

(defn other-clauses-resource-handle-filter*
  [batch-db disjunction]
  (if (= 1 (count disjunction))
    (matcher batch-db (first disjunction))
    (let [filters (map #((matcher batch-db %) (fn [_ _] true)) disjunction)]
      (filter
       (fn [resource-handle]
         (some #(% nil resource-handle) filters))))))

(defn other-clauses-resource-handle-filter
  "Creates a filter transducer over resource handles for all clauses in
  `conjunction` by possibly composing multiple filter transducers for each
  clause."
  [batch-db conjunction]
  (transduce
   (map (partial other-clauses-resource-handle-filter* batch-db))
   comp
   conjunction))

(defn- single-version-id-matcher [batch-db tid [search-param modifier _ values]]
  (search-param/single-version-id-matcher search-param batch-db tid modifier values))

(defn- other-clauses-single-version-id-filter*
  [batch-db tid disjunction]
  (if (= 1 (count disjunction))
    (single-version-id-matcher batch-db tid (first disjunction))
    (let [filters (map #((single-version-id-matcher batch-db tid %) (fn [_ _] true)) disjunction)]
      (filter
       (fn [single-version-id]
         (some #(% nil single-version-id) filters))))))

(defn- other-clauses-single-version-id-filter
  [batch-db tid conjunction]
  (transduce
   (map (partial other-clauses-single-version-id-filter* batch-db tid))
   comp
   conjunction))

(defn- other-clauses-index-handle-filter
  "Creates a filter transducer over index handles for all clauses in
  `conjunction` by possibly composing multiple filter transducers for each
  clause."
  [batch-db tid conjunction]
  (comp
   (mapcat ih/to-single-version-ids)
   (other-clauses-single-version-id-filter batch-db tid conjunction)
   u/by-id-grouper))

(defn- index-handles*
  ([batch-db tid [search-param modifier _ compiled-values]]
   (search-param/index-handles search-param batch-db tid modifier
                               compiled-values))
  ([batch-db tid [search-param modifier _ compiled-values] start-id]
   (search-param/index-handles search-param batch-db tid modifier
                               compiled-values start-id)))

(defn- index-handles [batch-db tid clauses]
  (if (= 1 (count clauses))
    (index-handles* batch-db tid (first clauses))
    (coll/eduction
     (mapcat (partial index-handles* batch-db tid))
     clauses)))

(defn- sorted-index-handles
  ([batch-db tid [search-param modifier]]
   (condp = modifier
     "asc" (search-param/sorted-index-handles search-param batch-db tid :asc)
     "desc" (search-param/sorted-index-handles search-param batch-db tid :desc)))
  ([batch-db tid [search-param modifier] start-id]
   (condp = modifier
     "asc" (search-param/sorted-index-handles search-param batch-db tid :asc start-id)
     "desc" (search-param/sorted-index-handles search-param batch-db tid :desc start-id))))

(defn- ordered-index-handles**
  ([batch-db tid [search-param modifier _ compiled-values]]
   (search-param/ordered-index-handles search-param batch-db tid modifier
                                       compiled-values))
  ([batch-db tid [search-param modifier _ compiled-values] start-id]
   (search-param/ordered-index-handles search-param batch-db tid modifier
                                       compiled-values start-id)))

(defn- ordered-index-handles*
  ([batch-db tid disjunction]
   (if (= 1 (count disjunction))
     (ordered-index-handles** batch-db tid (first disjunction))
     (let [f #(ordered-index-handles** batch-db tid %)]
       (u/union-index-handles (map f disjunction)))))
  ([batch-db tid disjunction start-id]
   (if (= 1 (count disjunction))
     (ordered-index-handles** batch-db tid (first disjunction) start-id)
     (let [f #(ordered-index-handles** batch-db tid % start-id)]
       (u/union-index-handles (map f disjunction))))))

(defn- ordered-index-handles
  ([batch-db tid conjunction]
   (if (= 1 (count conjunction))
     (ordered-index-handles* batch-db tid (first conjunction))
     (let [f #(ordered-index-handles* batch-db tid %)]
       (u/intersection-index-handles (map f conjunction)))))
  ([batch-db tid conjunction start-id]
   (if (= 1 (count conjunction))
     (ordered-index-handles* batch-db tid (first conjunction) start-id)
     (let [f #(ordered-index-handles* batch-db tid % start-id)]
       (u/intersection-index-handles (map f conjunction))))))

(defn- postprocess-matches** [batch-db [search-param _ values compiled-values]]
  (p/-postprocess-matches search-param batch-db values compiled-values))

(defn- postprocess-matches* [batch-db disjunction]
  (transduce (keep (partial postprocess-matches** batch-db)) comp disjunction))

(defn- postprocess-matches [batch-db conjunction]
  (transduce (keep (partial postprocess-matches* batch-db)) comp conjunction))

(defn- supports-ordered-index-handles
  [batch-db tid [search-param modifier _ compiled-values]]
  (p/-supports-ordered-index-handles search-param batch-db tid modifier
                                     compiled-values))

(defn- group-by-ordered-index-handle-support
  "Returns two groups, true and false."
  [batch-db tid clauses]
  (group-by (partial every? (partial supports-ordered-index-handles batch-db tid)) clauses))

(defn- type-query-plan*
  "Splits `clauses` into two groups. The first group of clauses should be
  scanned, while the second should be seeked.

  For the scan clauses, statistics are used. Clauses with a large estimated scan
  sizes are promoted to seek."
  [batch-db tid clauses]
  (let [{ordered-support-clauses true other-clauses false}
        (group-by-ordered-index-handle-support batch-db tid clauses)]
    (cond
      (= 1 (count ordered-support-clauses))
      [ordered-support-clauses other-clauses]

      (seq ordered-support-clauses)
      (let [{small-clauses :small large-clauses :large}
            (plan/group-by-estimated-scan-size batch-db tid ordered-support-clauses)]
        [small-clauses
         (into large-clauses other-clauses)])

      :else
      [nil other-clauses])))

(defn- resource-handle-mapper*
  ([batch-db tid all-clauses]
   (comp (u/resource-handle-xf batch-db tid)
         (postprocess-matches batch-db all-clauses)))
  ([batch-db tid all-clauses other-clauses]
   (comp (other-clauses-index-handle-filter batch-db tid other-clauses)
         (resource-handle-mapper* batch-db tid all-clauses))))

(defn- resource-handle-mapper [batch-db tid all-clauses other-clauses]
  (if (seq other-clauses)
    (resource-handle-mapper* batch-db tid all-clauses other-clauses)
    (resource-handle-mapper* batch-db tid all-clauses)))

(defn- ordered-resource-handles
  ([batch-db tid scan-clauses other-clauses]
   (coll/eduction
    (resource-handle-mapper batch-db tid scan-clauses other-clauses)
    (ordered-index-handles batch-db tid scan-clauses)))
  ([batch-db tid scan-clauses other-clauses start-id]
   (coll/eduction
    (resource-handle-mapper batch-db tid scan-clauses other-clauses)
    (ordered-index-handles batch-db tid scan-clauses start-id))))

(defn- unordered-resource-handles
  ([batch-db tid [first-clause & other-clauses :as clauses]]
   (coll/eduction
    (comp (resource-handle-mapper batch-db tid clauses other-clauses)
          (distinct))
    (index-handles batch-db tid first-clause)))
  ([batch-db tid [first-clause & other-clauses :as clauses] start-id]
   (if (and (= 1 (count first-clause))
            (= 1 (count (peek (first first-clause)))))
     (coll/eduction
      (comp (resource-handle-mapper batch-db tid clauses other-clauses)
            (distinct))
      (index-handles* batch-db tid (first first-clause) start-id))
     (let [start-id (codec/id-string start-id)]
       (coll/eduction
        (drop-while #(not= start-id (:id %)))
        (unordered-resource-handles batch-db tid clauses))))))

(defn type-query
  "Returns a reducible collection of resource handles from `batch-db` of type
  with `tid` that satisfy `clauses`, optionally starting with `start-id`."
  {:arglists '([batch-db tid clauses] [batch-db tid clauses start-id])}
  ([batch-db tid {:keys [sort-clause search-clauses]}]
   (if sort-clause
     (coll/eduction
      (resource-handle-mapper batch-db tid search-clauses search-clauses)
      (sorted-index-handles batch-db tid sort-clause))
     (let [[scan-clauses other-clauses] (type-query-plan* batch-db tid search-clauses)]
       (if (seq scan-clauses)
         (ordered-resource-handles batch-db tid scan-clauses other-clauses)
         (unordered-resource-handles batch-db tid other-clauses)))))
  ([batch-db tid {:keys [sort-clause search-clauses]} start-id]
   (if sort-clause
     (coll/eduction
      (resource-handle-mapper batch-db tid search-clauses search-clauses)
      (sorted-index-handles batch-db tid sort-clause start-id))
     (let [[scan-clauses other-clauses] (type-query-plan* batch-db tid search-clauses)]
       (if (seq scan-clauses)
         (ordered-resource-handles batch-db tid scan-clauses other-clauses start-id)
         (unordered-resource-handles batch-db tid other-clauses start-id))))))

(defn- clause-stats* [[{:keys [code]} modifier values]]
  {:code code
   :modifier modifier
   :values values})

(defn- clause-stats [clauses]
  (coll/eduction (map clause-stats*) clauses))

(defn type-query-plan
  {:arglists '([batch-db tid clauses])}
  [batch-db tid {:keys [sort-clause search-clauses]}]
  (if sort-clause
    {:query-type :type
     :scan-type :ordered
     :scan-clauses [(clause-stats* sort-clause)]
     :seek-clauses (into [] (mapcat clause-stats) search-clauses)}
    (let [[scan-clauses other-clauses] (type-query-plan* batch-db tid search-clauses)]
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
   (partition-all 10000)
   (map #(ac/supply-async (fn [] (count (coll/eduction chunk-xf %)))))))

(defn type-query-total
  "Returns a CompletableFuture that will complete with the count of the
  matching resource handles."
  {:arglists '([batch-db tid clauses])}
  [batch-db tid search-clauses]
  (if (seq search-clauses)
    (let [[scan-clauses other-clauses] (type-query-plan* batch-db tid search-clauses)]
      (if (seq scan-clauses)
        (sum-future-counts
         (chunk-counter
          (resource-handle-mapper batch-db tid scan-clauses other-clauses))
         (ordered-index-handles batch-db tid scan-clauses))
        (ac/completed-future
         (count (unordered-resource-handles batch-db tid other-clauses)))))
    (ac/completed-future (p/-type-total batch-db tid))))

(defn system-query [_ _]
  ;; TODO: implement
  [])

(defn- supports-ordered-compartment-index-handles [[search-param _ values]]
  (p/-supports-ordered-compartment-index-handles search-param values))

(defn compartment-query-plan* [search-clauses]
  (let [{scan-clauses true other-clauses false}
        (group-by (partial every? supports-ordered-compartment-index-handles) search-clauses)]
    [scan-clauses other-clauses]))

(defn- ordered-compartment-index-handles**
  ([batch-db compartment tid [search-param _ _ compiled-values]]
   (search-param/ordered-compartment-index-handles
    search-param batch-db compartment tid compiled-values))
  ([batch-db compartment tid [search-param _ _ compiled-values] start-id]
   (search-param/ordered-compartment-index-handles
    search-param batch-db compartment tid compiled-values start-id)))

(defn- ordered-compartment-index-handles*
  ([batch-db compartment tid disjunction]
   (if (= 1 (count disjunction))
     (ordered-compartment-index-handles** batch-db compartment tid (first disjunction))
     (let [f #(ordered-compartment-index-handles** batch-db compartment tid %)]
       (u/union-index-handles (map f disjunction)))))
  ([batch-db compartment tid disjunction start-id]
   (if (= 1 (count disjunction))
     (ordered-compartment-index-handles** batch-db compartment tid (first disjunction) start-id)
     (let [f #(ordered-compartment-index-handles** batch-db compartment tid % start-id)]
       (u/union-index-handles (map f disjunction))))))

(defn- ordered-compartment-index-handles
  ([batch-db compartment tid conjunction]
   (if (= 1 (count conjunction))
     (ordered-compartment-index-handles* batch-db compartment tid (first conjunction))
     (let [f #(ordered-compartment-index-handles* batch-db compartment tid %)]
       (u/intersection-index-handles (map f conjunction)))))
  ([batch-db compartment tid conjunction start-id]
   (if (= 1 (count conjunction))
     (ordered-compartment-index-handles* batch-db compartment tid (first conjunction) start-id)
     (let [f #(ordered-compartment-index-handles* batch-db compartment tid % start-id)]
       (u/intersection-index-handles (map f conjunction))))))

(defn- compartment-scan
  [batch-db compartment tid scan-clauses]
  (if (seq scan-clauses)
    (ordered-compartment-index-handles batch-db compartment tid scan-clauses)
    (coll/eduction
     (map ih/from-resource-handle)
     (cr/resource-handles batch-db compartment tid))))

(defn compartment-query
  "Returns a reducible collection of resource handles from `batch-db` in
  `compartment` of type with `tid` that satisfy `search-clauses`, optionally
  starting with `start-id`."
  [batch-db compartment tid search-clauses]
  (let [[scan-clauses other-clauses] (compartment-query-plan* search-clauses)]
    (coll/eduction
     (resource-handle-mapper batch-db tid scan-clauses other-clauses)
     (compartment-scan batch-db compartment tid scan-clauses))))

(defn compartment-query*
  "Returns a reducible collection of resource handles from `batch-db` in
  `compartment` of type with `tid` that satisfy `scan-clauses` and possible
  empty `other-clauses`, optionally starting with `start-id`."
  ([batch-db compartment tid scan-clauses other-clauses]
   (coll/eduction
    (resource-handle-mapper batch-db tid scan-clauses other-clauses)
    (ordered-compartment-index-handles batch-db compartment tid scan-clauses)))
  ([batch-db compartment tid scan-clauses other-clauses start-id]
   (coll/eduction
    (resource-handle-mapper batch-db tid scan-clauses other-clauses)
    (ordered-compartment-index-handles batch-db compartment tid scan-clauses start-id))))

(defn compartment-query-plan
  [search-clauses]
  (let [[scan-clauses other-clauses] (compartment-query-plan* search-clauses)]
    (cond->
     {:query-type :compartment
      :seek-clauses (into [] (mapcat clause-stats) other-clauses)}
      (seq scan-clauses)
      (assoc :scan-type :ordered
             :scan-clauses (into [] (mapcat clause-stats) scan-clauses)))))
