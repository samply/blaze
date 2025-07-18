(ns blaze.db.impl.index
  "This namespace contains query functions."
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param.all :as search-param-all]
   [blaze.db.search-param-registry :as sr]))

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

(defn- resource-handles
  ([search-param batch-db tid modifier values]
   (condp = modifier
     "asc" (search-param/sorted-resource-handles search-param batch-db tid :asc)
     "desc" (search-param/sorted-resource-handles search-param batch-db tid :desc)
     (search-param/resource-handles search-param batch-db tid modifier values)))
  ([search-param context tid modifier values start-id]
   (condp = modifier
     "asc" (search-param/sorted-resource-handles search-param context tid :asc
                                                 start-id)
     "desc" (search-param/sorted-resource-handles search-param context tid :desc
                                                  start-id)
     (search-param/resource-handles search-param context tid modifier values
                                    start-id))))

(defn type-query
  "Returns a reducible collection of resource handles from `batch-db` of type
  with `tid` that satisfy `clauses`, optionally starting with `start-id`."
  ([batch-db tid clauses]
   (let [[[search-param modifier _ values] & other-clauses] clauses]
     (if (seq other-clauses)
       (coll/eduction
        (other-clauses-filter batch-db other-clauses)
        (resource-handles
         search-param batch-db tid modifier values))
       (resource-handles
        search-param batch-db tid modifier values))))
  ([batch-db tid clauses start-id]
   (let [[[search-param modifier _ values] & other-clauses] clauses]
     (if (seq other-clauses)
       (coll/eduction
        (other-clauses-filter batch-db other-clauses)
        (resource-handles
         search-param batch-db tid modifier values start-id))
       (resource-handles
        search-param batch-db tid modifier values start-id)))))

(defn- resource-handle-chunk-counter [batch-db other-clauses chunk]
  (transduce
   (other-clauses-filter batch-db other-clauses)
   (completing (fn [sum _] (inc sum)))
   0
   chunk))

(defn- resource-handle-chunk-counter-mapper [batch-db other-clauses]
  (map #(ac/supply-async (fn [] (resource-handle-chunk-counter batch-db other-clauses %)))))

(defn- count-resource-handles [xform chunked-resource-handles]
  (let [futures (into [] xform chunked-resource-handles)]
    (do-sync [_ (ac/all-of futures)]
      (transduce (map ac/join) + futures))))

(defn- chunked-resource-handles
  [search-param context tid modifier values]
  (condp = modifier
    "asc" [(search-param/sorted-resource-handles search-param context tid :asc)]
    "desc" [(search-param/sorted-resource-handles search-param context tid :desc)]
    (search-param/chunked-resource-handles search-param context tid modifier values)))

(defn type-query-total
  "Returns a CompletableFuture that will complete with the count of the
  matching resource handles."
  [batch-db tid clauses]
  (let [[[search-param modifier _ values] & other-clauses] clauses]
    (if (seq other-clauses)
      (count-resource-handles
       (resource-handle-chunk-counter-mapper batch-db other-clauses)
       (chunked-resource-handles search-param batch-db tid modifier values))
      (count-resource-handles
       (map #(ac/supply-async (fn [] (reduce (fn [sum _] (inc sum)) 0 %))))
       (chunked-resource-handles search-param batch-db tid modifier values)))))

(defn system-query [_ _]
  ;; TODO: implement
  [])

(defn compartment-query
  "Iterates over the CompartmentSearchParamValueResource index."
  [batch-db compartment tid clauses]
  (let [[[search-param _ _ values] & other-clauses] clauses]
    (if (seq other-clauses)
      (coll/eduction
       (other-clauses-filter batch-db other-clauses)
       (search-param/compartment-resource-handles
        search-param batch-db compartment tid values))
      (search-param/compartment-resource-handles
       search-param batch-db compartment tid values))))
