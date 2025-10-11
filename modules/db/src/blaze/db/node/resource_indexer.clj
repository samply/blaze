(ns blaze.db.node.resource-indexer
  "This namespace contains the resource indexer component."
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.compartment.resource :as cr]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.kv :as kv]
   [blaze.db.kv.spec]
   [blaze.db.node.resource-indexer.spec]
   [blaze.db.node.util :as node-util]
   [blaze.db.resource-store :as rs]
   [blaze.db.search-param-registry :as sr]
   [blaze.executors :as ex]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.module :as m :refer [reg-collector]]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [prometheus.alpha :as prom :refer [defhistogram]]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent TimeUnit]))

(set! *warn-on-reflection* true)

(defhistogram duration-seconds
  "Durations in resource indexer."
  {:namespace "blaze"
   :subsystem "db_resource_indexer"}
  (take 14 (iterate #(* 2 %) 0.00001))
  "op")

(defhistogram index-entries
  "Number of index entries of a resource."
  {:namespace "blaze"
   :subsystem "db_resource_indexer"}
  (take 14 (iterate #(* 2 %) 1))
  "type")

(defn- compartment-resource-type-entry
  "Returns an entry into the :compartment-resource-type-index where `resource`
  is linked to `compartment`."
  {:arglists '([compartment resource])}
  [[comp-code comp-id] {:keys [id] :as resource}]
  (cr/index-entry
   [(codec/c-hash comp-code) (codec/id-byte-string comp-id)]
   (codec/tid (name (fhir-spec/fhir-type resource)))
   (codec/id-byte-string id)))

(defn- compartment-resource-type-entries [resource compartments]
  (mapv #(compartment-resource-type-entry % resource) compartments))

(defn- skip-indexing-msg [search-param resource cause-msg]
  (format "Skip indexing for search parameter `%s` on resource `%s/%s`. Cause: %s"
          (:url search-param) (name (fhir-spec/fhir-type resource))
          (:id resource) (or cause-msg "<unknown>")))

(defn- search-param-index-entries
  [search-param linked-compartments hash resource]
  (-> (search-param/index-entries search-param linked-compartments hash resource)
      (ba/exceptionally
       (fn [{::anom/keys [message]}]
         (log/warn (skip-indexing-msg search-param resource message))
         nil))))

(defn- skip-indexing-compartments-msg [hash message]
  (format "Skip indexing compartments of resource with hash `%s` because of: %s"
          hash (or message "<unknown>")))

(defn- linked-compartments [search-param-registry hash resource]
  (-> (sr/linked-compartments search-param-registry resource)
      (ba/exceptionally
       (fn [{::anom/keys [message]}]
         (log/warn (skip-indexing-compartments-msg hash message))
         nil))))

(defn- search-params [search-param-registry resource]
  (sr/list-by-type search-param-registry (name (:fhir/type resource))))

(defn- enhance-resource [last-updated resource]
  (update resource :meta (fnil assoc #fhir/Meta{}) :lastUpdated last-updated))

(defn- index-resource*
  [{:keys [search-param-registry last-updated]} hash resource]
  (let [resource (enhance-resource last-updated resource)
        compartments (linked-compartments search-param-registry hash resource)]
    (into
     (compartment-resource-type-entries resource compartments)
     (mapcat #(search-param-index-entries % compartments hash resource))
     (search-params search-param-registry resource))))

(defn- index-resource [context [hash resource]]
  (log/trace "Index resource with hash" (str hash))
  (with-open [_ (prom/timer duration-seconds "index-resource")]
    (let [entries (index-resource* context hash resource)]
      (prom/observe! index-entries (name (:fhir/type resource)) (count entries))
      entries)))

(defn- put! [store entries]
  (with-open [_ (prom/timer duration-seconds "put")]
    (kv/put! store entries)))

(defn- async-index-resource [{:keys [kv-store executor] :as context} entry]
  (ac/supply-async
   #(put! kv-store (index-resource context entry))
   executor))

(defn- index-resources* [context entries]
  (log/trace "Index" (count entries) "resource(s)")
  (ac/all-of (mapv (partial async-index-resource context) entries)))

(defn- cmd-rs-keys [tx-cmds variant]
  (into [] (keep (fn [{:keys [type hash]}] (when hash [type hash variant]))) tx-cmds))

(defn index-resources
  "Returns a CompletableFuture that will complete after all resources of
   `tx-data` are indexed.

   The :instant from `tx-data` is used to index the _lastUpdated search
   parameter because the property doesn't exist in the resource itself."
  {:arglists '([resource-indexer tx-data])}
  [{:keys [resource-store] :as resource-indexer}
   {:keys [tx-cmds] resources :local-payload last-updated :instant}]
  (let [context (assoc resource-indexer :last-updated last-updated)]
    (if resources
      (index-resources* context resources)
      (-> (rs/multi-get resource-store (cmd-rs-keys tx-cmds :complete))
          (ac/then-compose
           (fn [resources]
             (index-resources*
              context
              (into {} (map (fn [[[_type hash] resource]] [hash resource])) resources))))))))

(defn- re-index-resource [search-param [[_type hash] resource]]
  (log/trace "Re-index resource with hash" (str hash))
  (search-param-index-entries search-param nil hash resource))

(defn async-re-index-resources [kv-store executor search-param resources]
  (ac/supply-async
   #(kv/put!
     kv-store
     (coll/eduction
      (mapcat (partial re-index-resource search-param))
      resources))
   executor))

(defn- rs-keys [resource-handles variant]
  (mapv #(node-util/rs-key % variant) resource-handles))

(defn- re-index-resources*
  [{:keys [resource-store kv-store executor]} search-param resource-handles]
  (log/trace "Re-index" (count resource-handles) "resource(s)")
  (do-sync [resources (rs/multi-get resource-store (rs-keys resource-handles :complete))]
    (async-re-index-resources kv-store executor search-param resources)))

(defn re-index-resources
  "Indexes the first 10000 resources from `resource-handles` using
  `search-param`.

  Returns a CompletableFuture that will complete with a map of:
  * :num-resources - the number of resources indexed
  * :next - the resource handle to continue with"
  [resource-indexer search-param resource-handles]
  (let [handles (into [] (take 10001) resource-handles)
        [to-index next] (if (< 10000 (count handles))
                          [(pop handles) (peek handles)]
                          [handles])]
    (do-sync [_ (re-index-resources* resource-indexer search-param to-index)]
      {:num-resources (count to-index)
       :next next})))

(defmethod m/pre-init-spec :blaze.db.node/resource-indexer [_]
  (s/keys :req-un [:blaze.db/kv-store
                   :blaze.db/resource-store
                   :blaze.db/search-param-registry
                   ::executor]))

(defmethod ig/init-key :blaze.db.node/resource-indexer
  [key resource-indexer]
  (log/info "Init" (node-util/component-name key "resource indexer"))
  resource-indexer)

(defmethod m/pre-init-spec ::executor [_]
  (s/keys :opt-un [::num-threads]))

(defmethod ig/init-key ::executor
  [key {:keys [num-threads] :or {num-threads 4}}]
  (log/info "Init" (node-util/component-name key "resource indexer executor")
            "with" num-threads "threads")
  (ex/io-pool num-threads (node-util/thread-name-template key "resource-indexer-%d")))

(defmethod ig/halt-key! ::executor
  [_ executor]
  (log/info "Stopping resource indexer executor...")
  (ex/shutdown! executor)
  (if (ex/await-termination executor 10 TimeUnit/SECONDS)
    (log/info "Resource indexer executor was stopped successfully")
    (log/warn "Got timeout while stopping the resource indexer executor")))

(derive ::executor :blaze.metrics/thread-pool-executor)

(reg-collector ::duration-seconds
  duration-seconds)

(reg-collector ::index-entries
  index-entries)
