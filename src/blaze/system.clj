(ns blaze.system
  "Application System

  Call `init!` to initialize the system and `shutdown!` to bring it down.
  The specs at the beginning of the namespace describe the config which has to
  be given to `init!``. The server port has a default of `8080`."
  (:require
    [blaze.executors :as ex]
    [blaze.log]
    [blaze.server :as server]
    [blaze.server.spec]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.tools.reader.edn :as edn]
    [clojure.walk :refer [postwalk]]
    [integrant.core :as ig]
    [spec-coerce.alpha :refer [coerce]]
    [taoensso.timbre :as log])
  (:import
    [java.io PushbackReader]))



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
  (log/info "Loading namespaces (can take up to 20 seconds) ...")
  (let [loaded-ns (ig/load-namespaces config)]
    (log/info "Loaded the following namespaces:" (str/join ", " loaded-ns))))


(def ^:private root-config
  {:blaze/version "0.10.1-alpha.1"

   :blaze/structure-definition {}

   :blaze.handler/health {}

   :blaze.rest-api.json-parse/executor {}

   :blaze/rest-api
   {:base-url (->Cfg "BASE_URL" string? "http://localhost:8080")
    :version (ig/ref :blaze/version)
    :structure-definitions (ig/ref :blaze/structure-definition)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :auth-backends (ig/refset :blaze.auth/backend)
    :context-path (->Cfg "CONTEXT_PATH" string? "/fhir")
    :blaze.rest-api.json-parse/executor (ig/ref :blaze.rest-api.json-parse/executor)}

   :blaze.rest-api/requests-total {}
   :blaze.rest-api/request-duration-seconds {}
   :blaze.rest-api/parse-duration-seconds {}
   :blaze.rest-api/generate-duration-seconds {}

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
  "Determines whether a feature is enabled or not.

  Each feature has an environment variable name specified under :toggle. The
  value of the environment variable is read here and checked to be truthy or
  not. It is truthy if it is not blank and is not the word `false`."
  {:arglists '([env feature])}
  [env {:keys [toggle inverse?]}]
  (let [value (get env toggle)
        res (and (not (str/blank? value)) (not= "false" (some-> value str/trim)))]
    (if inverse? (not res) res)))


(defn- merge-storage
  [{:keys [storage] :as config} env]
  (let [key (get env "STORAGE" "in-memory")]
    (log/info "Use storage variant" key)
    (update config :base-config merge (get storage (keyword key)))))


(defn- merge-features
  "Merges feature config portions of enabled features into `base-config`."
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


(defn init!
  [{level "LOG_LEVEL" :or {level "info"} :as env}]
  (log/info "Set log level to:" (str/lower-case level))
  (log/set-level! (keyword (str/lower-case level)))
  (let [config (-> (read-blaze-edn)
                   (merge-storage env)
                   (merge-features env))
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


#_(defmethod ig/init-key :fhir-capabilities-handler
    [_ {:keys [base-url version structure-definitions]}]
    (log/debug "Init FHIR capabilities interaction handler")
    (fhir-capabilities-handler/handler base-url version structure-definitions))


(defn- executor-init-msg []
  (format "Init server executor with %d threads"
          (.availableProcessors (Runtime/getRuntime))))


(defmethod ig/init-key :blaze.server/executor
  [_ _]
  (log/info (executor-init-msg))
  (ex/manifold-cpu-bound-pool "server-%d"))


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
