(ns blaze.db.impl.batch-db
  "Batch Database Implementation

  A batch database keeps key-value store iterators open in order to avoid the
  cost associated with open and closing them."
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
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.index.system-as-of :as sao]
   [blaze.db.impl.index.system-stats :as system-stats]
   [blaze.db.impl.index.t-by-instant :as t-by-instant]
   [blaze.db.impl.index.type-as-of :as tao]
   [blaze.db.impl.index.type-stats :as type-stats]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.all :as search-param-all]
   [blaze.db.impl.search-param.chained :as spc]
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.kv :as kv]
   [blaze.db.node.resource-indexer :as resource-indexer]
   [blaze.db.search-param-registry :as sr]
   [blaze.fhir.spec.type :as type])
  (:import
   [java.io Writer]
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

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

(defrecord BatchDb [node snapshot basis-t t]
  p/Db
  (-node [_]
    node)

  (-basis-t [_]
    basis-t)

  (-as-of-t [_]
    (when (not= basis-t t) t))

  ;; ---- Instance-Level Functions --------------------------------------------

  (-resource-handle [_ tid id]
    (rao/resource-handle snapshot tid id t))

  ;; ---- Type-Level Functions ------------------------------------------------

  (-type-list [db tid]
    (rao/type-list db tid))

  (-type-list [db tid start-id]
    (rao/type-list db tid start-id))

  (-type-total [_ tid]
    (:total (type-stats/seek-value snapshot tid t) 0))

  ;; ---- System-Level Functions ----------------------------------------------

  (-system-list [db]
    (rao/system-list db))

  (-system-list [db start-tid start-id]
    (rao/system-list db start-tid start-id))

  (-system-total [_]
    (:total (system-stats/seek-value snapshot t) 0))

  ;; ---- Compartment-Level Functions -----------------------------------------

  (-compartment-resource-handles [db compartment tid]
    (cr/resource-handles db compartment tid))

  (-compartment-resource-handles [db compartment tid start-id]
    (cr/resource-handles db compartment tid start-id))

  ;; ---- Common Query Functions ----------------------------------------------

  (-count-query [db query]
    (p/-count query db))

  (-execute-query [db query]
    (p/-execute query db))

  (-execute-query [db query arg1]
    (p/-execute query db arg1))

  ;; ---- History Functions ---------------------------------------------------

  (-stop-history-at [_ instant]
    (let [t (t-by-instant/t-by-instant snapshot instant)]
      (take-while
       (fn [resource-handle]
         (< t (rh/t resource-handle))))))

  ;; ---- Instance-Level History Functions ------------------------------------

  (-instance-history [_ tid id start-t]
    (let [start-t (if (some-> start-t (<= t)) start-t t)]
      (rao/instance-history snapshot tid id start-t)))

  (-total-num-of-instance-changes [_ tid id since]
    (let [end-t (or (some->> since (t-by-instant/t-by-instant snapshot)) 0)]
      (rao/num-of-instance-changes snapshot tid id t end-t)))

  ;; ---- Type-Level History Functions ----------------------------------------

  (-type-history [_ tid start-t start-id]
    (let [start-t (if (some-> start-t (<= t)) start-t t)]
      (tao/type-history snapshot tid start-t start-id)))

  (-total-num-of-type-changes [_ type since]
    (let [tid (codec/tid type)
          end-t (some->> since (t-by-instant/t-by-instant snapshot))]
      (- (:num-changes (type-stats/seek-value snapshot tid t) 0)
         (:num-changes (some->> end-t (type-stats/seek-value snapshot tid)) 0))))

  ;; ---- System-Level History Functions --------------------------------------

  (-system-history [_ start-t start-tid start-id]
    (let [start-t (if (some-> start-t (<= t)) start-t t)]
      (sao/system-history snapshot start-t start-tid start-id)))

  (-total-num-of-system-changes [_ since]
    (let [end-t (some->> since (t-by-instant/t-by-instant snapshot))]
      (- (:num-changes (system-stats/seek-value snapshot t) 0)
         (:num-changes (some->> end-t (system-stats/seek-value snapshot)) 0))))

  ;; ---- Include ---------------------------------------------------------------

  (-include [db resource-handle code]
    (spc/targets db resource-handle (codec/c-hash code)))

  (-include [db resource-handle code target-type]
    (spc/targets db resource-handle (codec/c-hash code)
                 (codec/tid target-type)))

  (-rev-include [db resource-handle]
    (let [search-param-registry (:search-param-registry node)
          type (name (type/type resource-handle))]
      (coll/eduction
       (comp
        (mapcat
         (fn [{:keys [base code]}]
           (coll/eduction
            (mapcat #(p/-rev-include db resource-handle % code))
            base)))
        (distinct))
       (sr/list-by-target-type search-param-registry type))))

  (-rev-include [db resource-handle source-type code]
    (let [reference (codec/v-hash (rh/reference resource-handle))
          source-tid (codec/tid source-type)]
      (coll/eduction
       (u/resource-handle-mapper db source-tid)
       (sp-vr/prefix-keys snapshot (codec/c-hash code) source-tid
                          (bs/size reference) reference))))

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

  (-pull-many [_ resource-handles elements]
    (p/-pull-many node resource-handles elements))

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
      (cond
        (= search-param-all/search-param search-param)
        nil
        (#{"asc" "desc"} modifier)
        [:sort (:code search-param) (keyword modifier)]
        :else
        (into [(cond-> (:code search-param) modifier (str ":" modifier))] values))))
   clauses))

(defrecord TypeQuery [tid clauses]
  p/Query
  (-count [_ context]
    (index/type-query-total context tid clauses))
  (-execute [_ context]
    (index/type-query context tid clauses))
  (-execute [_ context start-id]
    (index/type-query context tid clauses (codec/id-byte-string start-id)))
  (-clauses [_]
    (decode-clauses clauses)))

(defrecord EmptyTypeQuery [tid]
  p/Query
  (-count [_ context]
    (ac/completed-future
     (:total (type-stats/seek-value (:snapshot context) tid (:t context)) 0)))
  (-execute [_ context]
    (rao/type-list context tid))
  (-execute [_ context start-id]
    (rao/type-list context tid (codec/id-byte-string start-id)))
  (-clauses [_]))

(defrecord SystemQuery [clauses]
  p/Query
  (-execute [_ context]
    (index/system-query context clauses)))

(defrecord CompartmentQuery [c-hash tid clauses]
  p/Query
  (-execute [_ context arg1]
    (index/compartment-query context [c-hash (codec/id-byte-string arg1)]
                             tid clauses))
  (-clauses [_]
    (decode-clauses clauses)))

(defrecord EmptyCompartmentQuery [c-hash tid]
  p/Query
  (-execute [_ context arg1]
    (cr/resource-handles context [c-hash (codec/id-byte-string arg1)] tid))
  (-clauses [_]))

(defn new-batch-db
  "Creates a new batch database.

  A batch database can be used instead of a normal database. It's functionally
  the same. Only the performance for multiple calls differs. It's not thread
  save and has to be closed after usage because it holds open iterators."
  ^AutoCloseable
  [{:keys [kv-store] :as node} basis-t t]
  (let [snapshot (kv/new-snapshot kv-store)]
    (->BatchDb node snapshot basis-t t)))
