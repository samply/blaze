(ns blaze.db.impl.db
  "Primary Database Implementation

  A database value reads from a key-value store snapshot that is held by the
  scope the database value was created in. All reads of the database value and
  of values derived from it with `since` use that snapshot. The snapshot pools
  its iterators per thread, so a database value can be used from several
  threads at once."
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.db.patient-everything :as pe]
   [blaze.db.impl.index :as index]
   [blaze.db.impl.index.patient-last-change :as plc]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.index.system-as-of :as sao]
   [blaze.db.impl.index.system-stats :as system-stats]
   [blaze.db.impl.index.t-by-instant :as t-by-instant]
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
   [java.io Writer]))

(set! *warn-on-reflection* true)

(defn- rev-include [db snapshot reference source-tid code]
  (coll/eduction
   (u/resource-handle-xf db source-tid)
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

(defrecord Db [node kv-store resource-handle-cache snapshot t since-t]
  p/Db
  (-node [_]
    node)

  (-t [_]
    t)

  (-since [_ since]
    (Db. node kv-store resource-handle-cache snapshot t
         ;; the snapshot can contain transactions newer than t
         (min t (or (t-by-instant/t-by-instant snapshot since) 0))))

  (-since-t [_]
    since-t)

  ;; ---- Instance-Level Functions --------------------------------------------

  (-resource-handle [db tid id]
    (rao/resource-handle db tid id))

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

  (-patient-compartment-last-change-t-xf [_ f]
    (plc/last-change-t-xf snapshot t f))

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

  (-compile-system-query-lenient [_ clauses]
    (p/-compile-system-query-lenient node clauses))

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

  (-pull-fn [_ opts]
    (p/-pull-fn node opts)))

(defmethod print-method Db [^Db db ^Writer w]
  (.write w (format "Db[t=%d]" (.t db))))

(defrecord Matcher [search-clauses]
  p/Matcher
  (-transducer [_ db]
    (index/other-clauses-resource-handle-filter db search-clauses))
  (-matcher-clauses [_]
    (qu/decode-clauses {:search-clauses search-clauses})))

(defn- db
  "Creates a database value on `node` that reads from `snapshot` at `t`,
  including only changes after `since-t` if it's positive.

  The database value is valid only as long as `snapshot` isn't closed."
  [{:keys [kv-store resource-handle-cache] :as node} snapshot t since-t]
  (->Db node kv-store resource-handle-cache snapshot t since-t))

(defn call-with-db
  "Like `with-db`, but `f` returns its result directly instead of a
  CompletableFuture, and the database value includes only changes after
  `since-t` if it's positive.

  The scope ends when `f` returns, so its result has to be realized. Returns
  the result of `f`."
  [{:keys [kv-store] :as node} t since-t f]
  (with-open [snapshot (kv/new-snapshot kv-store)]
    (f (db node snapshot t since-t))))

(defn with-db
  "Opens a scope with a database value on `node` at `t` and calls `f` with it.

  The scope holds a new snapshot of the key-value store of `node` from its start
  to its end. It ends when the CompletableFuture returned by `f` completes or
  when `f` returns something else or throws.

  Returns a CompletableFuture that completes with the result of the future
  returned by `f` after the scope has ended. Fails with the anomaly returned or
  thrown by `f` and with a fault if `f` returns neither a CompletableFuture nor
  an anomaly."
  [{:keys [kv-store] :as node} t f]
  (let [snapshot (kv/new-snapshot kv-store)
        result (ba/try-anomaly (f (db node snapshot t 0)))]
    (if (ac/completable-future? result)
      (ac/when-complete result (fn [_ _] (.close snapshot)))
      (do (.close snapshot)
          (ac/completed-future
           (if (ba/anomaly? result)
             result
             (ba/fault "The function passed to `with-db` has to return a CompletableFuture.")))))))
