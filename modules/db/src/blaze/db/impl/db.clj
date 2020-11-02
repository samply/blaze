(ns blaze.db.impl.db
  "Primary Database Implementation"
  (:require
    [blaze.db.impl.batch-db :as batch-db]
    [blaze.db.impl.protocols :as p])
  (:import
    [clojure.lang IReduceInit Sequential Seqable Counted]
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
         (.reduce ~coll rf# init#)))
     Seqable
     (seq [this#]
       (.seq ^Seqable (persistent! (.reduce this# conj! (transient [])))))
     Counted
     (count [this#]
       (.reduce this# (fn [sum# ~'_] (inc sum#)) 0))))


(deftype Db [node basis-t t]
  p/Db
  (-node [_]
    node)

  (-as-of [_ t]
    (assert (<= t basis-t))
    (Db. node basis-t t))

  (-basis-t [_]
    basis-t)

  (-as-of-t [_]
    (when (not= basis-t t) t))



  ;; ---- Instance-Level Functions --------------------------------------------

  (-resource-handle [_ type id]
    (with-open [batch-db (batch-db/new-batch-db node t)]
      (p/-resource-handle batch-db type id)))



  ;; ---- Type-Level Functions ------------------------------------------------

  (-list-resource-handles [_ type start-id]
    (with-open-coll [batch-db (batch-db/new-batch-db node t)]
      (p/-list-resource-handles batch-db type start-id)))

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

  (-list-compartment-resource-handles [_ code id type start-id]
    (with-open-coll [batch-db (batch-db/new-batch-db node t)]
      (p/-list-compartment-resource-handles batch-db code id type start-id)))



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



  ;; ---- Transaction ---------------------------------------------------------

  p/Tx
  (-tx [_ t]
    (p/-tx node t))


  ;; ---- QueryCompiler -------------------------------------------------------

  p/QueryCompiler
  (-compile-type-query [_ type clauses]
    (p/-compile-type-query node type clauses))

  (-compile-type-query-lenient [_ type clauses]
    (p/-compile-type-query-lenient node type clauses))

  (-compile-system-query [_ clauses]
    (p/-compile-system-query node clauses))

  (-compile-compartment-query [_ code type clauses]
    (p/-compile-compartment-query node code type clauses))

  (-compile-compartment-query-lenient [_ code type clauses]
    (p/-compile-compartment-query-lenient node code type clauses))



  ;; ---- Pull ----------------------------------------------------------------

  p/Pull
  (-pull [_ resource-handle]
    (p/-pull node resource-handle))

  (-pull-content [_ resource-handle]
    (p/-pull-content node resource-handle)))


(defmethod print-method Db [^Db db ^Writer w]
  (.write w (format "Db[t=%d]" (.t db))))


(defn db
  "Creates a database on `node` based on `t`."
  [node t]
  (->Db node t t))
