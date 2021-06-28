(ns blaze.db.impl.db
  "Primary Database Implementation"
  (:require
    [blaze.db.impl.batch-db :as batch-db]
    [blaze.db.impl.index.resource-as-of :as rao]
    [blaze.db.impl.protocols :as p]
    [blaze.db.kv :as kv])
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
         (.reduce ~(vary-meta coll assoc :tag `IReduceInit) rf# init#)))
     Seqable
     (seq [this#]
       (.seq ^Seqable (persistent! (.reduce this# conj! (transient [])))))
     Counted
     (count [this#]
       (.reduce this# (fn ^long [^long sum# ~'_] (inc sum#)) 0))))


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

  (-resource-handle [_ tid id]
    (let [{:keys [kv-store rh-cache]} node]
      (with-open [snapshot (kv/new-snapshot kv-store)
                  raoi (kv/new-iterator snapshot :resource-as-of-index)]
        ((rao/resource-handle rh-cache raoi t) tid id))))



  ;; ---- Type-Level Functions ------------------------------------------------

  (-type-list [_ tid]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-type-list batch-db tid)))

  (-type-list [_ tid start-id]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-type-list batch-db tid start-id)))

  (-type-total [_ tid]
    (with-open [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-type-total batch-db tid)))



  ;; ---- System-Level Functions ----------------------------------------------

  (-system-list [_]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-system-list batch-db)))

  (-system-list [_ start-tid start-id]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-system-list batch-db start-tid start-id)))

  (-system-total [_]
    (with-open [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-system-total batch-db)))



  ;; ---- Compartment-Level Functions -----------------------------------------

  (-compartment-resource-handles [_ compartment tid]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-compartment-resource-handles batch-db compartment tid)))

  (-compartment-resource-handles [_ compartment tid start-id]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-compartment-resource-handles batch-db compartment tid start-id)))



  ;; ---- Common Query Functions ----------------------------------------------

  (-execute-query [_ query]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-execute-query batch-db query)))

  (-execute-query [_ query arg1]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-execute-query batch-db query arg1)))



  ;; ---- Instance-Level History Functions ------------------------------------

  (-instance-history [_ tid id start-t since]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-instance-history batch-db tid id start-t since)))

  (-total-num-of-instance-changes [_ tid id since]
    (with-open [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-total-num-of-instance-changes batch-db tid id since)))



  ;; ---- Type-Level History Functions ----------------------------------------

  (-type-history [_ tid start-t start-id since]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-type-history batch-db tid start-t start-id since)))

  (-total-num-of-type-changes [_ type since]
    (with-open [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-total-num-of-type-changes batch-db type since)))



  ;; ---- System-Level History Functions --------------------------------------

  (-system-history [_ start-t start-tid start-id since]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-system-history batch-db start-t start-tid start-id since)))

  (-total-num-of-system-changes [_ since]
    (with-open [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-total-num-of-system-changes batch-db since)))



  ;; ---- Include ---------------------------------------------------------------

  (-include [_ resource-handle code]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-include batch-db resource-handle code)))

  (-include [_ resource-handle code target-type]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-include batch-db resource-handle code target-type)))

  (-rev-include [_ resource-handle source-type code]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-rev-include batch-db resource-handle source-type code)))



  ;; ---- Batch DB ------------------------------------------------------------

  (-new-batch-db [_]
    (batch-db/new-batch-db node basis-t t))



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
    (p/-pull-content node resource-handle))

  (-pull-many [_ resource-handles]
    (p/-pull-many node resource-handles)))


(defmethod print-method Db [^Db db ^Writer w]
  (.write w (format "Db[t=%d]" (.t db))))


(defn db
  "Creates a database on `node` based on `t`."
  [node t]
  (->Db node t t))
