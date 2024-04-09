(ns blaze.db.impl.index
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param.all :as search-param-all]
   [blaze.db.impl.search-param.chained :as spc]
   [clojure.spec.alpha :as s]))

(defmulti resolve-search-param (fn [_registry _type _ret [type] _lenient?] type))

(defmethod resolve-search-param :search-clause
  [registry type ret [_ [param & values]] lenient?]
  (let [values (distinct values)]
    (if-ok [[search-param modifier] (spc/parse-search-param registry type param)]
      (if-ok [compiled-values (search-param/compile-values search-param modifier values)]
        (conj ret [search-param modifier values compiled-values])
        reduced)
      #(if lenient? ret (reduced %)))))

(defmethod resolve-search-param :sort-clause
  [registry type ret [_ [_ param direction]] _lenient?]
  (cond
    (seq ret)
    (reduced (ba/incorrect "Sort clauses are only allowed at first position."))

    (not (#{"_id" "_lastUpdated"} param))
    (reduced (ba/incorrect (format "Unknown search-param `%s` in sort clause." param)))

    (and (= "_id" param) (= :desc direction))
    (reduced (ba/unsupported "Unsupported sort direction `desc` for search param `_id`."))

    :else
    (let [[search-param] (spc/parse-search-param registry type param)]
      (conj ret [search-param (name direction) [] []]))))

(defn- conform-clause [clause]
  (s/conform :blaze.db.query/clause clause))

(defn- resolve-search-params* [registry type clauses lenient?]
  (reduce
   #(resolve-search-param registry type %1 (conform-clause %2) lenient?)
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
  "Creates a filter xform for all `clauses` by possibly composing multiple
  filter xforms for each clause."
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
  ([batch-db tid clauses]
   (let [[[search-param modifier _ values] & other-clauses] clauses]
     (if (seq other-clauses)
       (coll/eduction
        (other-clauses-filter batch-db other-clauses)
        (resource-handles
         search-param batch-db tid modifier values))
       (resource-handles
        search-param batch-db tid modifier values))))
  ([context tid clauses start-id]
   (let [[[search-param modifier _ values] & other-clauses] clauses]
     (if (seq other-clauses)
       (coll/eduction
        (other-clauses-filter context other-clauses)
        (resource-handles
         search-param context tid modifier values start-id))
       (resource-handles
        search-param context tid modifier values start-id)))))

(defn- resource-handle-chunk-counter [context other-clauses chunk]
  (transduce
   (other-clauses-filter context other-clauses)
   (completing (fn [sum _] (inc sum)))
   0
   chunk))

(defn- resource-handle-chunk-counter-mapper [context other-clauses]
  (map #(ac/supply-async (fn [] (resource-handle-chunk-counter context other-clauses %)))))

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
