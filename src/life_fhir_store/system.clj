(ns life-fhir-store.system
  "Application System

  Call `init!` to initialize the system and `shutdown!` to bring it down.
  The specs at the beginning of the namespace describe the config which has to
  be given to `init!``. The server port has a default of `8080`."
  (:require
    [clojure.core.cache :as cache]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [datomic.api :as d]
    [datomic-tools.schema :as dts]
    [integrant.core :as ig]
    [life-fhir-store.datomic.schema :as schema]
    [life-fhir-store.handler.app :as app-handler]
    [life-fhir-store.handler.cql-evaluation :as cql-evaluation-handler]
    [life-fhir-store.handler.fhir.transaction :as fhir-transaction-handler]
    [life-fhir-store.handler.health :as health-handler]
    [life-fhir-store.server :as server]
    [life-fhir-store.structure-definition :refer [read-structure-definitions]]
    [taoensso.timbre :as log]))



;; ---- Specs -------------------------------------------------------------

(s/def :database/uri string?)
(s/def :config/database-conn (s/keys :req [:database/uri]))
(s/def :cache/threshold pos-int?)
(s/def :structure-definitions/path string?)
(s/def :config/cache (s/keys :opt [:cache/threshold]))
(s/def :config/structure-definitions (s/keys :req [:structure-definitions/path]))
(s/def :config/server (s/keys :opt-un [::server/port]))

(s/def :system/config
  (s/keys
    :req-un [:config/database-conn :config/cache
             :config/structure-definitions :config/server]))



;; ---- Functions -------------------------------------------------------------

(def ^:private default-config
  {:structure-definitions {}

   :database-conn
   {:structure-definitions (ig/ref :structure-definitions)}

   :cache {}

   :health-handler {}

   :cql-evaluation-handler
   {:database/conn (ig/ref :database-conn)
    :cache (ig/ref :cache)}

   :fhir-transaction-handler
   {:database/conn (ig/ref :database-conn)}

   :app-handler
   {:handler/health (ig/ref :health-handler)
    :handler/cql-evaluation (ig/ref :cql-evaluation-handler)
    :handler.fhir/transaction (ig/ref :fhir-transaction-handler)}

   :server {:port 8080 :handler (ig/ref :app-handler)}})


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

(defmethod ig/init-key :database-conn
  [_ {:database/keys [uri] :keys [structure-definitions]}]
  (if (d/create-database uri)
    (log/info "Created database at:" uri)
    (log/info "Use existing database at:" uri))

  (log/info "Connect with database:" uri)

  (let [conn (d/connect uri)
        _ @(d/transact conn (dts/schema))
        {:keys [tx-data]} @(d/transact conn (schema/structure-definition-schemas (vals structure-definitions)))]
    (log/info "Upsert schema in database:" uri "creating" (count tx-data) "new facts")
    conn))


(defmethod ig/init-key :cache
  [_ {:cache/keys [threshold] :or {threshold 128}}]
  (atom (cache/lru-cache-factory {} :threshold threshold)))


(defmethod ig/init-key :structure-definitions
  [_ {:structure-definitions/keys [path]}]
  (let [structure-definitions (read-structure-definitions path)]
    (log/info "Read structure definitions from:" path "resulting in:"
              (str/join ", " (keys structure-definitions)))
    structure-definitions))


(defmethod ig/init-key :health-handler
  [_ _]
  (health-handler/handler))


(defmethod ig/init-key :cql-evaluation-handler
  [_ {:database/keys [conn] :keys [cache]}]
  (cql-evaluation-handler/handler conn cache))


(defmethod ig/init-key :fhir-transaction-handler
  [_ {:database/keys [conn]}]
  (fhir-transaction-handler/handler conn))


(defmethod ig/init-key :app-handler
  [_ handlers]
  (app-handler/handler handlers))


(defmethod ig/init-key :server
  [_ {:keys [port handler]}]
  (server/init! port handler))


(defmethod ig/init-key :default
  [_ val]
  val)


(defmethod ig/halt-key! :server
  [_ server]
  (server/shutdown! server))
