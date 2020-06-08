(ns blaze.db.impl.db
  "Primary Database Implementation"
  (:require
    [blaze.db.impl.batch-db :as batch-db]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index :as index]
    [blaze.db.impl.index.resource :as resource]
    [blaze.db.impl.index.resource-as-of :as resource-as-of]
    [blaze.db.impl.protocols :as p]
    [blaze.db.kv :as kv])
  (:import
    [clojure.lang IReduceInit Sequential]
    [java.io Writer]))


(set! *warn-on-reflection* true)


(defmacro with-open-coll
  "Like `clojure.core/with-open` but opens and closes the resources on every
  reduce call to `coll`."
  [bindings coll]
  `(reify
     Sequential
     IReduceInit
     (reduce [_ rf# init#]
       (with-open ~bindings
         (.reduce ~coll rf# init#)))))


(deftype Db [node basis-t t]
  p/Db
  (-as-of [_ t]
    (assert (<= t basis-t))
    (Db. node basis-t t))

  (-basis-t [_]
    basis-t)

  (-as-of-t [_]
    (when (not= basis-t t) t))

  (-tx [_ t]
    (index/tx (:kv-store node) t))



  ;; ---- Instance-Level Functions --------------------------------------------

  (-resource-exists? [this type id]
    (if-let [resource (p/-resource this type id)]
      (not (resource/deleted? resource))
      false))

  (-resource [_ type id]
    (with-open [snapshot (kv/new-snapshot (:kv-store node))
                raoi (kv/new-iterator snapshot :resource-as-of-index)]
      (resource-as-of/resource node raoi (codec/tid type) (codec/id-bytes id) t)))



  ;; ---- Type-Level Functions ------------------------------------------------

  (-list-resources [_ type start-id]
    (with-open-coll [batch-db (batch-db/new-batch-db node t)]
      (p/-list-resources batch-db type start-id)))

  (-type-total [_ type]
    (with-open [batch-db (batch-db/new-batch-db node t)]
      (p/-type-total batch-db type)))



  ;; ---- System-Level Functions ----------------------------------------------

  (-system-list [_ start-type start-id]
    (with-open-coll [batch-db (batch-db/new-batch-db node t)]
      (p/-system-list batch-db start-type start-id)))

  (-system-total [_]
    (with-open [batch-db (batch-db/new-batch-db node t)]
      (p/-system-total batch-db)))



  ;; ---- Compartment-Level Functions -----------------------------------------

  (-list-compartment-resources [_ code id type start-id]
    (with-open-coll [batch-db (batch-db/new-batch-db node t)]
      (p/-list-compartment-resources batch-db code id type start-id)))



  ;; ---- Common Query Functions ----------------------------------------------

  (-execute-query [_ query]
    (with-open-coll [batch-db (batch-db/new-batch-db node t)]
      (p/-execute-query batch-db query)))

  (-execute-query [_ query arg1]
    (with-open-coll [batch-db (batch-db/new-batch-db node t)]
      (p/-execute-query batch-db query arg1)))



  ;; ---- Instance-Level History Functions ------------------------------------

  (-instance-history [_ type id start-t since]
    (with-open-coll [batch-db (batch-db/new-batch-db node t)]
      (p/-instance-history batch-db type id start-t since)))

  (-total-num-of-instance-changes [_ type id since]
    (with-open [batch-db (batch-db/new-batch-db node t)]
      (p/-total-num-of-instance-changes batch-db type id since)))



  ;; ---- Type-Level History Functions ----------------------------------------

  (-type-history [_ type start-t start-id since]
    (with-open-coll [batch-db (batch-db/new-batch-db node t)]
      (p/-type-history batch-db type start-t start-id since)))

  (-total-num-of-type-changes [_ type since]
    (with-open [batch-db (batch-db/new-batch-db node t)]
      (p/-total-num-of-type-changes batch-db type since)))



  ;; ---- System-Level History Functions --------------------------------------

  (-system-history [_ start-t start-type start-id since]
    (with-open-coll [batch-db (batch-db/new-batch-db node t)]
      (p/-system-history batch-db start-t start-type start-id since)))

  (-total-num-of-system-changes [_ since]
    (with-open [batch-db (batch-db/new-batch-db node t)]
      (p/-total-num-of-system-changes batch-db since)))



  ;; ---- Batch DB ------------------------------------------------------------

  (-new-batch-db [_]
    (batch-db/new-batch-db node t))



  ;; ---- QueryCompiler -------------------------------------------------------

  p/QueryCompiler
  (-compile-type-query [_ type clauses]
    (p/-compile-type-query node type clauses))

  (-compile-system-query [_ clauses]
    (p/-compile-system-query node clauses))

  (-compile-compartment-query [_ code type clauses]
    (p/-compile-compartment-query node code type clauses)))


(defmethod print-method Db [^Db db ^Writer w]
  (.write w (format "Db[t=%d]" (.t db))))


(defn db
  "Creates a database on `node` based on `t`."
  [node t]
  (->Db node t t))
