(ns blaze.db.impl.batch-db
  "Batch Database Implementation

  A batch database keeps key-value store iterators open in order to avoid the
  cost associated with open and closing them."
  (:require
   [blaze.async.comp :as ac]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
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
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.kv :as kv]
   [blaze.db.search-param-registry :as sr]
   [blaze.fhir.spec.type :as type])
  (:import
   [java.io Writer]
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

(defn- non-compartment-types [search-param-registry]
  (apply disj (sr/all-types search-param-registry)
         "Bundle"
         "CapabilityStatement"
         "CompartmentDefinition"
         "ConceptMap"
         "GraphDefinition"
         "ImplementationGuide"
         "MessageDefinition"
         "MessageHeader"
         "OperationDefinition"
         "SearchParameter"
         "Subscription"
         "TerminologyCapabilities"
         "TestReport"
         "TestScript"
         (map first (sr/compartment-resources search-param-registry "Patient"))))

(defn- supporting-codes
  "Returns all codes of search params of resources with `type` that point to one
  of the `non-compartment-types`."
  [search-param-registry non-compartment-types type]
  (into
   []
   (comp
    (filter (comp #{"reference"} :type))
    (filter (comp (partial some non-compartment-types) :target))
    (map :code))
   (sr/list-by-type search-param-registry type)))

(defrecord BatchDb [node snapshot basis-t t]
  p/Db
  (-node [_]
    node)

  (-basis-t [_]
    basis-t)

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
    (index/targets db resource-handle (codec/c-hash code)))

  (-include [db resource-handle code target-type]
    (index/targets db resource-handle (codec/c-hash code)
                   (codec/tid target-type)))

  (-rev-include [db resource-handle source-type code]
    (let [reference (codec/v-hash (rh/reference resource-handle))
          source-tid (codec/tid source-type)]
      (coll/eduction
       (u/resource-handle-mapper db source-tid)
       (sp-vr/prefix-keys snapshot (codec/c-hash code) source-tid
                          (bs/size reference) reference))))

  (-patient-everything [db patient-handle]
    (let [search-param-registry (:search-param-registry node)
          non-compartment-types (non-compartment-types search-param-registry)
          supporting-codes (partial supporting-codes search-param-registry
                                    non-compartment-types)]
      (coll/eduction
       cat
       [[patient-handle]
        (coll/eduction
         (comp
          (mapcat
           (fn [[type codes]]
             (let [supporting-codes (supporting-codes type)]
               (coll/eduction
                (comp
                 (mapcat (partial p/-rev-include db patient-handle type))
                 (mapcat
                  (fn [resource-handle]
                    (into
                     [resource-handle]
                     (comp
                      (mapcat (partial p/-include db resource-handle))
                      (filter (comp non-compartment-types name type/type)))
                     supporting-codes))))
                codes))))
          (distinct))
         (sr/compartment-resources search-param-registry "Patient"))])))

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
