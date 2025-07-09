(ns blaze.db.impl.index
  "This namespace contains query functions."
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.compartment.resource :as cr]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param.all :as search-param-all]
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.search-param-registry :as sr]
   [prometheus.alpha :as prom :refer [defhistogram]]))

(defhistogram index-scan-bytes
  "Estimated storage size of scanned indices."
  {:namespace "blaze"
   :subsystem "db"
   :name "index_scan_bytes"}
  (take 18 (iterate #(* 2 %) (* 1024 1024))))

(defn- resolve-search-clause
  [registry type ret [param & values :as clause] lenient?]
  (let [values (cond->> values (< 1 (count values)) (into [] (distinct)))]
    (if (empty? values)
      (reduced (ba/incorrect (format "Clause `%s` isn't valid." clause)))
      (if-ok [[search-param modifier] (sr/parse registry type param)]
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

(defn- resolve-search-params* [registry type clauses lenient?]
  (reduce
   (fn [ret clause]
     (if (identical? :sort (first clause))
       (resolve-sort-clause registry type ret clause)
       (resolve-search-clause registry type ret clause lenient?)))
   []
   clauses))

(defn- type-priority [{:keys [type]}]
  (case type
    "id" 0
    "token" 1
    2))

(defn- priority
  "Gives the single sorting search param the priority 0 and all other search
  params a priority starting at 1 in order to keep the sorting search param at
  the first position."
  [[search-param modifier]]
  (if (#{"asc" "desc"} modifier)
    0
    (inc (type-priority search-param))))

(defn- order-clauses
  "Orders clauses by specificity so that the clause constraining the resources
  the most will come first."
  [clauses]
  (sort-by priority clauses))

(defn- fix-last-updated [[[first-search-param first-modifier] :as clauses]]
  (if (and (= "_lastUpdated" (:code first-search-param))
           (not (#{"asc" "desc"} first-modifier)))
    (into [[search-param-all/search-param nil [""] [""]]] clauses)
    clauses))

(defn resolve-search-params [registry type clauses lenient?]
  (when-ok [clauses (resolve-search-params* registry type clauses lenient?)]
    (-> clauses order-clauses fix-last-updated)))

(defn other-clauses-filter
  "Creates a filter transducer for all `clauses` by possibly composing multiple
  filter transducers for each clause."
  [batch-db clauses]
  (transduce
   (map
    (fn [[search-param modifier _ values]]
      (search-param/matcher search-param batch-db modifier values)))
   comp
   clauses))

(defn other-clauses-filter-1*
  "Creates a filter transducer for all `clauses` by possibly composing multiple
  filter transducers for each clause."
  [batch-db tid clauses]
  (transduce
   (map
    (fn [[search-param modifier _ values]]
      (search-param/single-version-id-matcher search-param batch-db tid modifier values)))
   comp
   clauses))

(defn other-clauses-filter-1
  "Creates a filter transducer for all `clauses` by possibly composing multiple
  filter transducers for each clause."
  [batch-db tid clauses]
  (comp
   (mapcat ih/to-single-version-ids)
   (other-clauses-filter-1* batch-db tid clauses)
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

(defn- ordered-index-handles
  ([batch-db tid clauses]
   (if (= 1 (count clauses))
     (ordered-index-handles* batch-db tid (first clauses))
     (->> (map #(ordered-index-handles* batch-db tid %) clauses)
          (apply coll/intersection ih/intersection))))
  ([batch-db tid clauses start-id]
   (if (= 1 (count clauses))
     (ordered-index-handles* batch-db tid (first clauses) start-id)
     (->> (map #(ordered-index-handles* batch-db tid % start-id) clauses)
          (apply coll/intersection ih/intersection)))))

(defn- second-pass-filter [batch-db clauses]
  (transduce
   (keep
    (fn [[search-param _ values]]
      (p/-second-pass-filter search-param batch-db values)))
   comp
   clauses))

(defn- group-by-ordered-index-handle-support [clauses]
  (group-by (comp p/-supports-ordered-index-handles first) clauses))

(defn- estimated-storage-size
  [batch-db tid [search-param modifier _ compiled-values]]
  (search-param/estimated-storage-size search-param batch-db tid modifier compiled-values))

(defn- group-by-estimated-storage-size [batch-db tid clauses]
  (let [sized-clauses (mapv #(vector % (estimated-storage-size batch-db tid %)) clauses)
        estimated-sizes (sort (remove ba/anomaly? (map second sized-clauses)))]
    (if (seq estimated-sizes)
      (let [threshold (* 10 (first estimated-sizes))]
        (group-by
         (fn [[_ estimated-size]]
           (and (not (ba/anomaly? estimated-size))
                (< estimated-size threshold)))
         sized-clauses))
      {true sized-clauses})))

(defn observe-sizes! [sized-clauses]
  (run!
   (fn [[_ estimated-size]]
     (when-not (ba/anomaly? estimated-size)
       (prom/observe! index-scan-bytes estimated-size)))
   sized-clauses))

(defn- split-clauses [batch-db tid clauses]
  (let [{ordered-support-clauses true other-clauses false}
        (group-by-ordered-index-handle-support clauses)]
    (if (seq ordered-support-clauses)
      (let [{small-clauses true large-clauses false}
            (group-by-estimated-storage-size batch-db tid ordered-support-clauses)]
        (observe-sizes! small-clauses)
        [(mapv first small-clauses)
         (into (mapv first large-clauses) other-clauses)])
      [ordered-support-clauses other-clauses])))

(defn- resource-handle-mapper
  ([batch-db tid clauses]
   (comp (u/resource-handle-xf batch-db tid)
         (second-pass-filter batch-db clauses)))
  ([batch-db tid clauses other-clauses]
   (comp (other-clauses-filter-1 batch-db tid other-clauses)
         (resource-handle-mapper batch-db tid clauses))))

(defn- ordered-resource-handles
  ([batch-db tid clauses other-clauses]
   (coll/eduction
    (if (seq other-clauses)
      (resource-handle-mapper batch-db tid clauses other-clauses)
      (resource-handle-mapper batch-db tid clauses))
    (ordered-index-handles batch-db tid clauses)))
  ([batch-db tid clauses other-clauses start-id]
   (coll/eduction
    (if (seq other-clauses)
      (resource-handle-mapper batch-db tid clauses other-clauses)
      (resource-handle-mapper batch-db tid clauses))
    (ordered-index-handles batch-db tid clauses start-id))))

(defn- unordered-resource-handles
  ([batch-db tid [first-clause & other-clauses :as clauses]]
   (coll/eduction
    (comp (if (seq other-clauses)
            (resource-handle-mapper batch-db tid clauses other-clauses)
            (resource-handle-mapper batch-db tid clauses))
          (distinct))
    (index-handles batch-db tid first-clause)))
  ([batch-db tid [[_ _ _ compiled-values :as first-clause] & other-clauses
                  :as clauses] start-id]
   (if (= 1 (count compiled-values))
     (coll/eduction
      (comp (if (seq other-clauses)
              (resource-handle-mapper batch-db tid clauses other-clauses)
              (resource-handle-mapper batch-db tid clauses))
            (distinct))
      (index-handles batch-db tid first-clause start-id))
     (let [start-id (codec/id-string start-id)]
       (coll/eduction
        (drop-while #(not= start-id (rh/id %)))
        (unordered-resource-handles batch-db tid clauses))))))

(defn type-query
  "Returns a reducible collection of resource handles from `batch-db` of type
  with `tid` that satisfy `clauses`, optionally starting with `start-id`."
  ([batch-db tid clauses]
   (if (sort-clause? (first clauses))
     (let [[first-clause & other-clauses] clauses]
       (coll/eduction
        (resource-handle-mapper batch-db tid clauses)
        (cond->> (sorted-index-handles batch-db tid first-clause)
          (seq other-clauses)
          (coll/eduction (other-clauses-filter-1 batch-db tid other-clauses)))))
     (let [[clauses other-clauses] (split-clauses batch-db tid clauses)]
       (if (seq clauses)
         (ordered-resource-handles batch-db tid clauses other-clauses)
         (unordered-resource-handles batch-db tid other-clauses)))))
  ([batch-db tid clauses start-id]
   (if (sort-clause? (first clauses))
     (let [[first-clause & other-clauses] clauses]
       (coll/eduction
        (resource-handle-mapper batch-db tid clauses)
        (cond->> (sorted-index-handles batch-db tid first-clause start-id)
          (seq other-clauses)
          (coll/eduction (other-clauses-filter-1 batch-db tid other-clauses)))))
     (let [[clauses other-clauses] (split-clauses batch-db tid clauses)]
       (if (seq clauses)
         (ordered-resource-handles batch-db tid clauses other-clauses start-id)
         (unordered-resource-handles batch-db tid other-clauses start-id))))))

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
    (let [[clauses other-clauses] (split-clauses batch-db tid clauses)]
      (if (seq clauses)
        (sum-future-counts
         (chunk-counter
          (if (seq other-clauses)
            (resource-handle-mapper batch-db tid clauses other-clauses)
            (resource-handle-mapper batch-db tid clauses)))
         (ordered-index-handles batch-db tid clauses))
        (ac/completed-future
         (count (unordered-resource-handles batch-db tid other-clauses)))))))

(defn system-query [_ _]
  ;; TODO: implement
  [])

(defn- group-by-ordered-compartment-index-handle-support [clauses]
  (group-by
   (fn [[search-param _ values]]
     (p/-supports-ordered-compartment-index-handles search-param values))
   clauses))

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
          (apply coll/intersection ih/intersection))))
  ([batch-db compartment tid clauses start-id]
   (if (= 1 (count clauses))
     (ordered-compartment-index-handles* batch-db compartment tid (first clauses) start-id)
     (->> (map #(ordered-compartment-index-handles* batch-db compartment tid % start-id)
               clauses)
          (apply coll/intersection ih/intersection)))))

(defn compartment-query
  "Returns a reducible collection of resource handles from `batch-db` in
  `compartment` of type with `tid` that satisfy `clauses`, optionally starting
  with `start-id`."
  ([batch-db compartment tid clauses]
   (let [{clauses true other-clauses false} (group-by-ordered-compartment-index-handle-support clauses)]
     (coll/eduction
      (resource-handle-mapper batch-db tid clauses)
      (cond->>
       (if (seq clauses)
         (ordered-compartment-index-handles batch-db compartment tid clauses)
         (coll/eduction
          (map ih/from-resource-handle)
          (cr/resource-handles batch-db compartment tid)))
        (seq other-clauses)
        (coll/eduction (other-clauses-filter-1 batch-db tid other-clauses))))))
  ([batch-db compartment tid clauses start-id]
   (let [{clauses true other-clauses false} (group-by-ordered-compartment-index-handle-support clauses)]
     (coll/eduction
      (resource-handle-mapper batch-db tid clauses)
      (cond->>
       (if (seq clauses)
         (ordered-compartment-index-handles batch-db compartment tid clauses start-id)
         (coll/eduction
          (map ih/from-resource-handle)
          (cr/resource-handles batch-db compartment tid start-id)))
        (seq other-clauses)
        (coll/eduction (other-clauses-filter-1 batch-db tid other-clauses)))))))
