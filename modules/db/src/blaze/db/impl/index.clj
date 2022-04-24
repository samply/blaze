(ns blaze.db.impl.index
  (:require
    [blaze.coll.core :as coll]
    [blaze.db.impl.search-param :as search-param]))


(defn- other-clauses-filter [context clauses]
  (filter
    (fn [resource-handle]
      (loop [[[search-param modifier _ values] & clauses] clauses]
        (if search-param
          (when (search-param/matches? search-param context resource-handle
                                       modifier values)
            (recur clauses))
          true)))))


(defn type-query
  ([context tid clauses]
   (let [[[search-param modifier _ values] & other-clauses] clauses]
     (if (seq other-clauses)
       (coll/eduction
         (other-clauses-filter context other-clauses)
         (search-param/resource-handles
           search-param context tid modifier values))
       (search-param/resource-handles
         search-param context tid modifier values))))
  ([context tid clauses start-id]
   (let [[[search-param modifier _ values] & other-clauses] clauses]
     (if (seq other-clauses)
       (coll/eduction
         (other-clauses-filter context other-clauses)
         (search-param/resource-handles
           search-param context tid modifier values start-id))
       (search-param/resource-handles
         search-param context tid modifier values start-id)))))


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
