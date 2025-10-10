(ns blaze.fhir.operation.graphql
  "Main entry point into the $graphql operation."
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.executors :as ex]
   [blaze.fhir.operation.graphql.spec]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.module :as m]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]
   [com.walmartlabs.lacinia :as lacinia]
   [com.walmartlabs.lacinia.resolve :as resolve]
   [com.walmartlabs.lacinia.schema :as ls]
   [com.walmartlabs.lacinia.util :as lu]
   [integrant.core :as ig]
   [jsonista.core :as j]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent TimeUnit]))

(def schema
  {:objects
   {:Patient
    {:fields
     {:id {:type 'String}
      :gender {:type 'String}}}

    :Observation
    {:fields
     {:id {:type 'String}
      :subject {:type :Reference}}}

    :Reference
    {:fields
     {:reference {:type 'String}}}

    :Query
    {:fields
     {:PatientList
      {:type '(list :Patient)
       :args {:gender {:type 'String}}}

      :ObservationList
      {:type '(list :Observation)
       :args {:code {:type 'String}}}}}}})

(defn- clauses [args]
  (mapv (fn [[key value]] [(name key) value]) args))

(defn- type-query [db type args]
  (if (seq args)
    (d/type-query db type (clauses args))
    (d/type-list db type)))

(defn- read-json [source]
  (j/read-value source j/keyword-keys-object-mapper))

(defn- write-read-json [writing-context x]
  (read-json (fhir-spec/write-json-as-bytes writing-context x)))

(defn- to-error [{::anom/keys [message]}]
  {:message message})

(defn- resolve-type-list [type {:keys [writing-context db]} args _]
  (log/trace (format "execute %sList query" type))
  (let [result (resolve/resolve-promise)]
    (-> (d/pull-many db (type-query db type args))
        (ac/when-complete
         (fn [r e]
           (resolve/deliver! result (mapv (partial write-read-json writing-context) r) (some-> e to-error)))))
    result))

(defn- compile-schema [options]
  (-> schema
      (lu/inject-resolvers
       {:Query/PatientList (partial resolve-type-list "Patient")
        :Query/ObservationList (partial resolve-type-list "Observation")})
      (ls/compile options)))

(defn- execute-query
  [schema writing-context {:keys [request-method] :blaze/keys [db] :as request}]
  (let [context {:writing-context writing-context :db db}]
    (if (= :post request-method)
      (let [{{:keys [query variables]} :body} request]
        (lacinia/execute schema query variables context))
      (lacinia/execute schema (get (:params request) "query") nil context))))

(defmethod m/pre-init-spec ::handler [_]
  (s/keys :req-un [:blaze.db/node :blaze.fhir/writing-context ::executor]))

(defmethod ig/init-key ::handler [_ {:keys [writing-context executor] :as context}]
  (log/info "Init FHIR $graphql operation handler")
  (let [schema (compile-schema context)]
    (fn [request]
      (ac/supply-async
       #(ring/response (execute-query schema writing-context request))
       executor))))

(defmethod m/pre-init-spec ::executor [_]
  (s/keys :opt-un [::num-threads]))

(defn- executor-init-msg [num-threads]
  (format "Init $graphql operation executor with %d threads" num-threads))

(defmethod ig/init-key ::executor
  [_ {:keys [num-threads] :or {num-threads 4}}]
  (log/info (executor-init-msg num-threads))
  (ex/io-pool num-threads "operation-graphql-%d"))

(defmethod ig/halt-key! ::executor
  [_ executor]
  (log/info "Stopping $graphql operation executor...")
  (ex/shutdown! executor)
  (if (ex/await-termination executor 10 TimeUnit/SECONDS)
    (log/info "$graphql operation executor was stopped successfully")
    (log/warn "Got timeout while stopping the $graphql operation executor")))

(derive ::executor :blaze.metrics/thread-pool-executor)
