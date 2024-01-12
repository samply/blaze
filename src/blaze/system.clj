(ns blaze.system
  "Application System

  Call `init!` to initialize the system and `shutdown!` to bring it down.
  The specs at the beginning of the namespace describe the config which has to
  be given to `init!``. The server port has a default of `8080`."
  (:require
   [blaze.log]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.reader.edn :as edn]
   [clojure.walk :as walk]
   [integrant.core :as ig]
   [spec-coerce.alpha :refer [coerce]]
   [taoensso.timbre :as log])
  (:import
   [java.io PushbackReader]
   [java.security SecureRandom]
   [java.time Clock]
   [java.util.concurrent ThreadLocalRandom]))

(defrecord Cfg [env-var spec default])

(defn- cfg
  "Creates a config entry which consists of the name of an environment variable,
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
  "Resolves config entries to their actual values with the help of an
  environment."
  [config env]
  (walk/postwalk
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

(defrecord RefMap [key]
  ig/RefLike
  (ref-key [_] key)
  (ref-resolve [_ config resolvef]
    (into {} (map (fn [[k v]] [k (resolvef k v)])) (ig/find-derived config key))))

(def ^:private root-config
  {:blaze/version "0.23.4"

   :blaze/release-date "2024-01-10"

   :blaze/clock {}

   :blaze/rng-fn {}

   :blaze/secure-rng {}

   :blaze.fhir/structure-definition-repo {}

   :blaze.handler/health {}

   :blaze/rest-api
   {:base-url (->Cfg "BASE_URL" string? "http://localhost:8080")
    :version (ig/ref :blaze/version)
    :release-date (ig/ref :blaze/release-date)
    :structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)
    :node (ig/ref :blaze.db/node)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :auth-backends (ig/refset :blaze.auth/backend)
    :context-path (->Cfg "CONTEXT_PATH" string? "/fhir")
    :db-sync-timeout (->Cfg "DB_SYNC_TIMEOUT" pos-int? 10000)}

   :blaze.rest-api/requests-total {}
   :blaze.rest-api/request-duration-seconds {}
   :blaze.rest-api/parse-duration-seconds {}
   :blaze.rest-api/generate-duration-seconds {}

   :blaze.handler/app
   {:rest-api (ig/ref :blaze/rest-api)
    :health-handler (ig/ref :blaze.handler/health)
    :context-path (->Cfg "CONTEXT_PATH" string? "/fhir")}

   :blaze/server
   {:port (->Cfg "SERVER_PORT" nat-int? 8080)
    :handler (ig/ref :blaze.handler/app)
    :version (ig/ref :blaze/version)
    :async? true}

   :blaze/thread-pool-executor-collector
   {:executors (->RefMap :blaze.metrics/thread-pool-executor)}

   :blaze.metrics/registry
   {:collectors (ig/refset :blaze.metrics/collector)}

   :blaze.metrics/handler
   {:registry (ig/ref :blaze.metrics/registry)}

   [:blaze/server :blaze.metrics/server]
   {:name "metrics"
    :port (->Cfg "METRICS_SERVER_PORT" nat-int? 8081)
    :handler (ig/ref :blaze.metrics/handler)
    :version (ig/ref :blaze/version)
    :min-threads 2}})

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
    (-> (assoc-in config [:base-config :blaze.db/storage] (keyword key))
        (update :base-config merge (get storage (keyword key))))))

(defn- merge-features
  "Merges feature config portions of enabled features into `base-config`."
  {:arglists '([blaze-edn env])}
  [{:keys [base-config features]} env]
  (reduce
   (fn [res {:keys [name config] :as feature}]
     (let [enabled? (feature-enabled? env feature)]
       (log/info "Feature" name (if enabled? "enabled" "disabled"))
       (if enabled?
         (merge-with merge res config)
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

(defmethod ig/init-key :blaze/version
  [_ version]
  version)

(defmethod ig/init-key :blaze/release-date
  [_ release-date]
  release-date)

(defmethod ig/init-key :blaze.db/storage
  [_ storage]
  storage)

(defmethod ig/init-key :blaze/clock
  [_ _]
  (Clock/systemDefaultZone))

(defmethod ig/init-key :blaze/rng-fn
  [_ _]
  #(ThreadLocalRandom/current))

(defmethod ig/init-key :blaze/secure-rng
  [_ _]
  (SecureRandom.))
