(ns blaze.db.impl.query.compartment
  (:require
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index :as index]
   [blaze.db.impl.index.compartment.resource :as cr]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.query.util :as qu]))

(defn- code-clause [[search-param] compiled-value]
  [search-param nil [] [compiled-value]])

(defn- optimize-in-compiled-value [batch-db tid clause compiled-value]
  (filterv #(coll/first (index/type-query batch-db tid [(code-clause clause %)]))
           compiled-value))

(defn- optimize-in-compiled-values [batch-db tid [_ _ _ compiled-values :as clause]]
  (mapv (partial optimize-in-compiled-value batch-db tid clause) compiled-values))

(defn- optimize-clause [batch-db tid [_ modifier :as clause]]
  (if (= "in" modifier)
    (assoc clause 3 (optimize-in-compiled-values batch-db tid clause))
    clause))

(defrecord CompartmentQuery [c-hash tid clauses]
  p/Query
  (-optimize [_ batch-db]
    (->> (mapv (partial optimize-clause batch-db tid) clauses)
         (->CompartmentQuery c-hash tid)))
  (-execute [_ batch-db arg1]
    (index/compartment-query batch-db [c-hash (codec/id-byte-string arg1)]
                             tid clauses))
  (-query-clauses [_]
    (qu/decode-clauses clauses))
  (-query-plan [_ _]
    (index/compartment-query-plan clauses)))

(defrecord EmptyCompartmentQuery [c-hash tid]
  p/Query
  (-optimize [query _]
    query)
  (-execute [_ batch-db arg1]
    (cr/resource-handles batch-db [c-hash (codec/id-byte-string arg1)] tid))
  (-query-clauses [_])
  (-query-plan [_ _]
    {:query-type :compartment}))
