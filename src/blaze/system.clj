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
    [blaze.executors :as executors]
    [blaze.handler.app :as app-handler]
    [blaze.handler.cql-evaluation :as cql-evaluation-handler]
    [blaze.handler.fhir.capabilities :as fhir-capabilities-handler]
    [blaze.handler.fhir.create :as fhir-create-handler]
    [blaze.handler.fhir.delete :as fhir-delete-handler]
    [blaze.handler.fhir.history-instance :as fhir-history-instance-handler]
    [blaze.handler.fhir.history-type :as fhir-history-type-handler]
    [blaze.handler.fhir.history-system :as fhir-history-system-handler]
    [blaze.handler.fhir.read :as fhir-read-handler]
    [blaze.handler.fhir.search :as fhir-search-handler]
    [blaze.handler.fhir.transaction :as fhir-transaction-handler]
    [blaze.handler.fhir.update :as fhir-update-handler]
    [blaze.handler.health :as health-handler]
    [blaze.handler.metrics :as metrics-handler]
    [blaze.metrics :as metrics]
    [blaze.middleware.fhir.metrics :as fhir-metrics]
    [blaze.middleware.json :as json]
    [blaze.server :as server]
    [blaze.structure-definition :refer [read-structure-definitions
                                        read-other]]
    [datomic.api :as d]
    [datomic-tools.schema :as dts]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import [io.prometheus.client CollectorRegistry]
           [io.prometheus.client.hotspot StandardExports MemoryPoolsExports
                                         GarbageCollectorExports ThreadExports
                                         ClassLoadingExports VersionInfoExports]))



;; ---- Specs -------------------------------------------------------------

(s/def :log/level
  #{"trace" "debug" "info" "warn" "error" "fatal" "report"
    "TRACE" "DEBUG" "INFO" "WARN" "ERROR" "FATAL" "REPORT"})
(s/def :config/logging (s/keys :opt [:log/level]))
(s/def :database/uri string?)
(s/def :config/database-conn (s/keys :req [:database/uri]))
(s/def :cache/threshold pos-int?)
(s/def :structure-definitions/path string?)
(s/def :config/base-uri string?)
(s/def :config/cache (s/keys :opt [:cache/threshold]))
(s/def :config/structure-definitions (s/keys :req [:structure-definitions/path]))
(s/def :config/fhir-capabilities-handler (s/keys :opt-un [:config/base-uri]))
(s/def :config/fhir-create-handler (s/keys :opt-un [:config/base-uri]))
(s/def :config/fhir-history-instance-handler (s/keys :opt-un [:config/base-uri]))
(s/def :config/fhir-history-type-handler (s/keys :opt-un [:config/base-uri]))
(s/def :config/fhir-history-system-handler (s/keys :opt-un [:config/base-uri]))
(s/def :config/fhir-search-handler (s/keys :opt-un [:config/base-uri]))
(s/def :config/fhir-update-handler (s/keys :opt-un [:config/base-uri]))
(s/def :config/server (s/keys :opt-un [::server/port]))
(s/def :config/metrics-server (s/keys :opt-un [::server/port]))

(s/def :system/config
  (s/keys
    :opt-un
    [:config/logging
     :config/database-conn
     :config/cache
     :config/structure-definitions
     :config/fhir-capabilities-handler
     :config/fhir-create-handler
     :config/fhir-history-instance-handler
     :config/fhir-history-type-handler
     :config/fhir-history-system-handler
     :config/fhir-search-handler
     :config/fhir-update-handler
     :config/server
     :config/metrics-server]))



;; ---- Functions -------------------------------------------------------------

(def ^:private version "0.6-alpha48")

(def ^:private base-uri "http://localhost:8080")

(def ^:private default-config
  {:logging {:log/level "info"}

   :structure-definitions
   {:structure-definitions/path "fhir/r4/structure-definitions"}

   :database-conn
   {:structure-definitions (ig/ref :structure-definitions)
    :database/uri "datomic:mem://dev"}

   :cache {}

   :transaction-interaction-executor {}

   :health-handler {}

   :cql-evaluation-handler
   {:database/conn (ig/ref :database-conn)
    :cache (ig/ref :cache)}

   :fhir-capabilities-handler
   {:base-uri base-uri
    :version version
    :structure-definitions (ig/ref :structure-definitions)}

   :fhir-create-handler
   {:base-uri base-uri
    :database/conn (ig/ref :database-conn)}

   :fhir-delete-handler
   {:database/conn (ig/ref :database-conn)}

   :fhir-history-instance-handler
   {:base-uri base-uri
    :database/conn (ig/ref :database-conn)}

   :fhir-history-type-handler
   {:base-uri base-uri
    :database/conn (ig/ref :database-conn)}

   :fhir-history-system-handler
   {:base-uri base-uri
    :database/conn (ig/ref :database-conn)}

   :fhir-read-handler
   {:database/conn (ig/ref :database-conn)}

   :fhir-search-handler
   {:base-uri base-uri
    :database/conn (ig/ref :database-conn)}

   :fhir-transaction-handler
   {:base-uri base-uri
    :executor (ig/ref :transaction-interaction-executor)
    :database/conn (ig/ref :database-conn)}

   :fhir-update-handler
   {:base-uri base-uri
    :database/conn (ig/ref :database-conn)}

   :app-handler
   {:database/conn (ig/ref :database-conn)
    :handlers
    {:handler/cql-evaluation (ig/ref :cql-evaluation-handler)
     :handler/health (ig/ref :health-handler)
     :handler.fhir/capabilities (ig/ref :fhir-capabilities-handler)
     :handler.fhir/create (ig/ref :fhir-create-handler)
     :handler.fhir/delete (ig/ref :fhir-delete-handler)
     :handler.fhir/history-instance (ig/ref :fhir-history-instance-handler)
     :handler.fhir/history-type (ig/ref :fhir-history-type-handler)
     :handler.fhir/history-system (ig/ref :fhir-history-system-handler)
     :handler.fhir/read (ig/ref :fhir-read-handler)
     :handler.fhir/search (ig/ref :fhir-search-handler)
     :handler.fhir/transaction (ig/ref :fhir-transaction-handler)
     :handler.fhir/update (ig/ref :fhir-update-handler)}}

   :server-executor {}

   :server
   {:port 8080
    :executor (ig/ref :server-executor)
    :handler (ig/ref :app-handler)
    :version version}

   :metrics/registry
   {:server-executor (ig/ref :server-executor)
    :transaction-interaction-executor (ig/ref :transaction-interaction-executor)}

   :metrics-handler
   {:registry (ig/ref :metrics/registry)}

   :metrics-server
   {:port 8081
    :handler (ig/ref :metrics-handler)
    :version version}})


(s/fdef init!
  :args (s/cat :config :system/config :keys (s/? (s/coll-of keyword?))))

(defn init!
  ([config]
   (ig/init (merge-with merge default-config config)))
  ([config keys]
   (ig/init (merge-with merge default-config config) keys)))


(defn shutdown! [system]
  (ig/halt! system))



;; ---- Integrant Hooks -------------------------------------------------------

(defmethod ig/init-key :logging
  [_ {:log/keys [level]}]
  (log/info "Set log level to:" (str/lower-case level))
  (log/merge-config! {:level (keyword (str/lower-case level))}))


(defmethod ig/init-key :structure-definitions
  [_ {:structure-definitions/keys [path]}]
  (let [structure-definitions (read-structure-definitions path)]
    (log/info "Read structure definitions from:" path "resulting in:"
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
    (log/info "Created database at:" uri)
    (log/info "Use existing database at:" uri))

  (log/info "Connect with database:" uri)

  (upsert-schema uri structure-definitions)

  (d/connect uri))


(defmethod ig/init-key :cache
  [_ {:cache/keys [threshold] :or {threshold 128}}]
  (atom (cache/lru-cache-factory {} :threshold threshold)))


(defmethod ig/init-key :transaction-interaction-executor
  [_ _]
  (executors/cpu-bound-pool))


(defmethod ig/init-key :health-handler
  [_ _]
  (health-handler/handler))


(defmethod ig/init-key :cql-evaluation-handler
  [_ {:database/keys [conn] :keys [cache]}]
  (cql-evaluation-handler/handler conn cache))


(defmethod ig/init-key :fhir-capabilities-handler
  [_ {:keys [base-uri version structure-definitions]}]
  (fhir-capabilities-handler/handler base-uri version structure-definitions))


(defmethod ig/init-key :fhir-create-handler
  [_ {:keys [base-uri] :database/keys [conn]}]
  (fhir-create-handler/handler base-uri conn))


(defmethod ig/init-key :fhir-delete-handler
  [_ {:database/keys [conn]}]
  (fhir-delete-handler/handler conn))


(defmethod ig/init-key :fhir-history-instance-handler
  [_ {:keys [base-uri] :database/keys [conn]}]
  (fhir-history-instance-handler/handler base-uri conn))


(defmethod ig/init-key :fhir-history-type-handler
  [_ {:keys [base-uri] :database/keys [conn]}]
  (fhir-history-type-handler/handler base-uri conn))


(defmethod ig/init-key :fhir-history-system-handler
  [_ {:keys [base-uri] :database/keys [conn]}]
  (fhir-history-system-handler/handler base-uri conn))


(defmethod ig/init-key :fhir-read-handler
  [_ {:database/keys [conn]}]
  (fhir-read-handler/handler conn))


(defmethod ig/init-key :fhir-search-handler
  [_ {:keys [base-uri] :database/keys [conn]}]
  (fhir-search-handler/handler base-uri conn))


(defmethod ig/init-key :fhir-transaction-handler
  [_ {:keys [base-uri executor] :database/keys [conn]}]
  (fhir-transaction-handler/handler base-uri conn executor))


(defmethod ig/init-key :fhir-update-handler
  [_ {:keys [base-uri] :database/keys [conn]}]
  (fhir-update-handler/handler base-uri conn))


(defmethod ig/init-key :app-handler
  [_ {:database/keys [conn] :keys [handlers]}]
  (app-handler/handler conn handlers))


(defmethod ig/init-key :server-executor
  [_ _]
  (executors/cpu-bound-pool))


(defmethod ig/init-key :server
  [_ {:keys [port executor handler version]}]
  (log/info "Start main server on port" port)
  (server/init! port executor handler version))


(defmethod ig/init-key :metrics/registry
  [_ {:keys [server-executor transaction-interaction-executor]}]
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
    (.register (metrics/thread-pool-executor-collector
                 [["server" server-executor]
                  ["transaction-interaction" transaction-interaction-executor]
                  ["transactor" tx/tx-executor]]))))


(defmethod ig/init-key :metrics-handler
  [_ {:keys [registry]}]
  (metrics-handler/metrics-handler registry))


(defmethod ig/init-key :metrics-server
  [_ {:keys [port handler version]}]
  (log/info "Start metrics server on port" port)
  (server/init! port (executors/single-thread-executor) handler version))


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
