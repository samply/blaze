(ns blaze.db.node
  "This namespace contains the local database node component."
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.async.flow :as flow]
   [blaze.db.api :as d]
   [blaze.db.impl.batch-db :as batch-db]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.db :as db]
   [blaze.db.impl.index :as index]
   [blaze.db.impl.index.patient-last-change :as plc]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.t-by-instant :as t-by-instant]
   [blaze.db.impl.index.tx-error :as tx-error]
   [blaze.db.impl.index.tx-success :as tx-success]
   [blaze.db.impl.protocols :as p]
   [blaze.db.kv :as kv]
   [blaze.db.node.protocols :as np]
   [blaze.db.node.resource-indexer :as resource-indexer]
   [blaze.db.node.resource-indexer.spec]
   [blaze.db.node.spec]
   [blaze.db.node.transaction :as tx]
   [blaze.db.node.tx-indexer :as tx-indexer]
   [blaze.db.node.tx-indexer.util :as tx-u]
   [blaze.db.node.tx-indexer.verify :as verify]
   [blaze.db.node.util :as node-util]
   [blaze.db.node.validation :as validation]
   [blaze.db.node.version :as version]
   [blaze.db.resource-store :as rs]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry.spec]
   [blaze.db.tx-log :as tx-log]
   [blaze.executors :as ex]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.references :as fsr]
   [blaze.fhir.spec.type :as type]
   [blaze.module :as m :refer [reg-collector]]
   [blaze.scheduler :as sched]
   [blaze.spec]
   [blaze.util :refer [conj-vec]]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [java-time.api :as time]
   [prometheus.alpha :as prom :refer [defhistogram]]
   [taoensso.timbre :as log])
  (:import
   [java.lang AutoCloseable]
   [java.util.concurrent CompletableFuture SubmissionPublisher TimeUnit]))

(set! *warn-on-reflection* true)

(defn node? [x]
  (satisfies? np/Node x))

(defn submit-tx [node tx-ops]
  (np/-submit-tx node tx-ops))

(defn tx-result
  "Waits for the transaction with `t` to happen on `node`.

  Returns a CompletableFuture that will complete with the database after the
  transaction in case of success or will complete exceptionally with an anomaly
  in case of a transaction error or other errors."
  [node t]
  (np/-tx-result node t))

(defhistogram duration-seconds
  "Node durations."
  {:namespace "blaze"
   :subsystem "db_node"
   :name "duration_seconds"}
  (take 16 (iterate #(* 2 %) 0.0001))
  "op")

(defn- db-future
  "Adds a watcher to `node` and returns a CompletableFuture that will complete
  with the database value of at least the point in time `t` if `t` is reached or
  complete exceptionally in case the indexing errored."
  [{:keys [state] :as node} t]
  (let [future (ac/future)]
    (add-watch
     state future
     (fn [future state _ {:keys [e] new-t :t new-error-t :error-t}]
       (log/trace "process watch for t =" new-t)
       (cond
         (<= t (max new-t new-error-t))
         (do (log/trace "complete database future with new db with t =" new-t)
             ;; it's important to complete async here, because otherwise all
             ;; the later work will happen on the indexer thread
             (ac/complete-async! future #(db/db node new-t))
             (remove-watch state future))

         e
         (do (ac/complete-exceptionally! future e)
             (remove-watch state future))

         (ac/canceled? future)
         (remove-watch state future))))
    future))

(defn- index-tx [db-before tx-data]
  (with-open [_ (prom/timer duration-seconds "index-transactions")]
    (tx-indexer/index-tx db-before tx-data)))

(defn- advance-t! [state t]
  (log/trace "advance state to t =" t)
  (swap! state assoc :t t))

(defn- advance-error-t! [state t]
  (log/trace "advance state to error-t =" t)
  (swap! state assoc :error-t t))

(defn- commit-error! [{:keys [kv-store state]} t anomaly]
  (log/trace "commit transaction error with t =" t)
  (kv/put! kv-store [(tx-error/index-entry t anomaly)])
  (advance-error-t! state t))

(defn- store-tx-entries! [kv-store entries]
  (log/trace "store" (count entries) "transaction index entries")
  (with-open [_ (prom/timer duration-seconds "store-tx-entries")]
    (kv/put! kv-store entries)))

(defn- wait-for-resources [future timer]
  (try
    (log/trace "wait until resources are indexed...")
    (ac/join future)
    (log/trace "done indexing all resources")
    (catch Exception e
      (log/error "Error while resource indexing: " (ex-message (ex-cause e)))
      (log/error e)
      (throw e))
    (finally
      (prom/observe-duration! timer))))

(defn- tx-success-entries [t instant]
  [(tx-success/index-entry t instant)
   (t-by-instant/index-entry instant t)])

(defn- commit-success! [{:keys [kv-store state]} t instant]
  (log/trace "commit transaction success with t =" t)
  (kv/put! kv-store (tx-success-entries t instant))
  (advance-t! state t))

(defn- index-tx-data!
  "This is the main transaction handling function.

  If indexes resources and transaction data and commits either success or error."
  [{:keys [resource-indexer kv-store] :as node}
   {:keys [t instant tx-cmds] :as tx-data}]
  (log/trace "index transaction with t =" t "and" (count tx-cmds) "command(s)")
  (let [timer (prom/timer duration-seconds "index-resources")
        future (resource-indexer/index-resources resource-indexer tx-data)
        result (index-tx (np/-db node) tx-data)]
    (if (ba/anomaly? result)
      (commit-error! node t result)
      (do
        (store-tx-entries! kv-store result)
        (wait-for-resources future timer)
        (commit-success! node t instant)))))

(defn- poll-tx-queue! [queue poll-timeout]
  (with-open [_ (prom/timer duration-seconds "poll-tx-queue")]
    (tx-log/poll! queue poll-timeout)))

(defn- poll-and-index!
  "Polls `queue` once and indexes the resulting transaction data.

  Waits up to `poll-timeout` for the transaction data to become available."
  [node queue poll-timeout]
  (log/trace "poll transaction queue")
  (run! (partial index-tx-data! node) (poll-tx-queue! queue poll-timeout)))

(defn- enhance-resource-meta [meta t {:blaze.db.tx/keys [instant]}]
  (-> (or meta #fhir/Meta{})
      (assoc :versionId (type/->Id (str t)))
      (assoc :lastUpdated instant)))

(defn- mk-meta [handle tx]
  {:blaze.resource/hash (rh/hash handle)
   :blaze.db/num-changes (rh/num-changes handle)
   :blaze.db/op (rh/op handle)
   :blaze.db/tx tx})

(defn- enhance-resource [tx-cache handle resource]
  (let [t (rh/t handle)
        tx (tx-success/tx tx-cache t)]
    (-> (update resource :meta enhance-resource-meta t tx)
        (with-meta (mk-meta handle tx)))))

(defn- hashes-of-non-deleted [resource-handles]
  (into [] (comp (remove rh/deleted?) (map rh/hash)) resource-handles))

(defn- deleted-resource [{:keys [id] :as resource-handle}]
  {:fhir/type (fhir-spec/fhir-type resource-handle) :id id})

(defn- resource-content-not-found-msg [resource-handle]
  (format "The resource content of `%s/%s` with hash `%s` was not found."
          (name (type/type resource-handle)) (:id resource-handle)
          (:hash resource-handle)))

(defn- resource-content-not-found-anom [resource-handle]
  (ba/not-found (resource-content-not-found-msg resource-handle)
                :blaze.db/resource-handle resource-handle))

(defn- to-resource [tx-cache resources resource-handle]
  (if-let [resource (if (rh/deleted? resource-handle)
                      (deleted-resource resource-handle)
                      (get resources (rh/hash resource-handle)))]
    (enhance-resource tx-cache resource-handle resource)
    (resource-content-not-found-anom resource-handle)))

(defn- get-resource [resource-store resource-handle variant]
  (if (rh/deleted? resource-handle)
    (ac/completed-future (deleted-resource resource-handle))
    (rs/get resource-store (rh/hash resource-handle) variant)))

(defn- patient-compartment-search-param-codes [search-param-registry type]
  (when (seq (sr/compartment-resources search-param-registry "Patient" type))
    (cond-> #{"subject" "patient"}
      (nil? (sr/get search-param-registry "subject" type)) (disj "subject")
      (nil? (sr/get search-param-registry "patient" type)) (disj "patient"))))

(defn- clause-with-code-fn? [codes]
  (fn [[search-param]]
    (codes (:code search-param))))

(defn- has-system? [value]
  (let [[system code] (str/split value #"\|")]
    (not (or (str/blank? system) (str/blank? code)))))

(defn- token-clause? [[search-param _ values]]
  (and (= "token" (:type search-param))
       (every? has-system? values)))

(defn- compartment-clause-patient-ids [[{:keys [code]} _ values]]
  (transduce
   (comp
    (map
     (if (= "patient" code)
       (fn [value]
         (if-let [[type id] (fsr/split-literal-ref value)]
           (when (= "Patient" type)
             id)
           (when (re-matches #"[A-Za-z0-9\-\.]{1,64}" value)
             value)))
       (fn [value]
         (when-let [[type id] (fsr/split-literal-ref value)]
           (when (= "Patient" type)
             id)))))
    (halt-when nil?))
   conj
   []
   values))

(defn- compile-patient-type-query [search-param-registry type clauses]
  (let [codes (patient-compartment-search-param-codes search-param-registry type)]
    (when (seq codes)
      (let [[compartment-clause & more] (filter (clause-with-code-fn? codes) clauses)
            [token-clause] (filter token-clause? clauses)
            patient-ids (compartment-clause-patient-ids compartment-clause)]
        (when (and compartment-clause (empty? more) token-clause patient-ids)
          (batch-db/->PatientTypeQuery
           (codec/tid type)
           patient-ids
           compartment-clause
           (into [token-clause] (remove (clause-with-code-fn? (conj codes (:code (first token-clause))))) clauses)))))))

(defn- compile-type-query [search-param-registry type clauses lenient?]
  (when-ok [clauses (index/resolve-search-params search-param-registry type clauses
                                                 lenient?)]
    (if (empty? clauses)
      (batch-db/->EmptyTypeQuery (codec/tid type))
      (or (compile-patient-type-query search-param-registry type clauses)
          (batch-db/->TypeQuery (codec/tid type) clauses)))))

(defn- compile-compartment-query
  [search-param-registry code type clauses lenient?]
  (when-ok [clauses (index/resolve-search-params search-param-registry type clauses
                                                 lenient?)]
    (if (empty? clauses)
      (batch-db/->EmptyCompartmentQuery (codec/c-hash code) (codec/tid type))
      (batch-db/->CompartmentQuery (codec/c-hash code) (codec/tid type)
                                   clauses))))

(def ^:private add-subsetted-xf
  (map #(update % :meta update :tag conj-vec fhir-spec/subsetted)))

(defn- subset-xf [elements]
  (let [keys (conj (seq elements) :fhir/type :id :meta)]
    (comp (map #(select-keys % keys))
          add-subsetted-xf)))

(defn- changed-handles [node type t]
  (with-open [db (batch-db/new-batch-db node t t)]
    (into [] (take-while #(= t (rh/t %))) (d/type-history db type))))

(defrecord Node [context tx-log tx-cache kv-store resource-store sync-fn
                 search-param-registry resource-indexer state run? poll-timeout
                 finished]
  np/Node
  (-db [node]
    (db/db node (:t @state)))

  (-sync [node]
    (log/trace "sync on last t")
    (sync-fn node))

  (-sync [node t]
    (log/trace "sync on t =" t)
    (let [{current-t :t current-error-t :error-t} @state]
      (if (<= t (max current-t current-error-t))
        (ac/completed-future (db/db node current-t))
        (db-future node t))))

  (-submit-tx [_ tx-ops]
    (log/trace "submit" (count tx-ops) "tx-ops")
    (if-ok [_ (validation/validate-ops tx-ops)]
      (let [[tx-cmds entries] (tx/prepare-ops context tx-ops)]
        (-> (rs/put! resource-store entries)
            (ac/then-compose-async
             (fn [_] (tx-log/submit tx-log tx-cmds entries)))))
      ac/completed-future))

  (-tx-result [node t]
    (let [future (db-future node t)
          {current-t :t current-error-t :error-t :as current-state} @state
          current-t (max current-t current-error-t)]
      (log/trace "call tx-result: t =" t "current-t =" current-t)
      (cond
        (<= t current-t)
        (do (remove-watch state future)
            (ac/completed-future (tx/load-tx-result node t)))

        (:e current-state)
        (do (remove-watch state future)
            (ac/failed-future (:e current-state)))

        :else
        (ac/then-apply future (fn [_] (tx/load-tx-result node t))))))

  (-changed-resources-publisher [node type]
    (let [publisher (SubmissionPublisher.)]
      (add-watch
       state publisher
       (fn [publisher _state _ {:keys [t error-t]}]
         (when (< error-t t)
           (let [changed-handles (changed-handles node type t)]
             (log/trace "Publish" (count changed-handles) "changed" type "resource handles")
             (flow/submit! publisher changed-handles)))))
      publisher))

  p/Tx
  (-tx [_ t]
    (tx-success/tx tx-cache t))

  p/QueryCompiler
  (-compile-type-query [_ type clauses]
    (compile-type-query search-param-registry type clauses false))

  (-compile-type-query-lenient [_ type clauses]
    (compile-type-query search-param-registry type clauses true))

  (-compile-type-matcher [_ type clauses]
    (when-ok [clauses (index/resolve-search-params search-param-registry type
                                                   clauses false)]
      (batch-db/->TypeMatcher clauses)))

  (-compile-system-query [_ clauses]
    (when-ok [clauses (index/resolve-search-params search-param-registry
                                                   "Resource" clauses false)]
      (batch-db/->SystemQuery clauses)))

  (-compile-compartment-query [_ code type clauses]
    (compile-compartment-query search-param-registry code type clauses false))

  (-compile-compartment-query-lenient [_ code type clauses]
    (compile-compartment-query search-param-registry code type clauses true))

  p/Pull
  (-pull [_ resource-handle variant]
    (do-sync [resource (get-resource resource-store resource-handle variant)]
      (or (some->> resource (enhance-resource tx-cache resource-handle))
          (resource-content-not-found-anom resource-handle))))

  (-pull-content [_ resource-handle variant]
    (do-sync [resource (get-resource resource-store resource-handle variant)]
      (or (some-> resource (with-meta (meta resource-handle)))
          (resource-content-not-found-anom resource-handle))))

  (-pull-many [_ resource-handles variant]
    (let [resource-handles (vec resource-handles)           ; don't evaluate resource-handles twice
          hashes (hashes-of-non-deleted resource-handles)
          [variant elements] (if (keyword? variant) [variant] [:complete variant])]
      (do-sync [resources (rs/multi-get resource-store hashes variant)]
        (into
         []
         (cond-> (comp (map (partial to-resource tx-cache resources))
                       (halt-when ba/anomaly?))
           elements (comp (subset-xf elements)))
         resource-handles))))

  Runnable
  (run [node]
    (try
      (let [offset (inc (:t @state))]
        (log/trace "enter indexer and open transaction queue with offset =" offset)
        (with-open [queue (tx-log/new-queue tx-log offset)]
          (while @run?
            (try
              (poll-and-index! node queue poll-timeout)
              (catch Exception e
                (swap! state assoc :e e)
                (throw e))))))
      (finally
        (ac/complete! finished true)
        (log/trace "exit indexer"))))

  AutoCloseable
  (close [_]
    (log/trace "start closing")
    (vreset! run? false)
    @finished
    (log/trace "done closing")))

(defn- execute [node executor]
  (-> (CompletableFuture/runAsync node executor)
      (ac/exceptionally
       (fn [{::anom/keys [message]}]
         (log/error "Error while indexing:" message)))))

(defn- initial-state [kv-store]
  {:t (or (tx-success/last-t kv-store) 0)
   :error-t 0})

(defn- init-msg
  [key {:keys [enforce-referential-integrity]
        :or {enforce-referential-integrity true}}]
  (log/info "Open" (node-util/component-name key "local database node") "with"
            (if enforce-referential-integrity "enabled" "disabled")
            "referential integrity checks"))

(defn- ctx
  [{:keys [enforce-referential-integrity allow-multiple-delete]
    :or {enforce-referential-integrity true
         allow-multiple-delete false}}]
  {:blaze.db/enforce-referential-integrity enforce-referential-integrity
   :blaze.db/allow-multiple-delete allow-multiple-delete})

(def ^:private expected-kv-store-version 0)

(defn- kv-store-version [kv-store]
  (or (some-> (kv/get kv-store :default version/key) version/decode-value) 0))

(def ^:private incompatible-kv-store-version-msg
  "Incompatible index store version %1$d found. This version of Blaze needs
  version %2$d.

  Either use an older version of Blaze which is compatible with index store
  version %1$d or do a database migration described here:

    https://github.com/samply/blaze/tree/master/docs/database/migration.md

  ")

(defn- incompatible-kv-store-version-ex [actual-version expected-version]
  (ex-info (format incompatible-kv-store-version-msg actual-version expected-version)
           {:actual-version actual-version :expected-version expected-version}))

(defn- check-version! [kv-store]
  (when (tx-success/last-t kv-store)
    (let [actual-kv-store-version (kv-store-version kv-store)]
      (if (= actual-kv-store-version expected-kv-store-version)
        (log/info "Index store version is" actual-kv-store-version)
        (throw (incompatible-kv-store-version-ex actual-kv-store-version
                                                 expected-kv-store-version))))))

(defn- sync-fn [storage]
  (condp identical? storage
    :distributed
    (fn sync-distributed [^Node node]
      (-> (tx-log/last-t (.-tx_log node))
          (ac/then-compose #(np/-sync node %))))
    (fn sync-standalone [^Node node]
      (ac/completed-future (db/db node (:t @(.-state node)))))))

(defn- initial-plc-index-entries [{:keys [state] :as node}]
  (into
   [(plc/state-index-entry {:type :current})]
   (map (fn [{:keys [id]}] (plc/index-entry (codec/id-byte-string id) (:t @state))))
   (d/type-list (d/db node) "Patient")))

(defn build-patient-last-change-index
  [key {:keys [kv-store] :as node}]
  (let [{:keys [type]} (plc/state kv-store)]
    (when (identical? :building type)
      (log/info "Building PatientLastChange index of" (node-util/component-name key "node"))
      (store-tx-entries! kv-store (initial-plc-index-entries node))
      (log/info (format "Finished building PatientLastChange index of %s." (node-util/component-name key "node"))))))

(defmethod m/pre-init-spec :blaze.db/node [_]
  (s/keys
   :req-un
   [:blaze.db/tx-log
    :blaze.db/tx-cache
    ::indexer-executor
    :blaze.db/kv-store
    ::resource-indexer
    :blaze.db/resource-store
    :blaze.db/search-param-registry
    :blaze/scheduler]
   :opt-un
   [:blaze.db/enforce-referential-integrity
    :blaze.db/allow-multiple-delete]))

(defmethod ig/init-key :blaze.db/node
  [key {:keys [storage tx-log tx-cache indexer-executor kv-store resource-indexer
               resource-store search-param-registry scheduler poll-timeout]
        :or {poll-timeout (time/seconds 1)}
        :as config}]
  (init-msg key config)
  (check-version! kv-store)
  (let [node (->Node (ctx config) tx-log tx-cache kv-store resource-store
                     (sync-fn storage) search-param-registry resource-indexer
                     (atom (initial-state kv-store))
                     (volatile! true)
                     poll-timeout
                     (ac/future))]
    (when (= :building (:type (plc/state kv-store)))
      (sched/submit scheduler #(build-patient-last-change-index key node)))
    (execute node indexer-executor)
    node))

(defmethod ig/halt-key! :blaze.db/node
  [_ node]
  (log/info "Close" (node-util/component-name key "local database node"))
  (.close ^AutoCloseable node))

(defmethod ig/init-key ::indexer-executor
  [key _]
  (log/info "Init" (node-util/component-name key "indexer executor"))
  (ex/single-thread-executor (node-util/thread-name-template key "indexer")))

(defmethod ig/halt-key! ::indexer-executor
  [_ executor]
  (log/info "Stopping" (node-util/component-name key "indexer executor..."))
  (ex/shutdown! executor)
  (if (ex/await-termination executor 10 TimeUnit/SECONDS)
    (log/info "Indexer executor was stopped successfully")
    (log/warn "Got timeout while stopping the" (node-util/component-name key "indexer executor"))))

(reg-collector ::duration-seconds
  duration-seconds)

(reg-collector ::transaction-sizes
  verify/transaction-sizes)

(reg-collector ::tx-indexer/duration-seconds
  tx-u/duration-seconds)
