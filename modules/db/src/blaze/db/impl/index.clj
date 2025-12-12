(ns blaze.db.impl.index
  "This namespace contains query functions."
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.compartment.resource :as cr]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.search-param-registry :as sr]))

(defn- resolve-search-clause
  [registry type ret [param & values :as clause] lenient?]
  (let [values (cond->> values (< 1 (count values)) (into [] (distinct)))]
    (if (empty? values)
      (reduced (ba/incorrect (format "Clause `%s` isn't valid." clause)))
      (if-ok [[search-param modifier] (sr/parse registry type param)
              _ (search-param/validate-modifier search-param modifier)]
        (if-ok [compiled-values (search-param/compile-values search-param modifier values)]
          (conj ret [search-param modifier values compiled-values])
          reduced)
        #(if lenient? ret (reduced %))))))

(defn- resolve-sort-clause
  [registry type ret [_ param direction :as clause]]
  (cond
    (not (#{:asc :desc} direction))
    (reduced (ba/incorrect (format "Clause `%s` isn't valid." clause)))

    (seq ret)
    (reduced (ba/incorrect "Sort clauses are only allowed at first position."))

    (not (#{"_id" "_lastUpdated"} param))
    (reduced (ba/incorrect (format "Unknown search-param `%s` in sort clause." param)))

    (and (= "_id" param) (= :desc direction))
    (reduced (ba/unsupported "Unsupported sort direction `desc` for search param `_id`."))

    :else
    (let [[search-param] (sr/parse registry type param)]
      (conj ret [search-param (name direction) [] []]))))

(defn resolve-search-params [registry type clauses lenient?]
  (reduce
   (fn [ret clause]
     (if (identical? :sort (first clause))
       (resolve-sort-clause registry type ret clause)
       (resolve-search-clause registry type ret clause lenient?)))
   []
   clauses))

(defn other-clauses-resource-handle-filter
  "Creates a filter transducer over resource handles for all `clauses` by
  possibly composing multiple filter transducers for each clause."
  [batch-db clauses]
  (transduce
   (map
    (fn [[search-param modifier _ values]]
      (search-param/matcher search-param batch-db modifier values)))
   comp
   clauses))

(defn- other-clauses-single-version-id-filter
  [batch-db tid clauses]
  (transduce
   (map
    (fn [[search-param modifier _ values]]
      (search-param/single-version-id-matcher search-param batch-db tid modifier values)))
   comp
   clauses))

(defn other-clauses-index-handle-filter
  "Creates a filter transducer over index handles for all `clauses` by possibly
  composing multiple filter transducers for each clause."
  [batch-db tid clauses]
  (comp
   (mapcat ih/to-single-version-ids)
   (other-clauses-single-version-id-filter batch-db tid clauses)
   u/by-id-grouper))

(defn- sort-clause? [[_ modifier _ _]]
  (#{"asc" "desc"} modifier))

(defn- index-handles
  ([batch-db tid [search-param modifier _ compiled-values]]
   (search-param/index-handles search-param batch-db tid modifier
                               compiled-values))
  ([batch-db tid [search-param modifier _ compiled-values] start-id]
   (search-param/index-handles search-param batch-db tid modifier
                               compiled-values start-id)))

(defn- sorted-index-handles
  ([batch-db tid [search-param modifier]]
   (condp = modifier
     "asc" (search-param/sorted-index-handles search-param batch-db tid :asc)
     "desc" (search-param/sorted-index-handles search-param batch-db tid :desc)))
  ([batch-db tid [search-param modifier] start-id]
   (condp = modifier
     "asc" (search-param/sorted-index-handles search-param batch-db tid :asc start-id)
     "desc" (search-param/sorted-index-handles search-param batch-db tid :desc start-id))))

(defn- ordered-index-handles*
  ([batch-db tid [search-param modifier _ compiled-values]]
   (search-param/ordered-index-handles search-param batch-db tid modifier
                                       compiled-values))
  ([batch-db tid [search-param modifier _ compiled-values] start-id]
   (search-param/ordered-index-handles search-param batch-db tid modifier
                                       compiled-values start-id)))

(defn- intersection-index-handles [index-handles]
  (apply coll/intersection ih/id-comp ih/intersection index-handles))

(defn- ordered-index-handles
  ([batch-db tid clauses]
   (if (= 1 (count clauses))
     (ordered-index-handles* batch-db tid (first clauses))
     (let [ordered-index-handles #(ordered-index-handles* batch-db tid %)]
       (intersection-index-handles (map ordered-index-handles clauses)))))
  ([batch-db tid clauses start-id]
   (if (= 1 (count clauses))
     (ordered-index-handles* batch-db tid (first clauses) start-id)
     (let [ordered-index-handles #(ordered-index-handles* batch-db tid % start-id)]
       (intersection-index-handles (map ordered-index-handles clauses))))))

(defn- postprocess-matches [batch-db clauses]
  (transduce
   (keep
    (fn [[search-param _ values compiled-values]]
      (p/-postprocess-matches search-param batch-db values compiled-values)))
   comp
   clauses))

(defn- supports-ordered-index-handles
  [batch-db tid [search-param modifier _ compiled-values]]
  (p/-supports-ordered-index-handles search-param batch-db tid modifier
                                     compiled-values))

(defn- group-by-ordered-index-handle-support
  "Returns two groups, true and false."
  [batch-db tid clauses]
  (group-by (partial supports-ordered-index-handles batch-db tid) clauses))

(defn- estimated-scan-size
  [batch-db tid [search-param modifier _ compiled-values]]
  (search-param/estimated-scan-size search-param batch-db tid modifier compiled-values))

(defn- attach-estimated-scan-size [batch-db tid clause]
  (with-meta clause {:estimated-scan-size (estimated-scan-size batch-db tid clause)}))

(def ^:private ^:const ^long scan-factor
  "The factor to calculate the maximum difference between the search-param/values
  combination with the smallest scan size and the largest scan size to allow.

  Clauses with scan sizes larger than the calculated threshold will be excluded
  from scanning."
  10)

(defn- group-by-estimated-scan-size
  "Returns two groups, :small and :large."
  [batch-db tid clauses]
  (let [sized-clauses (mapv #(attach-estimated-scan-size batch-db tid %) clauses)
        estimated-sizes (->> (map (comp :estimated-scan-size meta) sized-clauses)
                             (remove ba/anomaly?)
                             (remove zero?)
                             (sort))]
    (if (seq estimated-sizes)
      (let [threshold (* scan-factor (first estimated-sizes))]
        (group-by
         (fn [clause]
           (let [{:keys [estimated-scan-size]} (meta clause)]
             (if (and (not (ba/anomaly? estimated-scan-size))
                      (< estimated-scan-size threshold))
               :small
               :large)))
         sized-clauses))
      {:small sized-clauses})))

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
            (group-by-estimated-scan-size batch-db tid ordered-support-clauses)]
        [small-clauses
         (into large-clauses other-clauses)])

      :else
      [nil other-clauses])))

(defn- resource-handle-mapper*
  ([batch-db tid clauses]
   (comp (u/resource-handle-xf batch-db tid)
         (postprocess-matches batch-db clauses)))
  ([batch-db tid clauses other-clauses]
   (comp (other-clauses-index-handle-filter batch-db tid other-clauses)
         (resource-handle-mapper* batch-db tid clauses))))

(defn- resource-handle-mapper [batch-db tid clauses other-clauses]
  (if (seq other-clauses)
    (resource-handle-mapper* batch-db tid clauses other-clauses)
    (resource-handle-mapper* batch-db tid clauses)))

(defn- clause-stats [[{:keys [code]} modifier values]]
  {:code code
   :modifier modifier
   :values values})

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
  ([batch-db tid [[_ _ _ compiled-values :as first-clause] & other-clauses
                  :as clauses] start-id]
   (if (= 1 (count compiled-values))
     (coll/eduction
      (comp (resource-handle-mapper batch-db tid clauses other-clauses)
            (distinct))
      (index-handles batch-db tid first-clause start-id))
     (let [start-id (codec/id-string start-id)]
       (coll/eduction
        (drop-while #(not= start-id (:id %)))
        (unordered-resource-handles batch-db tid clauses))))))

(defn type-query
  "Returns a reducible collection of resource handles from `batch-db` of type
  with `tid` that satisfy `clauses`, optionally starting with `start-id`."
  ([batch-db tid clauses]
   (if (sort-clause? (first clauses))
     (let [[first-clause & other-clauses] clauses]
       (coll/eduction
        (resource-handle-mapper batch-db tid clauses other-clauses)
        (sorted-index-handles batch-db tid first-clause)))
     (let [[scan-clauses other-clauses] (type-query-plan* batch-db tid clauses)]
       (if (seq scan-clauses)
         (ordered-resource-handles batch-db tid scan-clauses other-clauses)
         (unordered-resource-handles batch-db tid other-clauses)))))
  ([batch-db tid clauses start-id]
   (if (sort-clause? (first clauses))
     (let [[first-clause & other-clauses] clauses]
       (coll/eduction
        (resource-handle-mapper batch-db tid clauses other-clauses)
        (sorted-index-handles batch-db tid first-clause start-id)))
     (let [[scan-clauses other-clauses] (type-query-plan* batch-db tid clauses)]
       (if (seq scan-clauses)
         (ordered-resource-handles batch-db tid scan-clauses other-clauses start-id)
         (unordered-resource-handles batch-db tid other-clauses start-id))))))

(defn type-query-plan
  [batch-db tid clauses]
  (if (sort-clause? (first clauses))
    (let [[first-clause & other-clauses] clauses]
      {:query-type :type
       :scan-type :ordered
       :scan-clauses (mapv clause-stats [first-clause])
       :seek-clauses (mapv clause-stats other-clauses)})
    (let [[scan-clauses other-clauses] (type-query-plan* batch-db tid clauses)]
      (if (seq scan-clauses)
        {:query-type :type
         :scan-type :ordered
         :scan-clauses (mapv clause-stats scan-clauses)
         :seek-clauses (mapv clause-stats other-clauses)}
        {:query-type :type
         :scan-type :unordered
         :scan-clauses (mapv clause-stats [(first other-clauses)])
         :seek-clauses (mapv clause-stats (rest other-clauses))}))))

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
  [batch-db tid clauses]
  (if (sort-clause? (first clauses))
    (if (next clauses)
      (type-query-total batch-db tid (next clauses))
      (ac/completed-future (p/-type-total batch-db tid)))
    (let [[scan-clauses other-clauses] (type-query-plan* batch-db tid clauses)]
      (if (seq scan-clauses)
        (sum-future-counts
         (chunk-counter
          (resource-handle-mapper batch-db tid scan-clauses other-clauses))
         (ordered-index-handles batch-db tid scan-clauses))
        (ac/completed-future
         (count (unordered-resource-handles batch-db tid other-clauses)))))))

(defn system-query [_ _]
  ;; TODO: implement
  [])

(defn- supports-ordered-compartment-index-handles [[search-param _ values]]
  (p/-supports-ordered-compartment-index-handles search-param values))

(defn- compartment-query-plan* [clauses]
  (let [{scan-clauses true other-clauses false}
        (group-by supports-ordered-compartment-index-handles clauses)]
    [scan-clauses other-clauses]))

(defn- ordered-compartment-index-handles*
  ([batch-db compartment tid [search-param _ _ compiled-values]]
   (search-param/ordered-compartment-index-handles
    search-param batch-db compartment tid compiled-values))
  ([batch-db compartment tid [search-param _ _ compiled-values] start-id]
   (search-param/ordered-compartment-index-handles
    search-param batch-db compartment tid compiled-values start-id)))

(defn- ordered-compartment-index-handles
  ([batch-db compartment tid clauses]
   (if (= 1 (count clauses))
     (ordered-compartment-index-handles* batch-db compartment tid (first clauses))
     (->> (map #(ordered-compartment-index-handles* batch-db compartment tid %)
               clauses)
          (apply coll/intersection ih/id-comp ih/intersection))))
  ([batch-db compartment tid clauses start-id]
   (if (= 1 (count clauses))
     (ordered-compartment-index-handles* batch-db compartment tid (first clauses) start-id)
     (->> (map #(ordered-compartment-index-handles* batch-db compartment tid % start-id)
               clauses)
          (apply coll/intersection ih/id-comp ih/intersection)))))

(defn- compartment-scan
  ([batch-db compartment tid scan-clauses]
   (if (seq scan-clauses)
     (ordered-compartment-index-handles batch-db compartment tid scan-clauses)
     (coll/eduction
      (map ih/from-resource-handle)
      (cr/resource-handles batch-db compartment tid))))
  ([batch-db compartment tid scan-clauses start-id]
   (if (seq scan-clauses)
     (ordered-compartment-index-handles batch-db compartment tid scan-clauses start-id)
     (coll/eduction
      (map ih/from-resource-handle)
      (cr/resource-handles batch-db compartment tid start-id)))))

(defn compartment-query
  "Returns a reducible collection of resource handles from `batch-db` in
  `compartment` of type with `tid` that satisfy `clauses`, optionally starting
  with `start-id`."
  ([batch-db compartment tid clauses]
   (let [[scan-clauses other-clauses] (compartment-query-plan* clauses)]
     (coll/eduction
      (resource-handle-mapper batch-db tid scan-clauses other-clauses)
      (compartment-scan batch-db compartment tid scan-clauses))))
  ([batch-db compartment tid clauses start-id]
   (let [[scan-clauses other-clauses] (compartment-query-plan* clauses)]
     (coll/eduction
      (resource-handle-mapper batch-db tid scan-clauses other-clauses)
      (compartment-scan batch-db compartment tid scan-clauses start-id)))))

(defn compartment-query-plan [clauses]
  (let [[scan-clauses other-clauses] (compartment-query-plan* clauses)]
    (cond->
     {:query-type :compartment
      :seek-clauses (mapv clause-stats other-clauses)}
      (seq scan-clauses)
      (assoc :scan-type :ordered
             :scan-clauses (mapv clause-stats scan-clauses)))))
