(ns blaze.db.impl.batch-db
  "Batch Database Implementation

  A batch database keeps key-value store iterators open in order to avoid the
  cost associated with open and closing them."
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.impl.batch-db.patient-everything :as pe]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index :as index]
   [blaze.db.impl.index.patient-last-change :as plc]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.index.system-as-of :as sao]
   [blaze.db.impl.index.system-stats :as system-stats]
   [blaze.db.impl.index.type-as-of :as tao]
   [blaze.db.impl.index.type-stats :as type-stats]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.query.util :as qu]
   [blaze.db.impl.search-param.chained :as spc]
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.kv :as kv]
   [blaze.db.node.resource-indexer :as resource-indexer]
   [blaze.db.search-param-registry :as sr])
  (:import
   [java.io Writer]
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

(defn- rev-include [batch-db snapshot reference source-tid code]
  (coll/eduction
   (u/resource-handle-xf batch-db source-tid)
   (sp-vr/index-handles-full-value snapshot code source-tid reference)))

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

  (-execute-query [db query arg1 arg2]
    (p/-execute query db arg1 arg2))

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
          type (name (:fhir/type resource-handle))
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

  (-compile-compartment-query [_ code type]
    (p/-compile-compartment-query node code type))

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

  (-pull-many [_ resource-handles opts]
    (p/-pull-many node resource-handles opts))

  AutoCloseable
  (close [_]
    (.close ^AutoCloseable snapshot)))

(defmethod print-method BatchDb [^BatchDb db ^Writer w]
  (.write w (format "BatchDb[t=%d]" (.t db))))

(defrecord Matcher [search-clauses]
  p/Matcher
  (-transducer [_ batch-db]
    (index/other-clauses-resource-handle-filter batch-db search-clauses))
  (-matcher-clauses [_]
    (qu/decode-clauses {:search-clauses search-clauses})))

(defn new-batch-db
  "Creates a new batch database.

  A batch database can be used instead of a normal database. It's functionally
  the same. Only the performance for multiple calls differs. It's not thread
  save and has to be closed after usage because it holds open iterators."
  ^AutoCloseable
  [{:keys [kv-store] :as node} basis-t t since-t]
  (let [snapshot (kv/new-snapshot kv-store)]
    (->BatchDb node kv-store snapshot basis-t t since-t)))
