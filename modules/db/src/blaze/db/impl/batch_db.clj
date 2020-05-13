(ns blaze.db.impl.batch-db
  "Batch Database Implementation

  A batch database keeps key-value store iterators open in order to avoid the
  cost associated with open and closing them."
  (:require
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index :as index]
    [blaze.db.impl.protocols :as p]
    [blaze.db.kv :as kv])
  (:import
    [java.io Closeable Writer]))


(set! *warn-on-reflection* true)


(defrecord BatchDb [context node snapshot raoi cspvi t]
  p/Db
  (-resource [_ type id]
    (index/resource* context raoi (codec/tid type) (codec/id-bytes id) t))

  (-list-compartment-resources [this code id type]
    (p/-list-compartment-resources this code id type nil))

  (-list-compartment-resources [_ code id type start-id]
    (let [compartment {:c-hash (codec/c-hash code) :res-id (codec/id-bytes id)}]
      (index/compartment-list context compartment (codec/tid type) (some-> start-id codec/id-bytes) t)))

  (-execute-query [_ query]
    (p/-execute query context snapshot raoi cspvi t))

  (-execute-query [_ query arg1]
    (p/-execute query context snapshot raoi cspvi t arg1))

  p/QueryCompiler
  (-compile-type-query [_ type clauses]
    (p/-compile-type-query node type clauses))

  (-compile-compartment-query [_ code type clauses]
    (p/-compile-compartment-query node code type clauses))

  Closeable
  (close [_]
    (.close ^Closeable cspvi)
    (.close ^Closeable raoi)
    (.close ^Closeable snapshot)))


(defmethod print-method BatchDb [^BatchDb db ^Writer w]
  (.write w (format "BatchDb[t=%d]" (.t db))))


(defrecord TypeQuery [tid clauses]
  p/Query
  (-execute [_ context snapshot raoi _ t]
    (index/type-query context snapshot raoi tid clauses t)))


(defrecord CompartmentQuery [c-hash tid clauses]
  p/Query
  (-execute [_ context snapshot raoi cspvi t arg1]
    (let [compartment {:c-hash c-hash :res-id (codec/id-bytes arg1)}]
      (index/compartment-query context snapshot cspvi raoi compartment
                               tid clauses t))))


(defn new-batch-db
  ^Closeable
  [{:blaze.db/keys [kv-store] :as context} node t]
  (let [snapshot (kv/new-snapshot kv-store)]
    (->BatchDb
      context node snapshot
      (index/resource-as-of-iter snapshot)
      (kv/new-iterator snapshot :compartment-search-param-value-index)
      t)))
