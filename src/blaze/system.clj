(ns blaze.system
  "Application System

  Call `init!` to initialize the system and `shutdown!` to bring it down.
  The specs at the beginning of the namespace describe the config which has to
  be given to `init!``. The server port has a default of `8080`."
  (:require
    [clojure.core.cache :as cache]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [blaze.bundle :as bundle]
    [blaze.datomic.transaction :as tx]
    [blaze.datomic.schema :as schema]
    [blaze.executors :as ex]
    [blaze.handler.app :as app-handler]
    [blaze.handler.cql-evaluation :as cql-evaluation-handler]
    [blaze.handler.fhir.capabilities :as fhir-capabilities-handler]
    [blaze.handler.fhir.core :as fhir-core-handler]
    [blaze.handler.fhir.create :as fhir-create-handler]
    [blaze.handler.fhir.delete :as fhir-delete-handler]
    [blaze.handler.fhir.history-instance :as fhir-history-instance-handler]
    [blaze.handler.fhir.history-type :as fhir-history-type-handler]
    [blaze.handler.fhir.history-system :as fhir-history-system-handler]
    [blaze.handler.fhir.read :as fhir-read-handler]
    [blaze.handler.fhir.search :as fhir-search-handler]
    [blaze.handler.fhir.transaction :as fhir-transaction-handler]
    [blaze.handler.fhir.update :as fhir-update-handler]
    [blaze.fhir.operation.evaluate-measure.handler
     :as fhir-operation-evaluate-measure-handler]
    [blaze.fhir.operation.evaluate-measure.measure :as evaluate-measure]
    [blaze.handler.health :as health-handler]
    [blaze.handler.metrics :as metrics-handler]
    [blaze.metrics :as metrics]
    [blaze.middleware.fhir.metrics :as fhir-metrics]
    [blaze.middleware.json :as json]
    [blaze.server :as server]
    [blaze.structure-definition :refer [read-structure-definitions]]
    [blaze.terminology-service.extern :as ts]
    [datomic.api :as d]
    [datomic-tools.schema :as dts]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import [io.prometheus.client CollectorRegistry]
           [io.prometheus.client.hotspot StandardExports MemoryPoolsExports
                                         GarbageCollectorExports ThreadExports
                                         ClassLoadingExports VersionInfoExports]
           [java.time Clock]))



;; ---- Specs -------------------------------------------------------------

(s/def :log/level
  #{"trace" "debug" "info" "warn" "error" "fatal" "report"
    "TRACE" "DEBUG" "INFO" "WARN" "ERROR" "FATAL" "REPORT"})
(s/def ::milli-second pos-int?)
(s/def :config/logging (s/keys :opt [:log/level]))
(s/def :database/uri string?)
(s/def :config/database-conn (s/keys :opt [:database/uri]))
(s/def :term-service/uri string?)
(s/def :term-service/proxy-host string?)
(s/def :term-service/proxy-port pos-int?)
(s/def :term-service/proxy-user string?)
(s/def :term-service/proxy-password string?)
(s/def :term-service/connection-timeout ::milli-second)
(s/def :term-service/request-timeout ::milli-second)
(s/def :config/term-service
  (s/keys :opt-un [:term-service/uri
                   :term-service/proxy-host
                   :term-service/proxy-port
                   :term-service/proxy-user
                   :term-service/proxy-password
                   :term-service/connection-timeout
                   :term-service/request-timeout]))
(s/def :cache/threshold pos-int?)
(s/def :config/base-url string?)
(s/def :config/cache (s/keys :opt [:cache/threshold]))
(s/def :config/fhir-capabilities-handler (s/keys :opt-un [:config/base-url]))
(s/def :config/fhir-core-handler (s/keys :opt-un [:config/base-url]))
(s/def :config/server (s/keys :opt-un [::server/port]))
(s/def :config/metrics-server (s/keys :opt-un [::server/port]))

(s/def :system/config
  (s/keys
    :opt-un
    [:config/logging
     :config/database-conn
     :config/term-service
     :config/cache
     :config/fhir-capabilities-handler
     :config/fhir-core-handler
     :config/server
     :config/metrics-server]))



;; ---- Functions -------------------------------------------------------------

(def ^:private version "0.7.0-alpha3")

(def ^:private base-url "http://localhost:8080")

(def ^:private default-config
  {:structure-definitions {}

   :database-conn
   {:structure-definitions (ig/ref :structure-definitions)
    :database/uri "datomic:mem://dev"}

   :term-service
   {:uri "http://tx.fhir.org/r4"}

   :cache {}

   :transaction-interaction-executor {}

   :evaluate-measure-operation-executor {}

   :health-handler {}

   :cql-evaluation-handler
   {:database/conn (ig/ref :database-conn)
    :cache (ig/ref :cache)}

   :fhir-capabilities-handler
   {:base-url base-url
    :version version
    :structure-definitions (ig/ref :structure-definitions)}

   :fhir-create-handler
   {:database/conn (ig/ref :database-conn)
    :term-service (ig/ref :term-service)}

   :fhir-delete-handler
   {:database/conn (ig/ref :database-conn)}

   :fhir-history-instance-handler
   {:database/conn (ig/ref :database-conn)}

   :fhir-history-type-handler
   {:database/conn (ig/ref :database-conn)}

   :fhir-history-system-handler
   {:database/conn (ig/ref :database-conn)}

   :fhir-read-handler
   {:database/conn (ig/ref :database-conn)}

   :fhir-search-handler
   {:database/conn (ig/ref :database-conn)}

   :fhir-transaction-handler
   {:database/conn (ig/ref :database-conn)
    :executor (ig/ref :transaction-interaction-executor)
    :term-service (ig/ref :term-service)}

   :fhir-update-handler
   {:database/conn (ig/ref :database-conn)
    :term-service (ig/ref :term-service)}

   :fhir-operation-evaluate-measure-handler
   {:clock (Clock/systemDefaultZone)
    :term-service (ig/ref :term-service)
    :executor (ig/ref :evaluate-measure-operation-executor)
    :database/conn (ig/ref :database-conn)}

   :fhir-core-handler
   {:base-url base-url
    :database/conn (ig/ref :database-conn)
    :handlers
    {:handler.fhir/capabilities (ig/ref :fhir-capabilities-handler)
     :handler.fhir/create (ig/ref :fhir-create-handler)
     :handler.fhir/delete (ig/ref :fhir-delete-handler)
     :handler.fhir/history-instance (ig/ref :fhir-history-instance-handler)
     :handler.fhir/history-type (ig/ref :fhir-history-type-handler)
     :handler.fhir/history-system (ig/ref :fhir-history-system-handler)
     :handler.fhir/read (ig/ref :fhir-read-handler)
     :handler.fhir/search (ig/ref :fhir-search-handler)
     :handler.fhir/transaction (ig/ref :fhir-transaction-handler)
     :handler.fhir/update (ig/ref :fhir-update-handler)
     :handler.fhir.operation/evaluate-measure
     (ig/ref :fhir-operation-evaluate-measure-handler)}}

   :app-handler
   {:handlers
    {:handler/cql-evaluation (ig/ref :cql-evaluation-handler)
     :handler/health (ig/ref :health-handler)
     :handler.fhir/core (ig/ref :fhir-core-handler)}}

   :server-executor {}

   :server
   {:port 8080
    :executor (ig/ref :server-executor)
    :handler (ig/ref :app-handler)
    :version version}

   :metrics/registry
   {:server-executor (ig/ref :server-executor)
    :transaction-interaction-executor (ig/ref :transaction-interaction-executor)
    :evaluate-measure-operation-executor (ig/ref :evaluate-measure-operation-executor)}

   :metrics-handler
   {:registry (ig/ref :metrics/registry)}

   :metrics-server
   {:port 8081
    :handler (ig/ref :metrics-handler)
    :version version}})


(s/fdef init!
  :args (s/cat :config :system/config))

(defn init!
  [{:log/keys [level] :or {level "info"} :as config}]
  (log/info "Set log level to:" (str/lower-case level))
  (log/merge-config! {:level (keyword (str/lower-case level))})
  (ig/init (merge-with merge default-config config)))


(defn shutdown! [system]
  (ig/halt! system))



;; ---- Integrant Hooks -------------------------------------------------------

(defmethod ig/init-key :structure-definitions
  [_ _]
  (let [structure-definitions (read-structure-definitions)]
    (log/info "Read structure definitions resulting in:"
              (count structure-definitions) "structure definitions")
    structure-definitions))


(defn- upsert-schema [uri structure-definitions]
  (let [conn (d/connect uri)
        _ @(d/transact-async conn (dts/schema))
        {:keys [tx-data]} @(d/transact-async conn (schema/structure-definition-schemas structure-definitions))]
    (log/info "Upsert schema in database:" uri "creating" (count tx-data) "new facts")))


(defmethod ig/init-key :database-conn
  [_ {:database/keys [uri] :keys [structure-definitions]}]
  (if (d/create-database uri)
    (do
      (log/info "Created database at:" uri)
      (upsert-schema uri structure-definitions))
    (log/info "Use existing database at:" uri))

  (log/info "Connect with database:" uri)
  (d/connect uri))


(defmethod ig/init-key :term-service
  [_ {:keys [uri proxy-host proxy-port proxy-user proxy-password
             connection-timeout request-timeout]}]
  (log/info
    (cond->
      (str "Init terminology server connection: " uri)
      proxy-host
      (str " using proxy host " proxy-host)
      proxy-port
      (str ", port " proxy-port)
      proxy-user
      (str ", user " proxy-user)
      proxy-password
      (str ", password ***")
      connection-timeout
      (str ", connection timeout " connection-timeout " ms")
      request-timeout
      (str ", request timeout " request-timeout " ms")))
  (ts/term-service
    uri
    (cond-> {}
      proxy-host (assoc :host proxy-host)
      proxy-port (assoc :port proxy-port)
      proxy-user (assoc :user proxy-user)
      proxy-password (assoc :password proxy-password))
    connection-timeout request-timeout))


(defmethod ig/init-key :cache
  [_ {:cache/keys [threshold] :or {threshold 128}}]
  (atom (cache/lru-cache-factory {} :threshold threshold)))


(defmethod ig/init-key :transaction-interaction-executor
  [_ _]
  (ex/cpu-bound-pool "transaction-interaction-%d"))


(defmethod ig/init-key :health-handler
  [_ _]
  (health-handler/handler))


(defmethod ig/init-key :cql-evaluation-handler
  [_ {:database/keys [conn] :keys [cache]}]
  (cql-evaluation-handler/handler conn cache))


(defmethod ig/init-key :fhir-capabilities-handler
  [_ {:keys [base-url version structure-definitions]}]
  (log/debug "Init FHIR capabilities interaction handler")
  (fhir-capabilities-handler/handler base-url version structure-definitions))


(defmethod ig/init-key :fhir-create-handler
  [_ {:database/keys [conn] :keys [term-service]}]
  (log/debug "Init FHIR create interaction handler")
  (fhir-create-handler/handler conn term-service))


(defmethod ig/init-key :fhir-delete-handler
  [_ {:database/keys [conn]}]
  (log/debug "Init FHIR delete interaction handler")
  (fhir-delete-handler/handler conn))


(defmethod ig/init-key :fhir-history-instance-handler
  [_ {:database/keys [conn]}]
  (log/debug "Init FHIR history instance interaction handler")
  (fhir-history-instance-handler/handler conn))


(defmethod ig/init-key :fhir-history-type-handler
  [_ {:database/keys [conn]}]
  (log/debug "Init FHIR history type interaction handler")
  (fhir-history-type-handler/handler conn))


(defmethod ig/init-key :fhir-history-system-handler
  [_ {:database/keys [conn]}]
  (log/debug "Init FHIR history system interaction handler")
  (fhir-history-system-handler/handler conn))


(defmethod ig/init-key :fhir-read-handler
  [_ {:database/keys [conn]}]
  (log/debug "Init FHIR read interaction handler")
  (fhir-read-handler/handler conn))


(defmethod ig/init-key :fhir-search-handler
  [_ {:database/keys [conn]}]
  (log/debug "Init FHIR search interaction handler")
  (fhir-search-handler/handler conn))


(defmethod ig/init-key :fhir-transaction-handler
  [_ {:database/keys [conn] :keys [term-service executor]}]
  (log/debug "Init FHIR transaction interaction handler")
  (fhir-transaction-handler/handler conn term-service executor))


(defmethod ig/init-key :fhir-update-handler
  [_ {:database/keys [conn] :keys [term-service]}]
  (log/debug "Init FHIR update interaction handler")
  (fhir-update-handler/handler conn term-service))


(defmethod ig/init-key :fhir-core-handler
  [_ {:keys [base-url handlers] :database/keys [conn]}]
  (let [fhir-base-url (str base-url "/fhir")]
    (log/info "Init FHIR RESTful API with base URL:" fhir-base-url)
    (fhir-core-handler/handler fhir-base-url conn handlers)))


(defmethod ig/init-key :evaluate-measure-operation-executor
  [_ _]
  (ex/cpu-bound-pool "evaluate-measure-operation-%d"))


(defmethod ig/init-key :fhir-operation-evaluate-measure-handler
  [_ {:keys [clock term-service executor] :database/keys [conn]}]
  (log/debug "Init FHIR $evaluate-measure operation handler")
  (fhir-operation-evaluate-measure-handler/handler clock conn term-service executor))


(defmethod ig/init-key :app-handler
  [_ {:keys [handlers]}]
  (log/debug "Init app handler")
  (app-handler/handler handlers))


(defmethod ig/init-key :server-executor
  [_ _]
  (ex/cpu-bound-pool "server-%d"))


(defmethod ig/init-key :server
  [_ {:keys [port executor handler version]}]
  (log/info "Start main server on port" port)
  (server/init! port executor handler version))


(defmethod ig/init-key :metrics/registry
  [_ {:keys [server-executor transaction-interaction-executor
             evaluate-measure-operation-executor]}]
  (log/debug "Init metrics registry")
  (doto (CollectorRegistry. true)
    (.register (StandardExports.))
    (.register (MemoryPoolsExports.))
    (.register (GarbageCollectorExports.))
    (.register (ThreadExports.))
    (.register (ClassLoadingExports.))
    (.register (VersionInfoExports.))
    (.register fhir-metrics/requests-total)
    (.register fhir-metrics/request-duration-seconds)
    (.register json/parse-duration-seconds)
    (.register json/generate-duration-seconds)
    (.register tx/resource-upsert-duration-seconds)
    (.register tx/execution-duration-seconds)
    (.register tx/resources-total)
    (.register tx/datoms-total)
    (.register bundle/tx-data-duration-seconds)
    (.register ts/errors-total)
    (.register ts/request-duration-seconds)
    (.register evaluate-measure/compile-duration-seconds)
    (.register evaluate-measure/evaluate-duration-seconds)
    (.register (metrics/thread-pool-executor-collector
                 [["server" server-executor]
                  ["transaction-interaction" transaction-interaction-executor]
                  ["evaluate-measure-operation" evaluate-measure-operation-executor]
                  ["transactor" tx/tx-executor]]))))


(defmethod ig/init-key :metrics-handler
  [_ {:keys [registry]}]
  (log/debug "Init metrics handler")
  (metrics-handler/metrics-handler registry))


(defmethod ig/init-key :metrics-server
  [_ {:keys [port handler version]}]
  (log/info "Start metrics server on port" port)
  (server/init! port (ex/single-thread-executor) handler version))


(defmethod ig/init-key :default
  [_ val]
  val)


(defmethod ig/halt-key! :server
  [_ server]
  (log/info "Shutdown main server")
  (server/shutdown! server))

(defmethod ig/halt-key! :metrics-server
  [_ server]
  (log/info "Shutdown metrics server")
  (server/shutdown! server))
