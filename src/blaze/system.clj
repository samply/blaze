(ns blaze.system
  "Application System

  Call `init!` to initialize the system and `shutdown!` to bring it down.
  The specs at the beginning of the namespace describe the config which has to
  be given to `init!``. The server port has a default of `8080`."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.coerce-env :as ce]
   [blaze.fhir.spec.type.system :as system]
   [blaze.log]
   [blaze.path :refer [dir? path]]
   [blaze.rest-api :as-alias rest-api]
   [blaze.spec]
   [blaze.terminology-service :as ts]
   [blaze.util :as u :refer [str]]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.tools.reader.edn :as edn]
   [clojure.walk :as walk]
   [integrant.core :as ig]
   [java-time.api :as time]
   [spec-coerce.alpha :as sc :refer [coerce]]
   [taoensso.timbre :as log])
  (:import
   [java.io PushbackReader]
   [java.time Clock LocalDate]
   [java.util.concurrent ThreadLocalRandom]))

(defmethod sc/pred-coercer `java-time.amount/duration?
  [_]
  (reify
    sc/Coercer
    (-coerce [_ x]
      (cond
        (string? x)
        (try
          (time/duration x)
          (catch Exception _
            ::s/invalid))
        :else ::s/invalid))))

(defmethod sc/pred-coercer `dir?
  [_]
  (reify
    sc/Coercer
    (-coerce [_ x]
      (cond
        (string? x)
        (let [path (path x)]
          (if (dir? path)
            path
            ::s/invalid))
        :else ::s/invalid))))

(defrecord Cfg [env-var spec default])

(defn- resolve-special-default-values [default]
  (condp identical? default
    :available-processors (u/available-processors)
    default))

(defn- cfg
  "Creates a config entry which consists of the name of an environment variable,
  a spec and a default value.

  Config entries appear in blaze.edn files."
  [[env-var spec-form default]]
  (let [spec
        (if (symbol? spec-form)
          (var-get (resolve spec-form))
          spec-form)]
    (->Cfg env-var spec (resolve-special-default-values default))))

(defrecord RefMap [key]
  ig/RefLike
  (ref-key [_] key)
  (ref-resolve [_ config resolvef]
    (into {} (map (fn [[k v]] [k (resolvef k v)])) (ig/find-derived config key))))

(defn- read-blaze-edn []
  (log/info "Try to read blaze.edn ...")
  (try
    (with-open [rdr (PushbackReader. (io/reader (io/resource "blaze.edn")))]
      (edn/read
       {:readers
        {'blaze/ref ig/ref 'blaze/var ig/var 'blaze/ref-map ->RefMap
         'blaze/cfg cfg}}
       rdr))
    (catch Exception e
      (log/warn "Problem while reading blaze.edn. Skipping it." e))))

(defn- read-blaze-version-edn []
  (log/info "Try to read blaze/version.edn ...")
  (try
    (when-let [url (io/resource "blaze/version.edn")]
      (with-open [rdr (PushbackReader. (io/reader url))]
        (edn/read rdr)))
    (catch Exception e
      (log/warn "Problem while reading blaze/version.edn. Skipping it." e))))

(defn resolve-config
  "Resolves config entries to their actual values with the help of an
  environment."
  [config env settings]
  (let [settings (volatile! settings)]
    (-> (walk/postwalk
         (fn [x]
           (if (instance? Cfg x)
             (when-some [value (ce/get-blank env (:env-var x) (:default x))]
               (let [value (coerce (:spec x) value)]
                 (vswap! settings assoc (:env-var x) (ce/setting x value))
                 value))
             x))
         config)
        (assoc-in [:blaze/admin-api :settings] (vec (sort-by :name (map #(assoc (val %) :name (key %)) @settings)))))))

(defn- load-namespaces [config]
  (log/info "Loading namespaces (can take up to 20 seconds) ...")
  (let [loaded-ns (ig/load-namespaces config)]
    (log/info "Loaded the following namespaces:" (str/join ", " loaded-ns))))

(def ^:private root-config
  {:blaze/version "latest"

   :blaze/release-date (str (LocalDate/now))

   :blaze.handler/health {}

   :blaze/rest-api
   {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)
    :auth-backends (ig/refset :blaze.auth/backend)
    :context-path (->Cfg "CONTEXT_PATH" string? "/fhir")
    :db-sync-timeout (->Cfg "DB_SYNC_TIMEOUT" pos-int? 10000)}

   ::rest-api/requests-total {}
   ::rest-api/request-duration-seconds {}
   ::rest-api/parse-duration-seconds {}
   ::rest-api/generate-duration-seconds {}

   :blaze.handler/app
   {:rest-api (ig/ref :blaze/rest-api)
    :health-handler (ig/ref :blaze.handler/health)}

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
        (update :base-config (partial merge-with merge) (get storage (keyword key))))))

(defn- conj-feature [config {:keys [key name toggle]} enabled?]
  (update-in config [:blaze/admin-api :features] (fnil conj [])
             {:key (clojure.core/name key) :name name :toggle toggle
              :enabled enabled?}))

(defn merge-features
  "Merges feature config portions of enabled features into `base-config`."
  {:arglists '([blaze-edn env])}
  [{:keys [base-config features]} env]
  (reduce
   (fn [res {:keys [name config] :as feature}]
     (let [enabled? (feature-enabled? env feature)
           res (conj-feature res feature enabled?)]
       (log/info "Feature" name (if enabled? "enabled" "disabled"))
       (if enabled?
         (merge-with
          (fn [v1 v2]
            (cond
              (and (vector? v1) (vector? v2)) (into v1 v2)
              :else (merge-with merge v1 v2)))
          res config)
         res)))
   base-config
   features))

(defn- post-init
  [{terminology-service :blaze.terminology-service/local :as system}]
  (when terminology-service
    (let [node (system [:blaze.db/node :blaze.db.main/node])]
      (ts/post-init! terminology-service node))))

(defn init!
  [{level "LOG_LEVEL" :or {level "info"} :as env}]
  (log/info "Set log level to:" (str/lower-case level))
  (log/set-min-level! (keyword (str/lower-case level)))
  (if-ok [{:keys [bind-map settings]}
          (ce/bindings-and-settings
           env
           'base-url ["BASE_URL" ce/coerce-base-url "http://localhost:8080"]
           'db-scale-factor ["DB_SCALE_FACTOR" ce/coerce-db-scale-factor "1"]
           'db-block-size ["DB_BLOCK_SIZE" ce/coerce-db-block-size "16384"])]

    (let [config (-> (read-blaze-edn)
                     (merge-storage env)
                     (merge-features env))
          root-config (merge root-config (read-blaze-version-edn))
          config (-> (merge-with merge root-config config)
                     (resolve-config env settings))]
      (load-namespaces config)
      (-> (ig/bind config bind-map)
          (ig/expand)
          (ig/init)
          (post-init)))

    ba/throw-anom))

(defn shutdown! [system]
  (ig/halt! system))

(defmethod ig/init-key :blaze/version
  [_ version]
  version)

(defmethod ig/init-key :blaze/release-date
  [_ release-date]
  (system/parse-date-time release-date))

(defmethod ig/init-key :blaze.db/storage
  [_ storage]
  storage)

(defmethod ig/init-key :blaze/clock
  [_ _]
  (Clock/systemDefaultZone))

(defmethod ig/init-key :blaze/rng-fn
  [_ _]
  #(ThreadLocalRandom/current))

(defmethod ig/init-key :blaze/java-tool-options
  [_ options]
  options)

(defn- update-existing [m k f x]
  (if-some [v (get m k)]
    (assoc m k (f v x))
    m))

(defn- scale-column-family [factor column-family]
  (-> column-family
      (update-existing :write-buffer-size-in-mb * factor)
      ;; the default max-write-buffer-number is 2
      ;; any scaling will result in 4 buffers
      (assoc :max-write-buffer-number 4)
      (update-existing :max-bytes-for-level-base-in-mb * factor)
      (update-existing :target-file-size-base-in-mb * factor)))

(defn- map-val [f]
  (map (fn [[k v]] [k (f v)])))

(defn- scale-column-families [column-families factor]
  (log/info "Scale RocksDB buffer and file sizes by a factor of" factor)
  (into {} (map-val (partial scale-column-family factor)) column-families))

(defmethod ig/expand-key :blaze.db.kv/rocksdb
  [key {:keys [scale-factor] :or {scale-factor 1} :as config}]
  {key (cond-> config
         (< 1 scale-factor)
         (update :column-families scale-column-families scale-factor))})
