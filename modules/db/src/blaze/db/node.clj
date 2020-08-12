(ns blaze.db.node
  "Local Database Node"
  (:require
    [blaze.anomaly :refer [when-ok ex-anom]]
    [blaze.async-comp :as ac]
    [blaze.db.api :as d]
    [blaze.db.impl.batch-db :as batch-db]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.db :as db]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.kv :as kv]
    [blaze.db.node.resource-indexer :as resource-indexer :refer [new-resource-indexer]]
    [blaze.db.node.transaction :as tx]
    [blaze.db.node.tx-indexer :as tx-indexer]
    [blaze.db.node.tx-indexer.verify :as tx-indexer-verify]
    [blaze.db.node.validation :as validation]
    [blaze.db.resource-store :as rs]
    [blaze.db.search-param-registry :as sr]
    [blaze.db.search-param-registry.spec]
    [blaze.db.tx-log :as tx-log]
    [blaze.executors :as ex]
    [blaze.module :refer [reg-collector]]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [prometheus.alpha :as prom :refer [defhistogram]]
    [taoensso.timbre :as log])
  (:import
    [java.io Closeable]
    [java.time Duration]
    [java.util.concurrent TimeUnit ExecutorService CompletableFuture]))


(set! *warn-on-reflection* true)


(defhistogram duration-seconds
  "Node durations."
  {:namespace "blaze"
   :subsystem "db_node"
   :name "duration_seconds"}
  (take 16 (iterate #(* 2 %) 0.0001))
  "op")


(defhistogram transaction-sizes
  "Number of transaction commands per transaction."
  {:namespace "blaze"
   :subsystem "db_node"
   :name "transaction_sizes"}
  (take 16 (iterate #(* 2 %) 1)))


(defn- resolve-search-param [search-param-registry type code]
  (if-let [search-param (sr/get search-param-registry code type)]
    search-param
    {::anom/category ::anom/not-found
     ::anom/message (format "search-param with code `%s` and type `%s` not found" code type)}))


(defn- resolve-search-params [search-param-registry type clauses]
  (reduce
    (fn [ret [code & values]]
      (let [[code modifier] (str/split code #":" 2)
            res (resolve-search-param search-param-registry type code)]
        (if (::anom/category res)
          (reduced res)
          (conj ret [res modifier (search-param/compile-values res values)]))))
    []
    clauses))


(defn- create-watcher! [state t]
  (let [future (ac/future)]
    (add-watch
      state future
      (fn [future state _ {:keys [e] new-t :t new-error-t :error-t}]
        (cond
          (<= t (max new-t new-error-t))
          (do (ac/complete! future nil)
              (remove-watch state future))

          e
          (do (ac/complete-exceptionally! future e)
              (remove-watch state future)))))
    future))


(defn- tx-success? [kv-store t]
  (kv/get kv-store :tx-success-index (codec/t-key t)))


(defn- get-tx-error [kv-store t]
  (some-> (kv/get kv-store :tx-error-index (codec/t-key t))
          (codec/decode-tx-error)))


(defn load-tx-result [node kv-store t]
  (if (tx-success? kv-store t)
    (ac/completed-future (db/db node t))
    (if-let [anomaly (get-tx-error kv-store t)]
      (ac/failed-future
        (ex-info (format "Transaction with point in time %d errored." t)
                 anomaly))
      (ac/failed-future
        (ex-anom
          {::anom/category ::anom/fault
           ::anom/message
           (format "Can't find transaction result with point in time of %d." t)})))))


(defn- index-tx [kv-store tx-data]
  (with-open [_ (prom/timer duration-seconds "index-transactions")]
    (tx-indexer/index-tx kv-store tx-data)))


(defn- store-tx-entries [kv-store entries]
  (with-open [_ (prom/timer duration-seconds "store-tx-entries")]
    (kv/put kv-store entries)))


(defn- advance-t! [state t]
  (log/trace "advance state to t =" t)
  (swap! state assoc :t t))


(defn- advance-error-t! [state t]
  (log/trace "advance state to error-t =" t)
  (swap! state assoc :error-t t))


(defn- index-tx-data [kv-store state resource-indexer {:keys [t instant] :as tx-data}]
  (log/trace "index transaction with t =" t "and" (count (:tx-cmds tx-data)) "command(s)")
  (prom/observe! transaction-sizes (count (:tx-cmds tx-data)))
  (let [timer (prom/timer duration-seconds "index-resources")
        future (resource-indexer/index-all-resources resource-indexer tx-data)
        result (index-tx kv-store tx-data)]
    (if (::anom/category result)
      (do (kv/put kv-store (codec/tx-error-entries t result))
          (advance-error-t! state t))
      (do (store-tx-entries kv-store result)
          (try
            (log/trace "wait until resources are indexed...")
            (ac/join future)
            (finally
              (log/trace "done indexing all resources")
              (prom/observe-duration! timer)))
          (kv/put kv-store (codec/tx-success-entries t instant))
          (advance-t! state t)))))


(defn- poll [kv-store state resource-indexer queue poll-timeout]
  (log/trace "poll transaction queue")
  (doseq [tx-data (tx-log/poll queue poll-timeout)]
    (index-tx-data kv-store state resource-indexer tx-data)))


(defn- max-t [state]
  (let [{:keys [t error-t]} @state]
    (max t error-t)))


(defn- enhance-meta [meta t {:blaze.db.tx/keys [instant]}]
  (-> (assoc meta :versionId (str t))
      (assoc :lastUpdated (str instant))))


(defn- mk-meta [meta state t tx]
  (-> meta
      (assoc :blaze.db/t t)
      (assoc :blaze.db/num-changes (codec/state->num-changes state))
      (assoc :blaze.db/op (codec/state->op state))
      (assoc :blaze.db/tx tx)))


(defn- tx [kv-store t]
  (some-> (kv/get kv-store :tx-success-index (codec/t-key t))
          (codec/decode-tx t)))


(defn- enhance-resource [kv-store handle resource]
  (let [t (rh/t handle)
        tx (tx kv-store t)]
    (-> (update resource :meta enhance-meta t tx)
        (with-meta (mk-meta (meta handle) (rh/state handle) t tx)))))


(defrecord Node [tx-log kv-store resource-store search-param-registry
                 resource-indexer state run? poll-timeout finished]
  p/Node
  (-db [this]
    (db/db this (:t @state)))

  (-sync [this t]
    (if (<= t (:t @state))
      (ac/completed-future (d/db this))
      (-> (create-watcher! state t)
          (ac/then-apply #(d/sync this t)))))

  (-submit-tx [_ tx-ops]
    (log/trace "submit" (count tx-ops) "tx-ops")
    (let [res (validation/validate-ops tx-ops)]
      (if (::anom/category res)
        (CompletableFuture/failedFuture (ex-info "" res))
        (let [[tx-cmds entries] (tx/prepare-ops tx-ops)]
          (-> (rs/put resource-store entries)
              (ac/then-compose (fn [_] (tx-log/submit tx-log tx-cmds))))))))

  (-tx-result [this t]
    (let [watcher (create-watcher! state t)
          current-t (max-t state)]
      (log/trace "call tx-result: t =" t "current-t =" current-t)
      (if (<= t current-t)
        (do (remove-watch state watcher)
            (load-tx-result this kv-store t))
        (ac/then-compose watcher (fn [_] (load-tx-result this kv-store t))))))

  p/Tx
  (-tx [_ t]
    (tx kv-store t))

  rs/ResourceLookup
  (-get [_ hash]
    (rs/-get resource-store hash))

  p/QueryCompiler
  (-compile-type-query [_ type clauses]
    (when-ok [clauses (resolve-search-params search-param-registry type clauses)]
      (batch-db/->TypeQuery (codec/tid type) (seq clauses))))

  (-compile-system-query [_ clauses]
    (when-ok [clauses (resolve-search-params search-param-registry "Resource"
                                             clauses)]
      (batch-db/->SystemQuery (seq clauses))))

  (-compile-compartment-query [_ code type clauses]
    (when-ok [clauses (resolve-search-params search-param-registry type clauses)]
      (batch-db/->CompartmentQuery (codec/c-hash code) (codec/tid type)
                                   (seq clauses))))

  p/Pull
  (-pull [_ resource-handle]
    (-> (rs/-get resource-store (rh/hash resource-handle))
        (ac/then-apply #(enhance-resource kv-store resource-handle %))))

  (-pull-content [_ resource-handle]
    (-> (rs/-get resource-store (rh/hash resource-handle))
        (ac/then-apply #(with-meta % (meta resource-handle)))))

  Runnable
  (run [_]
    (try
      (let [offset (inc (:t @state))]
        (log/trace "enter indexer and open transaction queue with offset =" offset)
        (with-open [queue (tx-log/new-queue tx-log offset)]
          (while @run?
            (try
              (poll kv-store state resource-indexer queue poll-timeout)
              (catch Exception e
                (swap! state assoc :e e)
                (throw e))))))
      (finally
        (ac/complete! finished true)
        (log/trace "exit indexer"))))

  Closeable
  (close [_]
    (log/trace "start closing")
    (vreset! run? false)
    @finished
    (log/trace "done closing")))


(defn- execute [executor node error]
  (-> (CompletableFuture/runAsync node executor)
      (ac/exceptionally
        (fn [e]
          (log/error "Error while indexing:" (ex-cause e))
          (swap! error (ex-cause e))))))


(defn new-node
  "Creates a new local database node."
  [tx-log resource-indexer-executor resource-indexer-batch-size
   indexer-executor kv-store resource-store search-param-registry
   poll-timeout]
  (let [resource-indexer (new-resource-indexer resource-store
                                               search-param-registry
                                               kv-store
                                               resource-indexer-executor
                                               resource-indexer-batch-size)
        indexer-abort-reason (atom nil)
        node (->Node tx-log kv-store resource-store search-param-registry
                     resource-indexer
                     (atom {:t (or (tx-indexer/last-t kv-store) 0)
                            :error-t 0})
                     (volatile! true)
                     poll-timeout
                     (ac/future))]
    (execute indexer-executor node indexer-abort-reason)
    node))


(s/def ::indexer-executor
  ex/executor?)


(defmethod ig/pre-init-spec :blaze.db/node [_]
  (s/keys
    :req-un
    [:blaze.db/tx-log
     ::resource-indexer-executor
     ::resource-indexer-batch-size
     ::indexer-executor
     :blaze.db/kv-store
     :blaze.db/resource-store
     :blaze.db/search-param-registry]))


(defn- init-msg [resource-indexer-batch-size]
  (format "Open local database node with a resource indexer batch size of %d"
          resource-indexer-batch-size))


(defmethod ig/init-key :blaze.db/node
  [_ {:keys [tx-log resource-indexer-executor resource-indexer-batch-size
             indexer-executor kv-store resource-store search-param-registry]}]
  (log/info (init-msg resource-indexer-batch-size))
  (new-node tx-log resource-indexer-executor resource-indexer-batch-size
            indexer-executor kv-store resource-store search-param-registry
            (Duration/ofSeconds 1)))


(defmethod ig/halt-key! :blaze.db/node
  [_ node]
  (log/info "Close local database node")
  (.close ^Closeable node))


(defn- executor-init-msg [num-threads]
  (format "Init resource indexer executor with %d threads" num-threads))


(defmethod ig/init-key ::resource-indexer-executor
  [_ {:keys [num-threads] :or {num-threads 4}}]
  (log/info (executor-init-msg num-threads))
  (ex/io-pool num-threads "resource-indexer-%d"))


(defmethod ig/halt-key! ::resource-indexer-executor
  [_ ^ExecutorService executor]
  (log/info "Stopping resource indexer executor...")
  (.shutdown executor)
  (if (.awaitTermination executor 10 TimeUnit/SECONDS)
    (log/info "Resource indexer executor was stopped successfully")
    (log/warn "Got timeout while stopping the resource indexer executor")))


(derive ::resource-indexer-executor :blaze.metrics/thread-pool-executor)


(defmethod ig/init-key ::indexer-executor
  [_ _]
  (log/info "Init indexer executor")
  (ex/single-thread-executor "indexer"))


(defmethod ig/halt-key! ::indexer-executor
  [_ ^ExecutorService executor]
  (log/info "Stopping indexer executor...")
  (.shutdown executor)
  (if (.awaitTermination executor 10 TimeUnit/SECONDS)
    (log/info "Indexer executor was stopped successfully")
    (log/warn "Got timeout while stopping the indexer executor")))


(reg-collector ::duration-seconds
  duration-seconds)


(reg-collector ::transaction-sizes
  transaction-sizes)


(reg-collector :blaze.db.node.resource-indexer/duration-seconds
  resource-indexer/duration-seconds)


(reg-collector ::tx-indexer/duration-seconds
  tx-indexer-verify/duration-seconds)
