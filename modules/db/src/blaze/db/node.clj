(ns blaze.db.node
  "Local Database Node"
  (:require
    [blaze.anomaly :as ba :refer [if-ok when-ok]]
    [blaze.async.comp :as ac :refer [do-sync]]
    [blaze.db.impl.batch-db :as batch-db]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.db :as db]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.impl.index.t-by-instant :as t-by-instant]
    [blaze.db.impl.index.tx-error :as tx-error]
    [blaze.db.impl.index.tx-success :as tx-success]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param.all :as search-param-all]
    [blaze.db.impl.search-param.chained :as spc]
    [blaze.db.kv :as kv]
    [blaze.db.node.protocols :as np]
    [blaze.db.node.resource-indexer :as resource-indexer]
    [blaze.db.node.resource-indexer.spec]
    [blaze.db.node.spec]
    [blaze.db.node.transaction :as tx]
    [blaze.db.node.tx-indexer :as tx-indexer]
    [blaze.db.node.tx-indexer.verify :as tx-indexer-verify]
    [blaze.db.node.validation :as validation]
    [blaze.db.node.version :as version]
    [blaze.db.resource-store :as rs]
    [blaze.db.search-param-registry.spec]
    [blaze.db.tx-log :as tx-log]
    [blaze.executors :as ex]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]
    [blaze.module :refer [reg-collector]]
    [blaze.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [java-time.api :as time]
    [prometheus.alpha :as prom :refer [defhistogram]]
    [taoensso.timbre :as log])
  (:import
    [java.lang AutoCloseable]
    [java.util.concurrent CompletableFuture TimeUnit]))


(set! *warn-on-reflection* true)


(defn submit-tx [node tx-ops]
  (np/-submit-tx node tx-ops))


(defn tx-result
  "Waits for the transaction with `t` to happen on `node`.

  Returns a CompletableFuture that completes with the database after the
  transaction in case of success or completes exceptionally with an anomaly in
  case of a transaction error or other errors."
  [node t]
  (np/-tx-result node t))


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


(defmulti resolve-search-param (fn [_registry _type _ret [type] _lenient?] type))


(defmethod resolve-search-param :search-clause
  [registry type ret [_ [param & values]] lenient?]
  (let [values (distinct values)]
    (if-ok [[search-param modifier] (spc/parse-search-param registry type param)]
      (if-ok [compiled-values (search-param/compile-values search-param modifier values)]
        (conj ret [search-param modifier values compiled-values])
        reduced)
      #(if lenient? ret (reduced %)))))


(defmethod resolve-search-param :sort-clause
  [registry type ret [_ [_ param direction]] _lenient?]
  (cond
    (seq ret)
    (reduced (ba/incorrect "Sort clauses are only allowed at first position."))

    (not= "_lastUpdated" param)
    (reduced (ba/incorrect (format "Unknown search-param `%s` in sort clause." param)))

    :else
    (let [[search-param] (spc/parse-search-param registry type param)]
      (conj ret [search-param (name direction) [] []]))))


(defn- conform-clause [clause]
  (s/conform :blaze.db.query/clause clause))


(defn- resolve-search-params* [registry type clauses lenient?]
  (reduce
    #(resolve-search-param registry type %1 (conform-clause %2) lenient?)
    []
    clauses))


(defn- type-priority [type]
  (case type
    "token" 0
    1))


(defn- order-clauses
  "Orders clauses by specificity so that the clause constraining the resources
  the most will come first."
  [clauses]
  (sort-by (comp type-priority :type first) clauses))


(defn- fix-last-updated [[[first-search-param first-modifier] :as clauses]]
  (if (and (= "_lastUpdated" (:code first-search-param))
           (not (#{"asc" "desc"} first-modifier)))
    (into [[search-param-all/search-param nil [""] [""]]] clauses)
    clauses))


(defn- resolve-search-params [registry type clauses lenient?]
  (when-ok [clauses (resolve-search-params* registry type clauses lenient?)]
    (-> clauses order-clauses fix-last-updated)))


(defn- db-future
  "Adds a watcher to `node` and returns a CompletableFuture that will complete
  with the database value of at least the point in time `t` if `t` is reached or
  complete exceptionally in case the indexing errored."
  [{:keys [state] :as node} t]
  (let [future (ac/future)]
    (add-watch
      state future
      (fn [future state _ {:keys [e] new-t :t new-error-t :error-t}]
        (cond
          (<= t (max new-t new-error-t))
          (do (ac/complete! future (db/db node new-t))
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
  (kv/put! kv-store [(tx-error/index-entry t anomaly)])
  (advance-error-t! state t))


(defn- store-tx-entries! [kv-store entries]
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
  (kv/put! kv-store (tx-success-entries t instant))
  (advance-t! state t))


(defn- index-tx-data!
  "This is the main transaction handling function.

  If indexes resources and transaction data and commits either success or error."
  [{:keys [resource-indexer kv-store] :as node}
   {:keys [t instant tx-cmds] :as tx-data}]
  (log/trace "index transaction with t =" t "and" (count tx-cmds) "command(s)")
  (prom/observe! transaction-sizes (count tx-cmds))
  (let [timer (prom/timer duration-seconds "index-resources")
        result (index-tx (np/-db node) tx-data)]
    (if (ba/anomaly? result)
      (commit-error! node t result)
      (let [[entries tx-cmds] result]
        (store-tx-entries! kv-store entries)
        (let [future (resource-indexer/index-resources resource-indexer (assoc tx-data :tx-cmds tx-cmds))]
          (wait-for-resources future timer))
        (commit-success! node t instant)))))


(defn- poll-tx-queue! [queue poll-timeout]
  (with-open [_ (prom/timer duration-seconds "poll-tx-queue")]
    (tx-log/poll! queue poll-timeout)))


(defn- poll-and-index! [node queue poll-timeout]
  (log/trace "poll transaction queue")
  (run! (partial index-tx-data! node) (poll-tx-queue! queue poll-timeout)))


(defn- enhance-resource-meta [meta t {:blaze.db.tx/keys [instant]}]
  (-> (or meta #fhir/Meta{})
      (assoc :versionId (type/->Id (str t)))
      (assoc :lastUpdated instant)))


(defn- mk-meta [{:keys [t num-changes op] :as handle} tx]
  (-> (meta handle)
      (assoc :blaze.db/t t)
      (assoc :blaze.db/num-changes num-changes)
      (assoc :blaze.db/op op)
      (assoc :blaze.db/tx tx)))


(defn- enhance-resource [tx-cache {:keys [t] :as handle} resource]
  (let [tx (tx-success/tx tx-cache t)]
    (-> (update resource :meta enhance-resource-meta t tx)
        (with-meta (mk-meta handle tx)))))


(defn- hashes-of-non-deleted [resource-handles]
  (into [] (comp (remove rh/deleted?) (map rh/hash)) resource-handles))


(defn- deleted-resource [{:keys [id] :as resource-handle}]
  {:fhir/type (fhir-spec/fhir-type resource-handle) :id id})


(defn- to-resource [tx-cache resources resource-handle]
  (let [resource (if (rh/deleted? resource-handle)
                   (deleted-resource resource-handle)
                   (get resources (rh/hash resource-handle)))]
    (enhance-resource tx-cache resource-handle resource)))


(defn- get-resource [resource-store resource-handle]
  (if (rh/deleted? resource-handle)
    (ac/completed-future (deleted-resource resource-handle))
    (rs/get resource-store (rh/hash resource-handle))))


(defn- compile-type-query [search-param-registry type clauses lenient?]
  (when-ok [clauses (resolve-search-params search-param-registry type clauses
                                           lenient?)]
    (if (empty? clauses)
      (batch-db/->EmptyTypeQuery (codec/tid type))
      (batch-db/->TypeQuery (codec/tid type) clauses))))


(defn- compile-compartment-query
  [search-param-registry code type clauses lenient?]
  (when-ok [clauses (resolve-search-params search-param-registry type clauses
                                           lenient?)]
    (if (empty? clauses)
      (batch-db/->EmptyCompartmentQuery (codec/c-hash code) (codec/tid code)
                                        (codec/tid type))
      (batch-db/->CompartmentQuery (codec/c-hash code) (codec/tid code)
                                   (codec/tid type) clauses))))


(defrecord Node [context tx-log rh-cache tx-cache kv-store resource-store
                 search-param-registry resource-indexer state run? poll-timeout
                 finished]
  np/Node
  (-db [node]
    (db/db node (:t @state)))

  (-sync [node]
    (log/trace "sync on last t")
    (-> (tx-log/last-t tx-log)
        (ac/then-compose #(np/-sync node %))))

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
            (ac/then-compose (fn [_] (tx-log/submit tx-log tx-cmds entries)))))
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

  p/Tx
  (-tx [_ t]
    (tx-success/tx tx-cache t))

  p/QueryCompiler
  (-compile-type-query [_ type clauses]
    (compile-type-query search-param-registry type clauses false))

  (-compile-type-query-lenient [_ type clauses]
    (compile-type-query search-param-registry type clauses true))

  (-compile-system-query [_ clauses]
    (when-ok [clauses (resolve-search-params search-param-registry "Resource"
                                             clauses false)]
      (batch-db/->SystemQuery clauses)))

  (-compile-compartment-query [_ code type clauses]
    (compile-compartment-query search-param-registry code type clauses false))

  (-compile-compartment-query-lenient [_ code type clauses]
    (compile-compartment-query search-param-registry code type clauses true))

  p/Pull
  (-pull [_ resource-handle]
    (do-sync [resource (get-resource resource-store resource-handle)]
      (enhance-resource tx-cache resource-handle resource)))

  (-pull-content [_ resource-handle]
    (do-sync [resource (get-resource resource-store resource-handle)]
      (with-meta resource (meta resource-handle))))

  (-pull-many [_ resource-handles]
    (let [resource-handles (vec resource-handles)           ; don't evaluate resource-handles twice
          hashes (hashes-of-non-deleted resource-handles)]
      (do-sync [resources (rs/multi-get resource-store hashes)]
        (mapv (partial to-resource tx-cache resources) resource-handles))))

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
  [{:keys [enforce-referential-integrity]
    :or {enforce-referential-integrity true}}]
  (log/info "Open local database node with"
            (if enforce-referential-integrity "enabled" "disabled")
            "referential integrity checks"))


(defn- ctx
  [{:keys [enforce-referential-integrity]
    :or {enforce-referential-integrity true}}]
  {:blaze.db/enforce-referential-integrity enforce-referential-integrity})


(defmethod ig/pre-init-spec :blaze.db/node [_]
  (s/keys
    :req-un
    [:blaze.db/tx-log
     :blaze.db/resource-handle-cache
     :blaze.db/tx-cache
     ::indexer-executor
     :blaze.db/kv-store
     ::resource-indexer
     :blaze.db/resource-store
     :blaze.db/search-param-registry]
    :opt-un
    [:blaze.db/enforce-referential-integrity]))


(def ^:private expected-kv-store-version 2)


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


(defn- check-and-set-version! [kv-store]
  (if (tx-success/last-t kv-store)
    (let [actual-kv-store-version (version/get kv-store)]
      (if (= actual-kv-store-version expected-kv-store-version)
        (log/info "Index store version is" actual-kv-store-version)
        (throw (incompatible-kv-store-version-ex actual-kv-store-version
                                                 expected-kv-store-version))))
    (version/set! kv-store expected-kv-store-version)))


(defmethod ig/init-key :blaze.db/node
  [_ {:keys [tx-log resource-handle-cache tx-cache indexer-executor kv-store
             resource-indexer resource-store search-param-registry poll-timeout]
      :or {poll-timeout (time/seconds 1)}
      :as config}]
  (init-msg config)
  (check-and-set-version! kv-store)
  (let [node (->Node (ctx config) tx-log resource-handle-cache tx-cache kv-store
                     resource-store search-param-registry resource-indexer
                     (atom (initial-state kv-store))
                     (volatile! true)
                     poll-timeout
                     (ac/future))]
    (execute node indexer-executor)
    node))


(defmethod ig/halt-key! :blaze.db/node
  [_ node]
  (log/info "Close local database node")
  (.close ^AutoCloseable node))


(defmethod ig/init-key ::indexer-executor
  [_ _]
  (log/info "Init indexer executor")
  (ex/single-thread-executor "indexer"))


(defmethod ig/halt-key! ::indexer-executor
  [_ executor]
  (log/info "Stopping indexer executor...")
  (ex/shutdown! executor)
  (if (ex/await-termination executor 10 TimeUnit/SECONDS)
    (log/info "Indexer executor was stopped successfully")
    (log/warn "Got timeout while stopping the indexer executor")))


(reg-collector ::duration-seconds
  duration-seconds)


(reg-collector ::transaction-sizes
  transaction-sizes)


(reg-collector ::tx-indexer/duration-seconds
  tx-indexer-verify/duration-seconds)
