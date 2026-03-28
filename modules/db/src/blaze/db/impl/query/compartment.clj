(ns blaze.db.impl.query.compartment
  (:refer-clojure :exclude [str])
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index :as index]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.query.util :as qu]
   [blaze.db.impl.search-param :as search-param]
   [blaze.util :refer [str]]))

(defn- compile-search-param-values
  "Compiles the `values` via `search-param`.

  Most likely the search param is of type reference. Because compiling
  references isn't really async, we can join here."
  [search-param modifier values]
  (ac/join (search-param/compile-values search-param modifier values)))

(defn- value-compiler [values]
  (fn [[search-param modifier]]
    [search-param modifier values
     (compile-search-param-values search-param modifier values)]))

(defn- inject-values [compartment-clauses values]
  (mapv (partial mapv (value-compiler values)) compartment-clauses))

(defn- list-clauses [clauses code id]
  (inject-values clauses [(str code "/" id)]))

(defrecord CompartmentListQuery [code clauses tid]
  p/Query
  (-execute [_ batch-db arg1]
    (index/ordered-resource-handles batch-db tid (list-clauses clauses code arg1)
                                    nil))
  (-execute [_ batch-db arg1 arg2]
    (index/ordered-resource-handles batch-db tid (list-clauses clauses code arg1)
                                    nil (codec/id-byte-string arg2)))
  (-query-clauses [_])
  (-query-plan [_ batch-db]
    (index/type-query-plan batch-db tid {:search-clauses clauses})))

(defn- seek-clauses [clauses code id other-clauses]
  {:search-clauses (into (inject-values clauses [(str code "/" id)]) other-clauses)})

(defrecord CompartmentSeekQuery [code clauses tid other-clauses]
  p/Query
  (-execute [_ batch-db arg1]
    (index/type-query batch-db tid (seek-clauses clauses code arg1 other-clauses)))
  (-execute [_ batch-db arg1 arg2]
    (index/type-query batch-db tid (seek-clauses clauses code arg1 other-clauses)
                      (codec/id-byte-string arg2)))
  (-query-clauses [_]
    (qu/decode-clauses {:search-clauses other-clauses}))
  (-query-plan [_ batch-db]
    (index/type-query-plan batch-db tid {:search-clauses (into clauses other-clauses)})))

(defrecord CompartmentQuery [c-hash tid scan-clauses other-clauses]
  p/Query
  (-execute [_ batch-db arg1]
    (index/compartment-query batch-db [c-hash (codec/id-byte-string arg1)]
                             tid scan-clauses other-clauses))
  (-execute [_ batch-db arg1 arg2]
    (index/compartment-query batch-db [c-hash (codec/id-byte-string arg1)]
                             tid scan-clauses other-clauses
                             (codec/id-byte-string arg2)))
  (-query-clauses [_]
    (qu/decode-clauses {:search-clauses (into scan-clauses other-clauses)}))
  (-query-plan [_ _]
    (index/compartment-query-plan scan-clauses other-clauses)))
