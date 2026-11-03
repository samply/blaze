(ns blaze.db.impl.query.compartment
  (:refer-clojure :exclude [str])
  (:require
   [blaze.async.comp :as ac]
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
  (-execute [_ db arg1]
    (index/ordered-resource-handles db tid (list-clauses clauses code arg1)
                                    nil))
  (-execute [_ db arg1 arg2]
    (index/ordered-resource-handles db tid (list-clauses clauses code arg1)
                                    nil arg2))
  (-query-clauses [_])
  (-query-plan [_ db]
    (index/type-query-plan db tid {:search-clauses clauses}))
  (-query-match-extension-fn [_]))

(defn- seek-clauses [clauses code id other-clauses]
  {:search-clauses (into (inject-values clauses [(str code "/" id)]) other-clauses)})

(defrecord CompartmentSeekQuery [code clauses tid other-clauses]
  p/Query
  (-execute [_ db arg1]
    (index/type-query db tid (seek-clauses clauses code arg1 other-clauses)))
  (-execute [_ db arg1 arg2]
    (index/type-query db tid (seek-clauses clauses code arg1 other-clauses)
                      arg2))
  (-query-clauses [_]
    (qu/decode-clauses {:search-clauses other-clauses}))
  (-query-plan [_ db]
    (index/type-query-plan db tid {:search-clauses (into clauses other-clauses)}))
  (-query-match-extension-fn [_]
    (qu/match-extension-fn other-clauses)))

(defrecord CompartmentQuery [c-hash tid scan-clauses other-clauses]
  p/Query
  (-execute [_ db arg1]
    (index/compartment-query db [c-hash arg1]
                             tid scan-clauses other-clauses))
  (-execute [_ db arg1 arg2]
    (index/compartment-query db [c-hash arg1]
                             tid scan-clauses other-clauses
                             arg2))
  (-query-clauses [_]
    (qu/decode-clauses {:search-clauses (into scan-clauses other-clauses)}))
  (-query-plan [_ _]
    (index/compartment-query-plan scan-clauses other-clauses))
  (-query-match-extension-fn [_]
    (qu/match-extension-fn (into scan-clauses other-clauses))))
