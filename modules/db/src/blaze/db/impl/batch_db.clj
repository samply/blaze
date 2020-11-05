(ns blaze.db.impl.batch-db
  "Batch Database Implementation

  A batch database keeps key-value store iterators open in order to avoid the
  cost associated with open and closing them."
  (:require
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index :as index]
    [blaze.db.impl.index.resource-as-of :as resource-as-of]
    [blaze.db.impl.index.system-as-of :as system-as-of]
    [blaze.db.impl.index.system-stats :as system-stats]
    [blaze.db.impl.index.type-as-of :as type-as-of]
    [blaze.db.impl.index.type-stats :as type-stats]
    [blaze.db.impl.protocols :as p]
    [blaze.db.kv :as kv])
  (:import
    [java.io Closeable Writer]
    [clojure.lang IReduceInit]))


(set! *warn-on-reflection* true)


(defrecord BatchDb [node context]
  p/Db
  (-node [_]
    node)

  ;; ---- Instance-Level Functions --------------------------------------------

  (-resource-handle [_ type id]
    (resource-as-of/resource-handle context (codec/tid type)
                                    (codec/id-bytes id)))



  ;; ---- Type-Level Functions ------------------------------------------------

  (-list-resource-handles [_ type start-id]
    (resource-as-of/type-list context (codec/tid type)
                              (some-> start-id codec/id-bytes)))

  (-type-total [_ type]
    (let [{:keys [snapshot t]} context]
      (with-open [iter (type-stats/new-iterator snapshot)]
        (:total (type-stats/get! iter (codec/tid type) t) 0))))



  ;; ---- System-Level Functions ----------------------------------------------

  (-system-list [_ start-type start-id]
    (resource-as-of/system-list context (some-> start-type codec/tid)
                                (some-> start-id codec/id-bytes)))

  (-system-total [_]
    (let [{:keys [snapshot t]} context]
      (with-open [iter (system-stats/new-iterator snapshot)]
        (:total (system-stats/get! iter t) 0))))



  ;; ---- Compartment-Level Functions -----------------------------------------

  (-list-compartment-resource-handles [_ code id type start-id]
    (let [compartment {:c-hash (codec/c-hash code) :res-id (codec/id-bytes id)}]
      (index/compartment-list context compartment (codec/tid type)
                              (some-> start-id codec/id-bytes))))



  ;; ---- Common Query Functions ----------------------------------------------

  (-execute-query [_ query]
    (p/-execute query context))

  (-execute-query [_ query arg1]
    (p/-execute query context arg1))



  ;; ---- Instance-Level History Functions ------------------------------------

  (-instance-history [_ type id start-t since]
    (let [{:keys [snapshot raoi t]} context
          start-t (if (some-> start-t (<= t)) start-t t)
          end-t (or (some->> since (index/t-by-instant snapshot)) 0)]
      (resource-as-of/instance-history raoi (codec/tid type) id start-t end-t)))

  (-total-num-of-instance-changes [_ type id since]
    (let [{:keys [snapshot raoi t]} context
          end-t (or (some->> since (index/t-by-instant snapshot)) 0)]
      (resource-as-of/num-of-instance-changes raoi (codec/tid type)
                                              (codec/id-bytes id) t end-t)))



  ;; ---- Type-Level History Functions ----------------------------------------

  (-type-history [_ type start-t start-id since]
    (let [{:keys [snapshot t]} context
          tid (codec/tid type)
          start-t (if (some-> start-t (<= t)) start-t t)
          start-id (some-> start-id codec/id-bytes)
          end-t (or (some->> since (index/t-by-instant snapshot)) 0)]
      (reify IReduceInit
        (reduce [_ rf init]
          (with-open [taoi (kv/new-iterator snapshot :type-as-of-index)]
            (.reduce (type-as-of/type-history taoi tid start-t start-id end-t)
                     rf init))))))

  (-total-num-of-type-changes [_ type since]
    (let [{:keys [snapshot t]} context
          tid (codec/tid type)
          end-t (some->> since (index/t-by-instant snapshot))]
      (with-open [snapshot (kv/new-snapshot (:kv-store node))
                  iter (type-stats/new-iterator snapshot)]
        (- (:num-changes (type-stats/get! iter tid t) 0)
           (:num-changes (some->> end-t (type-stats/get! iter tid)) 0)))))



  ;; ---- System-Level History Functions --------------------------------------

  (-system-history [_ start-t start-type start-id since]
    (let [{:keys [snapshot t]} context
          start-t (if (some-> start-t (<= t)) start-t t)
          start-tid (some-> start-type codec/tid)
          start-id (some-> start-id codec/id-bytes)
          end-t (or (some->> since (index/t-by-instant snapshot)) 0)]
      (reify IReduceInit
        (reduce [_ rf init]
          (with-open [saoi (kv/new-iterator snapshot :system-as-of-index)]
            (.reduce (system-as-of/system-history saoi start-t start-tid
                                                  start-id end-t)
                     rf init))))))

  (-total-num-of-system-changes [_ since]
    (let [{:keys [snapshot t]} context
          end-t (some->> since (index/t-by-instant snapshot))]
      (with-open [snapshot (kv/new-snapshot (:kv-store node))
                  iter (system-stats/new-iterator snapshot)]
        (- (:num-changes (system-stats/get! iter t) 0)
           (:num-changes (some->> end-t (system-stats/get! iter)) 0)))))



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

  Closeable
  (close [_]
    (let [{:keys [snapshot raoi svri rsvi cri csvri]} context]
      (.close ^Closeable raoi)
      (.close ^Closeable svri)
      (.close ^Closeable rsvi)
      (.close ^Closeable cri)
      (.close ^Closeable csvri)
      (.close ^Closeable snapshot))))


(defmethod print-method BatchDb [^BatchDb db ^Writer w]
  (.write w (format "BatchDb[t=%d]" (:t (.context db)))))


(defn- decode-clauses [clauses]
  (mapv
    (fn [[search-param modifier values]]
      (cons (cond-> (:code search-param) modifier (str ":" modifier))
            values))
    clauses))


(defrecord TypeQuery [tid clauses]
  p/Query
  (-execute [_ context]
    (index/type-query context tid clauses nil))
  (-execute [_ context start-id]
    (index/type-query context tid clauses
                      (some-> start-id codec/id-bytes)))
  (-clauses [_]
    (decode-clauses clauses)))


(defrecord EmptyTypeQuery [tid]
  p/Query
  (-execute [_ context]
    (resource-as-of/type-list context tid nil))
  (-execute [_ context start-id]
    (resource-as-of/type-list context tid (some-> start-id codec/id-bytes)))
  (-clauses [_]))


(defrecord SystemQuery [clauses]
  p/Query
  (-execute [_ context]
    (index/system-query context clauses)))


(defrecord CompartmentQuery [c-hash tid clauses]
  p/Query
  (-execute [_ context arg1]
    (let [compartment {:c-hash c-hash :res-id (codec/id-bytes arg1)}]
      (index/compartment-query context compartment tid clauses)))
  (-clauses [_]
    (decode-clauses clauses)))


(defrecord EmptyCompartmentQuery [c-hash tid]
  p/Query
  (-execute [_ context arg1]
    (let [compartment {:c-hash c-hash :res-id (codec/id-bytes arg1)}]
      (index/compartment-list context compartment tid nil)))
  (-clauses [_]))


(defn new-batch-db
  "Creates a new batch database.

  A batch database can be used instead of a normal database. It's functionally
  the same. Only the performance for multiple calls differs. It's not thread
  save and has to be closed after usage because it holds open iterators."
  ^Closeable
  [{:keys [kv-store] :as node} t]
  (let [snapshot (kv/new-snapshot kv-store)]
    (->BatchDb
      node
      {:snapshot snapshot
       :raoi (kv/new-iterator snapshot :resource-as-of-index)
       :svri (kv/new-iterator snapshot :search-param-value-index)
       :rsvi (kv/new-iterator snapshot :resource-value-index)
       :cri (kv/new-iterator snapshot :compartment-resource-type-index)
       :csvri (kv/new-iterator snapshot :compartment-search-param-value-index)
       :t t})))
