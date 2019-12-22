(ns blaze.system
  "Application System

  Call `init!` to initialize the system and `shutdown!` to bring it down.
  The specs at the beginning of the namespace describe the config which has to
  be given to `init!``. The server port has a default of `8080`."
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.walk :refer [postwalk]]
    [blaze.executors :as ex]
    [blaze.server :as server]
    [clojure.tools.reader.edn :as edn]
    [integrant.core :as ig]
    [spec-coerce.alpha :refer [coerce]]
    [taoensso.timbre :as log]
    [clojure.java.io :as io])
  (:import
    [java.io PushbackReader]
    [java.time Clock]))



;; ---- Functions -------------------------------------------------------------

(defrecord Cfg [env-var spec default])


(defn- cfg
  "Creates a config entry which consists of the name of a environment variable,
  a spec and a default value.

  Config entries appear in blaze.edn files."
  [[env-var spec-form default]]
  (let [spec
        (if (symbol? spec-form)
          (var-get (resolve spec-form))
          spec-form)]
    (->Cfg env-var spec default)))


(defn- read-blaze-edn []
  (log/info "Try to read blaze.edn ...")
  (try
    (with-open [rdr (PushbackReader. (io/reader (io/resource "blaze.edn")))]
      (edn/read
        {:readers {'blaze/ref ig/ref 'blaze/cfg cfg}}
        rdr))
    (catch Exception e
      (log/warn "Problem while reading blaze.edn. Skipping it." e))))


(defn- get-blank [m k default]
  (let [v (get m k)]
    (if (or (nil? v) (str/blank? v))
      default
      v)))


(defn resolve-config
  "Resolves config entries to there actual values with the help of an
  environment."
  [config env]
  (postwalk
    (fn [x]
      (if (instance? Cfg x)
        (when-let [value (get-blank env (:env-var x) (:default x))]
          (coerce (:spec x) value))
        x))
    config))


(defn- load-namespaces [config]
  (log/info "Loading namespaces ...")
  (let [loaded-ns (ig/load-namespaces config)]
    (log/info "Loaded the following namespaces:" (str/join ", " loaded-ns))))


(def ^:private root-config
  {:blaze/version "0.8.0-alpha.7"

   :blaze/clock {}

   :blaze/structure-definition {}

   :blaze/search-parameter {}

   :blaze.datomic.transaction/executor {}

   :blaze.datomic/conn
   {:structure-definitions (ig/ref :blaze/structure-definition)
    :search-parameters (ig/ref :blaze/search-parameter)
    :database/uri (->Cfg "DATABASE_URI" string? "datomic:mem://dev")}

   :blaze.datomic/resource-upsert-duration-seconds {}
   :blaze.datomic/execution-duration-seconds {}
   :blaze.datomic/resources-total {}
   :blaze.datomic/datoms-total {}

   :blaze.handler/health {}

   :blaze/rest-api
   {:base-url (->Cfg "BASE_URL" string? "http://localhost:8080")
    :version (ig/ref :blaze/version)
    :structure-definitions (ig/ref :blaze/structure-definition)
    :auth-backends (ig/refset :blaze.auth/backend)
    :context-path "/fhir"}

   :blaze.rest-api/requests-total {}
   :blaze.rest-api/request-duration-seconds {}
   :blaze.rest-api/parse-duration-seconds {}
   :blaze.rest-api/generate-duration-seconds {}
   :blaze.rest-api/tx-data-duration-seconds {}

   :blaze.handler/app
   {:rest-api (ig/ref :blaze/rest-api)
    :health-handler (ig/ref :blaze.handler/health)}

   :blaze.server/executor {}

   :blaze/server
   {:port (->Cfg "SERVER_PORT" nat-int? 8080)
    :executor (ig/ref :blaze.server/executor)
    :handler (ig/ref :blaze.handler/app)
    :version (ig/ref :blaze/version)}

   :blaze/thread-pool-executor-collector
   {:executors (ig/refmap :blaze.metrics/thread-pool-executor)}

   :blaze.metrics/registry
   {:collectors (ig/refset :blaze.metrics/collector)}

   :blaze.handler/metrics
   {:registry (ig/ref :blaze.metrics/registry)}

   :blaze.metrics/server
   {:port (->Cfg "METRICS_SERVER_PORT" nat-int? 8081)
    :handler (ig/ref :blaze.handler/metrics)
    :version (ig/ref :blaze/version)}})


(defn- feature-enabled?
  {:arglists '([env feature])}
  [env {:keys [toggle]}]
  (let [value (get env toggle)]
    (and (not (str/blank? value)) (not= "false" (some-> value str/trim)))))


(defn- merge-features
  {:arglists '([blaze-edn env])}
  [{:keys [base-config features]} env]
  (reduce
    (fn [res {:keys [name config] :as feature}]
      (let [enabled? (feature-enabled? env feature)]
        (log/info "Feature" name (if enabled? "enabled" "disabled"))
        (if enabled?
          (merge res config)
          res)))
    base-config
    features))


(s/fdef init!
  :args (s/cat :env any?))

(defn init!
  [{level "LOG_LEVEL" :or {level "info"} :as env}]
  (log/info "Set log level to:" (str/lower-case level))
  (log/merge-config! {:level (keyword (str/lower-case level))})
  (let [config (merge-features (read-blaze-edn) env)
        config (-> (merge-with merge root-config config)
                   (resolve-config env))]
    (load-namespaces config)
    (-> config ig/prep ig/init)))


(defn shutdown! [system]
  (ig/halt! system))



;; ---- Integrant Hooks -------------------------------------------------------

(defmethod ig/init-key :blaze/version
  [_ version]
  version)


(defmethod ig/init-key :blaze/clock
  [_ _]
  (Clock/systemDefaultZone))


#_(defmethod ig/init-key :fhir-capabilities-handler
    [_ {:keys [base-url version structure-definitions]}]
    (log/debug "Init FHIR capabilities interaction handler")
    (fhir-capabilities-handler/handler base-url version structure-definitions))


(defmethod ig/init-key :blaze.server/executor
  [_ _]
  (log/info "Init server executor")
  (ex/cpu-bound-pool "server-%d"))


(derive :blaze.server/executor :blaze.metrics/thread-pool-executor)


(defmethod ig/init-key :blaze/server
  [_ {:keys [port executor handler version]}]
  (log/info "Start main server on port" port)
  (server/init! port executor handler version))


(defmethod ig/halt-key! :blaze/server
  [_ server]
  (log/info "Shutdown main server")
  (server/shutdown! server))


(defmethod ig/init-key :blaze.metrics/server
  [_ {:keys [port handler version]}]
  (log/info "Start metrics server on port" port)
  (server/init! port (ex/single-thread-executor) handler version))


(defmethod ig/halt-key! :blaze.metrics/server
  [_ server]
  (log/info "Shutdown metrics server")
  (server/shutdown! server))
