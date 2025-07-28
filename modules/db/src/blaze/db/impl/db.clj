(ns blaze.db.impl.db
  "Primary Database Implementation"
  (:require
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :refer [with-open-coll]]
   [blaze.db.impl.batch-db :as batch-db]
   [blaze.db.impl.index.patient-last-change :as plc]
   [blaze.db.impl.index.system-stats :as system-stats]
   [blaze.db.impl.index.type-stats :as type-stats]
   [blaze.db.impl.protocols :as p]
   [blaze.db.kv :as kv])
  (:import
   [java.io Writer]
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defrecord Db [node kv-store basis-t t]
  p/Db
  (-node [_]
    node)

  (-as-of [_ t]
    (assert (<= ^long t ^long basis-t) (format "(<= %d %d)" t basis-t))
    (Db. node kv-store basis-t t))

  (-basis-t [_]
    basis-t)

  (-as-of-t [_]
    (when (not= basis-t t) t))

  ;; ---- Instance-Level Functions --------------------------------------------

  (-resource-handle [_ tid id]
    (with-open [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-resource-handle batch-db tid id)))

  ;; ---- Type-Level Functions ------------------------------------------------

  (-type-list [_ tid]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-type-list batch-db tid)))

  (-type-list [_ tid start-id]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-type-list batch-db tid start-id)))

  (-type-total [_ tid]
    (with-open [snapshot (kv/new-snapshot kv-store)]
      (:total (type-stats/seek-value snapshot tid t) 0)))

  ;; ---- System-Level Functions ----------------------------------------------

  (-system-list [_]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-system-list batch-db)))

  (-system-list [_ start-tid start-id]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-system-list batch-db start-tid start-id)))

  (-system-total [_]
    (with-open [snapshot (kv/new-snapshot kv-store)]
      (:total (system-stats/seek-value snapshot t) 0)))

  ;; ---- Compartment-Level Functions -----------------------------------------

  (-compartment-resource-handles [_ compartment tid]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-compartment-resource-handles batch-db compartment tid)))

  (-compartment-resource-handles [_ compartment tid start-id]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-compartment-resource-handles batch-db compartment tid start-id)))

  ;; ---- Patient-Compartment-Level Functions ---------------------------------

  (-patient-compartment-last-change-t [_ patient-id]
    (with-open [snapshot (kv/new-snapshot kv-store)
                plci (kv/new-iterator snapshot :patient-last-change-index)]
      (plc/last-change-t plci patient-id t)))

  ;; ---- Common Query Functions ----------------------------------------------

  (-count-query [_ query]
    (let [batch-db (batch-db/new-batch-db node basis-t t)]
      (-> (p/-count-query batch-db query)
          (ac/when-complete (fn [_ _] (.close batch-db))))))

  (-execute-query [_ query]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-execute-query batch-db query)))

  (-execute-query [_ query arg1]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-execute-query batch-db query arg1)))

  (-matcher-transducer [_ clauses]
    (fn [rf]
      (let [batch-db (batch-db/new-batch-db node basis-t t)
            rf ((p/-matcher-transducer batch-db clauses) rf)]
        (fn
          ([result]
           (.close ^AutoCloseable batch-db)
           (rf result))
          ([result input]
           (rf result input))))))

  ;; ---- History Functions ---------------------------------------------------

  (-stop-history-at [_ instant]
    (with-open [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-stop-history-at batch-db instant)))

  ;; ---- Instance-Level History Functions ------------------------------------

  (-instance-history [_ tid id start-t]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-instance-history batch-db tid id start-t)))

  (-total-num-of-instance-changes [_ tid id since]
    (with-open [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-total-num-of-instance-changes batch-db tid id since)))

  ;; ---- Type-Level History Functions ----------------------------------------

  (-type-history [_ tid start-t start-id]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-type-history batch-db tid start-t start-id)))

  (-total-num-of-type-changes [_ type since]
    (with-open [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-total-num-of-type-changes batch-db type since)))

  ;; ---- System-Level History Functions --------------------------------------

  (-system-history [_ start-t start-tid start-id]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-system-history batch-db start-t start-tid start-id)))

  (-total-num-of-system-changes [_ since]
    (with-open [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-total-num-of-system-changes batch-db since)))

  (-changes [_]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-changes batch-db)))

  ;; ---- Include ---------------------------------------------------------------

  (-include [_ resource-handle code]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-include batch-db resource-handle code)))

  (-include [_ resource-handle code target-type]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-include batch-db resource-handle code target-type)))

  (-rev-include [_ resource-handle]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-rev-include batch-db resource-handle)))

  (-rev-include [_ resource-handle source-type code]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-rev-include batch-db resource-handle source-type code)))

  (-patient-everything [_ patient-handle start end]
    (with-open-coll [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-patient-everything batch-db patient-handle start end)))

  (-re-index-total [_ search-param-url]
    (with-open [batch-db (batch-db/new-batch-db node basis-t t)]
      (p/-re-index-total batch-db search-param-url)))

  (-re-index [_ search-param-url]
    (let [batch-db (batch-db/new-batch-db node basis-t t)]
      (do-sync [next (p/-re-index batch-db search-param-url)]
        (.close batch-db)
        next)))

  (-re-index [_ search-param-url start-type start-id]
    (let [batch-db (batch-db/new-batch-db node basis-t t)]
      (do-sync [next (p/-re-index batch-db search-param-url start-type start-id)]
        (.close batch-db)
        next)))

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

  (-compile-type-matcher [_ type clauses]
    (p/-compile-type-matcher node type clauses))

  (-compile-system-query [_ clauses]
    (p/-compile-system-query node clauses))

  (-compile-system-matcher [_ clauses]
    (p/-compile-system-matcher node clauses))

  (-compile-compartment-query [_ code type clauses]
    (p/-compile-compartment-query node code type clauses))

  (-compile-compartment-query-lenient [_ code type clauses]
    (p/-compile-compartment-query-lenient node code type clauses))

  ;; ---- Pull ----------------------------------------------------------------

  p/Pull
  (-pull [_ resource-handle variant]
    (p/-pull node resource-handle variant))

  (-pull-content [_ resource-handle variant]
    (p/-pull-content node resource-handle variant))

  (-pull-many [_ resource-handles variant]
    (p/-pull-many node resource-handles variant)))

(defmethod print-method Db [^Db db ^Writer w]
  (.write w (format "Db[t=%d]" (.t db))))

(defn db
  "Creates a database on `node` based on `t`."
  [node t]
  (->Db node (:kv-store node) t t))
