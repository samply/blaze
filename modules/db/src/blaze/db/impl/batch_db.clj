(ns blaze.db.impl.batch-db
  "Batch Database Implementation

  A batch database keeps key-value store iterators open in order to avoid the
  cost associated with open and closing them."
  (:require
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index :as index]
    [blaze.db.impl.index.compartment.resource :as cr]
    [blaze.db.impl.index.resource-as-of :as rao]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.impl.index.resource-id :as ri]
    [blaze.db.impl.index.search-param-value-resource :as sp-vr]
    [blaze.db.impl.index.system-as-of :as sao]
    [blaze.db.impl.index.system-stats :as system-stats]
    [blaze.db.impl.index.t-by-instant :as t-by-instant]
    [blaze.db.impl.index.type-as-of :as tao]
    [blaze.db.impl.index.type-stats :as type-stats]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.util :as u]
    [blaze.db.kv :as kv])
  (:import
    [clojure.lang IReduceInit]
    [java.io Writer]
    [java.lang AutoCloseable]))


(set! *warn-on-reflection* true)


(defrecord BatchDb [node basis-t context]
  p/Db
  (-node [_]
    node)

  (-basis-t [_]
    basis-t)



  ;; ---- Instance-Level Functions --------------------------------------------

  (-resource-handle [_ tid id]
    (when-let [did ((:resource-id context) tid id)]
      ((:resource-handle context) tid did)))



  ;; ---- Type-Level Functions ------------------------------------------------

  (-type-list [_ tid]
    (rao/type-list context tid))

  (-type-list [_ tid start-id]
    (when-let [start-did ((:resource-id context) tid start-id)]
      (rao/type-list context tid start-did)))

  (-type-total [_ tid]
    (let [{:keys [snapshot t]} context]
      (with-open [iter (type-stats/new-iterator snapshot)]
        (:total (type-stats/get! iter tid t) 0))))



  ;; ---- System-Level Functions ----------------------------------------------

  (-system-list [_]
    (rao/system-list context))

  (-system-list [_ start-tid start-id]
    (when-let [start-did ((:resource-id context) start-tid start-id)]
      (rao/system-list context start-tid start-did)))

  (-system-total [_]
    (let [{:keys [snapshot t]} context]
      (with-open [iter (system-stats/new-iterator snapshot)]
        (:total (system-stats/get! iter t) 0))))



  ;; ---- Compartment-Level Functions -----------------------------------------

  (-compartment-resource-handles [_ [code id] tid]
    (when-let [did ((:resource-id context) (codec/tid code) id)]
      (cr/resource-handles! context [(codec/c-hash code) did] tid)))

  (-compartment-resource-handles [_ [code id] tid start-id]
    (when-let [did ((:resource-id context) (codec/tid code) id)]
      (when-let [start-did ((:resource-id context) tid start-id)]
        (cr/resource-handles! context [(codec/c-hash code) did] tid start-did))))



  ;; ---- Common Query Functions ----------------------------------------------

  (-execute-query [_ query]
    (p/-execute query context))

  (-execute-query [_ query arg1]
    (p/-execute query context arg1))



  ;; ---- Instance-Level History Functions ------------------------------------

  (-instance-history [_ tid id start-t since]
    (let [{:keys [snapshot resource-id raoi t]} context
          start-t (if (some-> start-t (<= t)) start-t t)
          end-t (or (some->> since (t-by-instant/t-by-instant snapshot)) 0)]
      (when-let [did (resource-id tid id)]
        (rao/instance-history raoi tid did start-t end-t))))

  (-total-num-of-instance-changes [_ tid id since]
    (let [{:keys [snapshot resource-id resource-handle t]} context
          end-t (or (some->> since (t-by-instant/t-by-instant snapshot)) 0)]
      (if-let [did (resource-id tid id)]
        (rao/num-of-instance-changes resource-handle tid did t end-t)
        0)))



  ;; ---- Type-Level History Functions ----------------------------------------

  (-type-history [_ tid start-t start-id since]
    (let [{:keys [snapshot t resource-id]} context
          start-t (if (some-> start-t (<= t)) start-t t)
          end-t (or (some->> since (t-by-instant/t-by-instant snapshot)) 0)
          start-did (some->> start-id (resource-id tid))]
      (reify IReduceInit
        (reduce [_ rf init]
          (with-open [taoi (kv/new-iterator snapshot :type-as-of-index)]
            (reduce rf init (tao/type-history taoi tid start-t start-did end-t)))))))

  (-total-num-of-type-changes [_ type since]
    (let [{:keys [snapshot t]} context
          tid (codec/tid type)
          end-t (some->> since (t-by-instant/t-by-instant snapshot))]
      (with-open [snapshot (kv/new-snapshot (:kv-store node))
                  iter (type-stats/new-iterator snapshot)]
        (- (:num-changes (type-stats/get! iter tid t) 0)
           (:num-changes (some->> end-t (type-stats/get! iter tid)) 0)))))



  ;; ---- System-Level History Functions --------------------------------------

  (-system-history [_ start-t start-tid start-id since]
    (let [{:keys [snapshot t resource-id]} context
          start-t (if (some-> start-t (<= t)) start-t t)
          end-t (or (some->> since (t-by-instant/t-by-instant snapshot)) 0)
          start-did (some->> start-id (resource-id start-tid))]
      (reify IReduceInit
        (reduce [_ rf init]
          (with-open [saoi (kv/new-iterator snapshot :system-as-of-index)]
            (reduce rf init (sao/system-history saoi start-t start-tid start-did end-t)))))))

  (-total-num-of-system-changes [_ since]
    (let [{:keys [snapshot t]} context
          end-t (some->> since (t-by-instant/t-by-instant snapshot))]
      (with-open [snapshot (kv/new-snapshot (:kv-store node))
                  iter (system-stats/new-iterator snapshot)]
        (- (:num-changes (system-stats/get! iter t) 0)
           (:num-changes (some->> end-t (system-stats/get! iter)) 0)))))



  ;; ---- Include ---------------------------------------------------------------

  (-include [_ resource-handle code]
    (index/targets! context resource-handle (codec/c-hash code)))

  (-include [_ resource-handle code target-type]
    (index/targets! context resource-handle (codec/c-hash code)
                    (codec/tid target-type)))


  (-rev-include [_ resource-handle source-type code]
    (let [{:keys [svri]} context
          reference (codec/v-hash (rh/reference resource-handle))
          source-tid (codec/tid source-type)]
      (coll/eduction
        (u/resource-handle-mapper context source-tid)
        (sp-vr/prefix-keys! svri (codec/c-hash code) source-tid
                            reference reference))))



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
    (p/-pull-many node resource-handles))

  AutoCloseable
  (close [_]
    (let [{:keys [snapshot raoi svri rsvi cri csvri]} context]
      (.close ^AutoCloseable raoi)
      (.close ^AutoCloseable svri)
      (.close ^AutoCloseable rsvi)
      (.close ^AutoCloseable cri)
      (.close ^AutoCloseable csvri)
      (.close ^AutoCloseable snapshot))))


(defmethod print-method BatchDb [^BatchDb db ^Writer w]
  (.write w (format "BatchDb[t=%d]" (:t (.context db)))))


(defn- decode-clauses [clauses]
  (mapv
    (fn [[search-param modifier values]]
      (into [(cond-> (:code search-param) modifier (str ":" modifier))] values))
    clauses))


(defrecord TypeQuery [tid clauses]
  p/Query
  (-execute [_ context]
    (index/type-query context tid clauses))
  (-execute [_ {:keys [resource-id] :as context} start-id]
    (when-let [start-did (resource-id tid start-id)]
      (index/type-query context tid clauses start-did)))
  (-clauses [_]
    (decode-clauses clauses)))


(defrecord EmptyTypeQuery [tid]
  p/Query
  (-execute [_ context]
    (rao/type-list context tid))
  (-execute [_ {:keys [resource-id] :as context} start-id]
    (when-let [start-did (resource-id tid start-id)]
      (rao/type-list context tid start-did)))
  (-clauses [_]))


(defrecord SystemQuery [clauses]
  p/Query
  (-execute [_ context]
    (index/system-query context clauses)))


(defrecord CompartmentQuery [c-hash c-tid tid clauses]
  p/Query
  (-execute [_ {:keys [resource-id] :as context} compartment-id]
    (when-let [c-res-did (resource-id c-tid compartment-id)]
      (index/compartment-query context [c-hash c-res-did] tid clauses)))
  (-clauses [_]
    (decode-clauses clauses)))


(defrecord EmptyCompartmentQuery [c-hash c-tid tid]
  p/Query
  (-execute [_ {:keys [resource-id] :as context} compartment-id]
    (when-let [c-res-did (resource-id c-tid compartment-id)]
      (cr/resource-handles! context [c-hash c-res-did] tid)))
  (-clauses [_]))


(defn new-batch-db
  "Creates a new batch database.

  A batch database can be used instead of a normal database. It's functionally
  the same. Only the performance for multiple calls differs. It's not thread
  save and has to be closed after usage because it holds open iterators."
  ^AutoCloseable
  [{:keys [kv-store rh-cache] :as node} basis-t t]
  (let [snapshot (kv/new-snapshot kv-store)]
    (->BatchDb
      node
      basis-t
      (let [raoi (kv/new-iterator snapshot :resource-as-of-index)]
        {:snapshot snapshot
         :resource-id (ri/resource-id kv-store)
         :raoi raoi
         :resource-handle (rao/resource-handle rh-cache raoi t)
         :svri (kv/new-iterator snapshot :search-param-value-index)
         :rsvi (kv/new-iterator snapshot :resource-value-index)
         :cri (kv/new-iterator snapshot :compartment-resource-type-index)
         :csvri (kv/new-iterator snapshot :compartment-search-param-value-index)
         :t t}))))
