(ns blaze.db.impl.index
  (:require
   [blaze.async.comp :as ac]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param.util :as u]))

(defn- other-clauses-filter
  "Creates a filter xform for all `clauses` by possibly composing multiple
  filter xforms for each clause."
  [context clauses]
  (transduce
   (map
    (fn [[search-param modifier _ values]]
      (search-param/matcher search-param context modifier values)))
   comp
   clauses))

(defn- resource-handles
  ([search-param context tid modifier values]
   (condp = modifier
     "asc" (search-param/sorted-resource-handles search-param context tid :asc)
     "desc" (search-param/sorted-resource-handles search-param context tid :desc)
     (search-param/resource-handles search-param context tid modifier values)))
  ([search-param context tid modifier values start-id]
   (condp = modifier
     "asc" (search-param/sorted-resource-handles search-param context tid :asc
                                                 start-id)
     "desc" (search-param/sorted-resource-handles search-param context tid :desc
                                                  start-id)
     (search-param/resource-handles search-param context tid modifier values
                                    start-id))))

(defn type-query
  ([context tid clauses]
   (let [[[search-param modifier _ values] & other-clauses] clauses]
     (if (seq other-clauses)
       (coll/eduction
        (other-clauses-filter context other-clauses)
        (resource-handles
         search-param context tid modifier values))
       (resource-handles
        search-param context tid modifier values))))
  ([context tid clauses start-id]
   (let [[[search-param modifier _ values] & other-clauses] clauses]
     (if (seq other-clauses)
       (coll/eduction
        (other-clauses-filter context other-clauses)
        (resource-handles
         search-param context tid modifier values start-id))
       (resource-handles
        search-param context tid modifier values start-id)))))

(defn type-query-total
  "Returns a CompletableFuture that will complete with the count of the
  matching resource handles."
  [context tid clauses]
  (let [[[search-param modifier _ values] & other-clauses] clauses]
    (if (seq other-clauses)
      (ac/completed-future
       (count
        (coll/eduction
         (other-clauses-filter context other-clauses)
         (resource-handles search-param context tid modifier values))))
      (search-param/count-resource-handles
       search-param context tid modifier values))))

(defn system-query [_ _]
  ;; TODO: implement
  [])

(defn compartment-query
  "Iterates over the CompartmentSearchParamValueResource index."
  [context compartment tid clauses]
  (let [[[search-param _ _ values] & other-clauses] clauses]
    (if (seq other-clauses)
      (coll/eduction
       (other-clauses-filter context other-clauses)
       (search-param/compartment-resource-handles
        search-param context compartment tid values))
      (search-param/compartment-resource-handles
       search-param context compartment tid values))))

(defn targets
  "Returns a reducible collection of non-deleted resource handles that are
  referenced by `resource-handle` via a search-param with `code` having a type
  with `target-tid` (optional)."
  {:arglists
   '([context resource-handle code]
     [context resource-handle code target-tid])}
  ([{:keys [snapshot] :as context} {:keys [tid id hash]} code]
   (coll/eduction
    (u/reference-resource-handle-mapper context)
    (r-sp-v/prefix-keys snapshot tid (codec/id-byte-string id) hash code)))
  ([{:keys [snapshot] :as context} {:keys [tid id hash]} code target-tid]
   (coll/eduction
    (u/reference-resource-handle-mapper context)
    (let [start-value (codec/tid-byte-string target-tid)]
      (r-sp-v/prefix-keys snapshot tid (codec/id-byte-string id) hash code
                          (bs/size start-value) start-value)))))
