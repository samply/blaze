(ns blaze.db.impl.index
  (:require
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param.util :as u]))


(defn- other-clauses-filter [context clauses]
  (filter
    (fn [resource-handle]
      (loop [[[search-param modifier _ values] & clauses] clauses]
        (if search-param
          (when (search-param/matches? search-param context resource-handle
                                       modifier values)
            (recur clauses))
          true)))))


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
  ([context tid clauses start-did]
   (let [[[search-param modifier _ values] & other-clauses] clauses]
     (if (seq other-clauses)
       (coll/eduction
         (other-clauses-filter context other-clauses)
         (resource-handles
           search-param context tid modifier values start-did))
       (resource-handles
         search-param context tid modifier values start-did)))))


(defn system-query [_ _]
  ;; TODO: implement
  [])


(defn compartment-query
  "Iterates over the CSV index "
  [context compartment tid clauses]
  (let [[[search-param _ _ values] & other-clauses] clauses]
    (if (seq other-clauses)
      (coll/eduction
        (other-clauses-filter context other-clauses)
        (search-param/compartment-resource-handles
          search-param context compartment tid values))
      (search-param/compartment-resource-handles
        search-param context compartment tid values))))


(defn targets!
  "Returns a reducible collection of non-deleted resource handles that are
  referenced by `resource-handle` via a search-param with `code` having a type
  with `target-tid`.

  Changes the state of `context`. Consuming the collection requires exclusive
  access to `context`. Doesn't close `context`."
  {:arglists
   '([context resource-handle code]
     [context resource-handle code target-tid])}
  ([{:keys [rsvi] :as context} {:keys [tid did hash]} code]
   (coll/eduction
     (u/reference-resource-handle-mapper context)
    (r-sp-v/prefix-keys! rsvi tid did hash code)))
  ([{:keys [rsvi] :as context} {:keys [tid did hash]} code target-tid]
   (coll/eduction
     (u/reference-resource-handle-mapper context)
    (r-sp-v/prefix-keys! rsvi tid did hash code
                          (codec/tid-byte-string target-tid)))))
