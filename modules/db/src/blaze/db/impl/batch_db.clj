(ns blaze.db.impl.batch-db
  "Batch Database Implementation

  A batch database keeps key-value store iterators open in order to avoid the
  cost associated with open and closing them."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.impl.batch-db.patient-everything :as pe]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index :as index]
   [blaze.db.impl.index.compartment.resource :as cr]
   [blaze.db.impl.index.patient-last-change :as plc]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.index.system-as-of :as sao]
   [blaze.db.impl.index.system-stats :as system-stats]
   [blaze.db.impl.index.type-as-of :as tao]
   [blaze.db.impl.index.type-stats :as type-stats]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.chained :as spc]
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.kv :as kv]
   [blaze.db.node.resource-indexer :as resource-indexer]
   [blaze.db.search-param-registry :as sr]
   [blaze.fhir.spec.type :as type]
   [blaze.util :refer [str]])
  (:import
   [java.io Writer]
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

(defn- rev-include [batch-db snapshot reference source-tid code]
  (coll/eduction
   (u/resource-handle-xf batch-db source-tid)
   (sp-vr/index-handles snapshot code source-tid (bs/size reference) reference)))

(defn- sp-total
  [db {:keys [base]}]
  (if (= ["Resource"] base)
    (d/system-total db)
    (transduce (map (partial d/type-total db)) + base)))

(defn- sp-list
  "Returns a reducible collection of all resource handles of base types of
  `search-param` in `db` optionally starting with `start-type` and `start-id`."
  {:arglists '([db search-param] [db search-param start-type start-id])}
  ([db {:keys [base]}]
   (if (= ["Resource"] base)
     (d/system-list db)
     (coll/eduction (mapcat (partial d/type-list db)) base)))
  ([db {:keys [base]} start-type start-id]
   (if (= ["Resource"] base)
     (d/system-list db start-type start-id)
     (let [rest (drop 1 (drop-while (complement #{start-type}) base))]
       (coll/eduction
        cat
        [(d/type-list db start-type start-id)
         (coll/eduction (mapcat (partial d/type-list db)) rest)])))))

(defn- sp-get-by-url [{{:keys [search-param-registry]} :node} url]
  (or (sr/get-by-url search-param-registry url)
      (ba/not-found (format "Search parameter with URL `%s` not found." url))))

(defrecord BatchDb [node kv-store snapshot basis-t t since-t]
  p/Db
  (-node [_]
    node)

  (-basis-t [_]
    basis-t)

  (-as-of [_ _]
    (ba/unsupported "As of is not supported on batch-db."))

  (-as-of-t [_]
    (when (not= basis-t t) t))

  (-since [_ _]
    (ba/unsupported "Since is not supported on batch-db."))

  (-since-t [_]
    since-t)

  ;; ---- Instance-Level Functions --------------------------------------------

  (-resource-handle [_ tid id]
    (rao/resource-handle snapshot t since-t tid id))

  ;; ---- Type-Level Functions ------------------------------------------------

  (-type-list [db tid]
    (rao/type-list db tid))

  (-type-list [db tid start-id]
    (rao/type-list db tid start-id))

  (-type-total [_ tid]
    (if (zero? since-t)
      (:total (type-stats/seek-value snapshot tid t) 0)
      (ba/unsupported "Total is not supported on since-dbs.")))

  ;; ---- System-Level Functions ----------------------------------------------

  (-system-list [db]
    (rao/system-list db))

  (-system-list [db start-tid start-id]
    (rao/system-list db start-tid start-id))

  (-system-total [_]
    (if (zero? since-t)
      (:total (system-stats/seek-value snapshot t) 0)
      (ba/unsupported "Total is not supported on since-dbs.")))

  ;; ---- Compartment-Level Functions -----------------------------------------

  (-compartment-resource-handles [db compartment tid]
    (cr/resource-handles db compartment tid))

  (-compartment-resource-handles [db compartment tid start-id]
    (cr/resource-handles db compartment tid start-id))

  ;; ---- Patient-Compartment-Level Functions ---------------------------------

  (-patient-compartment-last-change-t [_ patient-id]
    (with-open [plci (kv/new-iterator snapshot :patient-last-change-index)]
      (plc/last-change-t plci patient-id t)))

  ;; ---- Common Query Functions ----------------------------------------------

  (-count-query [db query]
    (p/-count query db))

  (-execute-query [db query]
    (p/-execute query db))

  (-execute-query [db query arg1]
    (p/-execute query db arg1))

  (-explain-query [db query]
    (p/-query-plan query db))

  (-matcher-transducer [db matcher]
    (p/-transducer matcher db))

  ;; ---- Instance-Level History Functions ------------------------------------

  (-instance-history [_ tid id start-t]
    (let [start-t (if (some-> start-t (<= t)) start-t t)]
      (rao/instance-history snapshot t since-t tid id start-t)))

  (-total-num-of-instance-changes [_ tid id]
    (count (rao/instance-history snapshot t since-t tid id t)))

  ;; ---- Type-Level History Functions ----------------------------------------

  (-type-history [_ tid start-t start-id]
    (let [start-t (if (some-> start-t (<= t)) start-t t)]
      (tao/type-history snapshot t since-t tid start-t start-id)))

  (-total-num-of-type-changes [_ tid]
    (cond-> (:num-changes (type-stats/seek-value snapshot tid t) 0)
      (pos? since-t)
      (- (:num-changes (type-stats/seek-value snapshot tid since-t) 0))))

  ;; ---- System-Level History Functions --------------------------------------

  (-system-history [db start-t start-tid start-id]
    (let [start-t (if (some-> start-t (<= t)) start-t t)]
      (sao/system-history db start-t start-tid start-id)))

  (-total-num-of-system-changes [_]
    (cond-> (:num-changes (system-stats/seek-value snapshot t) 0)
      (pos? since-t)
      (- (:num-changes (system-stats/seek-value snapshot since-t) 0))))

  (-changes [_]
    (sao/changes snapshot t))

  ;; ---- Include ---------------------------------------------------------------

  (-include [db resource-handle code]
    (spc/targets db resource-handle (codec/c-hash code)))

  (-include [db resource-handle code target-type]
    (spc/targets db resource-handle (codec/c-hash code)
                 (codec/tid target-type)))

  (-rev-include [db resource-handle]
    (let [search-param-registry (:search-param-registry node)
          type (name (type/type resource-handle))
          reference (rh/tid-id resource-handle)]
      (coll/eduction
       (comp
        (mapcat
         (fn [{:keys [base code]}]
           (let [code (codec/c-hash code)]
             (coll/eduction
              (mapcat #(rev-include db snapshot reference (codec/tid %) code))
              base))))
        (distinct))
       (sr/list-by-target-type search-param-registry type))))

  (-rev-include [db resource-handle source-type code]
    (rev-include db snapshot (rh/tid-id resource-handle)
                 (codec/tid source-type) (codec/c-hash code)))

  (-patient-everything [db patient-handle start end]
    (pe/patient-everything db patient-handle start end))

  (-re-index-total [db search-param-url]
    (when-ok [search-param (sp-get-by-url db search-param-url)]
      (sp-total db search-param)))

  (-re-index [db search-param-url]
    (if-ok [search-param (sp-get-by-url db search-param-url)]
      (resource-indexer/re-index-resources (:resource-indexer node) search-param (sp-list db search-param))
      ac/completed-future))

  (-re-index [db search-param-url start-type start-id]
    (if-ok [search-param (sp-get-by-url db search-param-url)]
      (resource-indexer/re-index-resources (:resource-indexer node) search-param (sp-list db search-param start-type start-id))
      ac/completed-future))

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

  (-compile-type-matcher [_ type clause]
    (p/-compile-type-matcher node type clause))

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
    (p/-pull-many node resource-handles variant))

  AutoCloseable
  (close [_]
    (.close ^AutoCloseable snapshot)))

(defmethod print-method BatchDb [^BatchDb db ^Writer w]
  (.write w (format "BatchDb[t=%d]" (.t db))))

(defn- decode-clauses [clauses]
  (into
   []
   (keep
    (fn [[search-param modifier values]]
      (if (#{"asc" "desc"} modifier)
        [:sort (:code search-param) (keyword modifier)]
        (into [(cond-> (:code search-param) modifier (str ":" modifier))] values))))
   clauses))

(defrecord TypeQuery [tid clauses]
  p/Query
  (-count [_ batch-db]
    (index/type-query-total batch-db tid clauses))
  (-execute [_ batch-db]
    (index/type-query batch-db tid clauses))
  (-execute [_ batch-db start-id]
    (index/type-query batch-db tid clauses (codec/id-byte-string start-id)))
  (-query-clauses [_]
    (decode-clauses clauses))
  (-query-plan [_ batch-db]
    (index/type-query-plan batch-db tid clauses)))

(def ^:private ^:const ^long patient-compartment-hash (codec/c-hash "Patient"))
(def ^:private ^:const ^long patient-code-hash (codec/c-hash "patient"))

(defn- start-patient-id [batch-db tid start-id]
  (let [start-handle (p/-resource-handle batch-db tid start-id)
        start-patient-handle (coll/first (spc/targets batch-db start-handle patient-code-hash))]
    (codec/id-byte-string (rh/id start-patient-handle))))

;; A type query over resources with `tid` and patients with `patient-ids`.
(defrecord PatientTypeQuery [tid patient-ids compartment-clause clauses
                             compartment-query]
  p/Query
  (-count [query batch-db]
    (ac/completed-future (count (p/-execute query batch-db))))
  (-execute [_ batch-db]
    (coll/eduction (mapcat #(compartment-query batch-db %)) patient-ids))
  (-execute [_ batch-db start-id]
    (let [start-id (codec/id-byte-string start-id)
          start-patient-id (start-patient-id batch-db tid start-id)]
      (coll/eduction
       cat
       [(compartment-query batch-db start-patient-id start-id)
        (coll/eduction
         (comp (drop-while #(not= start-patient-id %))
               (drop 1)
               (mapcat #(compartment-query batch-db %)))
         patient-ids)])))
  (-query-clauses [_]
    (decode-clauses (into [compartment-clause] clauses)))
  (-query-plan [_ _]
    (index/compartment-query-plan clauses)))

(defn patient-type-query [tid patient-ids compartment-clause clauses]
  (->PatientTypeQuery
   tid patient-ids compartment-clause clauses
   (fn
     ([batch-db patient-id]
      (index/compartment-query
       batch-db [patient-compartment-hash patient-id] tid clauses))
     ([batch-db patient-id start-id]
      (index/compartment-query
       batch-db [patient-compartment-hash patient-id] tid clauses start-id)))))

(defrecord EmptyTypeQuery [tid]
  p/Query
  (-count [_ batch-db]
    (ac/completed-future
     (:total (type-stats/seek-value (:snapshot batch-db) tid (:t batch-db)) 0)))
  (-execute [_ batch-db]
    (rao/type-list batch-db tid))
  (-execute [_ batch-db start-id]
    (rao/type-list batch-db tid (codec/id-byte-string start-id)))
  (-query-clauses [_])
  (-query-plan [_ _]
    {:query-type :type}))

(defrecord SystemQuery [clauses]
  p/Query
  (-execute [_ batch-db]
    (index/system-query batch-db clauses)))

(defrecord CompartmentQuery [c-hash tid clauses]
  p/Query
  (-execute [_ batch-db arg1]
    (index/compartment-query batch-db [c-hash (codec/id-byte-string arg1)]
                             tid clauses))
  (-query-clauses [_]
    (decode-clauses clauses))
  (-query-plan [_ _]
    (index/compartment-query-plan clauses)))

(defrecord EmptyCompartmentQuery [c-hash tid]
  p/Query
  (-execute [_ batch-db arg1]
    (cr/resource-handles batch-db [c-hash (codec/id-byte-string arg1)] tid))
  (-query-clauses [_])
  (-query-plan [_ _]
    {:query-type :compartment}))

(defrecord Matcher [clauses]
  p/Matcher
  (-transducer [_ batch-db]
    (index/other-clauses-resource-handle-filter batch-db clauses))
  (-matcher-clauses [_]
    (decode-clauses clauses)))

(defn new-batch-db
  "Creates a new batch database.

  A batch database can be used instead of a normal database. It's functionally
  the same. Only the performance for multiple calls differs. It's not thread
  save and has to be closed after usage because it holds open iterators."
  ^AutoCloseable
  [{:keys [kv-store] :as node} basis-t t since-t]
  (let [snapshot (kv/new-snapshot kv-store)]
    (->BatchDb node kv-store snapshot basis-t t since-t)))
