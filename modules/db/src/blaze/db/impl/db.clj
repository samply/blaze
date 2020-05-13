(ns blaze.db.impl.db
  "Primary Database Implementation"
  (:require
    [blaze.db.impl.batch-db :as batch-db]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index :as index]
    [blaze.db.impl.protocols :as p])
  (:import
    [clojure.lang IReduceInit]
    [java.io Writer]))


(set! *warn-on-reflection* true)


(deftype Db [context node basis-t t]
  p/Db
  (-as-of [_ t]
    (assert (<= t basis-t))
    (Db. context node basis-t t))

  (-basis-t [_]
    basis-t)

  (-as-of-t [_]
    (when (not= basis-t t) t))

  (-tx [_ t]
    (index/tx (:blaze.db/kv-store context) t))

  (-resource-exists? [this type id]
    (if-let [resource (p/-resource this type id)]
      (not (index/deleted? resource))
      false))

  (-resource [_ type id]
    (index/resource context (codec/tid type) (codec/id-bytes id) t))

  (-list-resources [this type]
    (p/-list-resources this type nil))

  (-list-resources [_ type start-id]
    (index/type-list context (codec/tid type) (some-> start-id codec/id-bytes) t))

  (-list-compartment-resources [this code id type]
    (p/-list-compartment-resources this code id type nil))

  (-list-compartment-resources [_ code id type start-id]
    (let [compartment {:c-hash (codec/c-hash code) :res-id (codec/id-bytes id)}]
      (index/compartment-list context compartment (codec/tid type) (some-> start-id codec/id-bytes) t)))

  (-execute-query [_ query]
    (reify IReduceInit
      (reduce [_ f init]
        (with-open [batch-db (batch-db/new-batch-db context node t)]
          (.reduce (p/-execute-query batch-db query) f init)))))

  (-execute-query [_ query arg1]
    (reify IReduceInit
      (reduce [_ f init]
        (with-open [batch-db (batch-db/new-batch-db context node t)]
          (.reduce (p/-execute-query batch-db query arg1) f init)))))

  (-instance-history [_ type id start-t since]
    (let [start-t (if (some-> start-t (<= t)) start-t t)
          since-t (or (some->> since (index/t-by-instant context)) 0)]
      (index/instance-history context (codec/tid type) (codec/id-bytes id) start-t since-t)))

  (-total-num-of-instance-changes [_ type id since]
    (let [since-t (or (some->> since (index/t-by-instant context)) 0)]
      (index/num-of-instance-changes context (codec/tid type) (codec/id-bytes id) t since-t)))

  (-type-history [_ type start-t start-id since]
    (let [start-t (if (some-> start-t (<= t)) start-t t)
          since-t (or (some->> since (index/t-by-instant context)) 0)]
      (index/type-history context (codec/tid type) start-t (some-> start-id codec/id-bytes) since-t)))

  (-type-total [_ type]
    (index/type-total context (codec/tid type) t))

  (-total-num-of-type-changes [_ type since]
    (let [since-t (or (some->> since (index/t-by-instant context)) 0)]
      (index/num-of-type-changes context (codec/tid type) t since-t)))

  (-system-history [_ start-t start-type start-id since]
    (assert (or (nil? start-id) start-type) "missing start-type on present start-id")
    (let [start-t (if (some-> start-t (<= t)) start-t t)
          since-t (or (some->> since (index/t-by-instant context)) 0)]
      (index/system-history context start-t (some-> start-type codec/tid) (some-> start-id codec/id-bytes) since-t)))

  (-total-num-of-system-changes [_ since]
    (let [since-t (or (some->> since (index/t-by-instant context)) 0)]
      (index/num-of-system-changes context t since-t)))

  (-new-batch-db [_]
    (batch-db/new-batch-db context node t))

  p/QueryCompiler
  (-compile-type-query [_ type clauses]
    (p/-compile-type-query node type clauses))

  (-compile-compartment-query [_ code type clauses]
    (p/-compile-compartment-query node code type clauses)))


(defmethod print-method Db [^Db db ^Writer w]
  (.write w (format "Db[t=%d]" (.t db))))


(defn db [kv-store resource-cache node t]
  (->Db #:blaze.db{:kv-store kv-store :resource-cache resource-cache}
        node t t))
