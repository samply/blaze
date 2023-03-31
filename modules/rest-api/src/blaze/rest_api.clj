(ns blaze.rest-api
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.search-param-registry.spec]
    [blaze.db.spec]
    [blaze.fhir.structure-definition-repo :as sdr]
    [blaze.handler.util :as handler-util]
    [blaze.module :refer [reg-collector]]
    [blaze.rest-api.capabilities :as capabilities]
    [blaze.rest-api.middleware.cors :as cors]
    [blaze.rest-api.middleware.log :refer [wrap-log]]
    [blaze.rest-api.middleware.metrics :as metrics]
    [blaze.rest-api.middleware.output :as output]
    [blaze.rest-api.middleware.resource :as resource]
    [blaze.rest-api.routes :as routes]
    [blaze.rest-api.spec]
    [blaze.spec]
    [buddy.auth.middleware :refer [wrap-authentication]]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [reitit.ring]
    [reitit.ring.spec]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(def ^:private wrap-cors
  {:name :cors
   :wrap cors/wrap-cors})


(defn batch-handler
  "Handler for individual requests of a batch request."
  [routes context-path]
  (reitit.ring/ring-handler
    (reitit.ring/router
      routes
      {:path context-path
       :syntax :bracket
       :reitit.middleware/transform
       (fn [middleware]
         (filterv (comp not #{:resource :auth-guard :output :sync :forwarded
                              :error :observe-request-duration} :name) middleware))})
    handler-util/default-batch-handler))


(defn- allowed-methods [{{:keys [result]} ::reitit/match}]
  (->> result
       (keep (fn [[k v]] (when v k)))
       (map (comp str/upper-case name))
       (str/join ",")))


(defn default-options-handler [request]
  (-> (ring/status 204)
      (ring/header "Access-Control-Allow-Methods" (allowed-methods request))
      (ring/header "Access-Control-Allow-Headers" "content-type")
      (ac/completed-future)))


(defn router
  {:arglists '([context capabilities-handler])}
  [{:keys [context-path] :or {context-path ""} :as context} capabilities-handler]
  (let [batch-handler-promise (promise)
        routes (routes/routes context capabilities-handler
                              batch-handler-promise)]
    (deliver batch-handler-promise (batch-handler routes context-path))
    (reitit.ring/router
      routes
      {:path context-path
       :syntax :bracket
       :reitit.ring/default-options-endpoint
       {:no-doc true
        :handler default-options-handler}})))


(defn handler
  "Whole app Ring handler."
  [{:keys [auth-backends] :as context}]
  (-> (reitit.ring/ring-handler
        (router
          context
          (capabilities/capabilities-handler context))
        (reitit.ring/routes
          (reitit.ring/redirect-trailing-slash-handler {:method :strip})
          (output/wrap-output handler-util/default-handler {:accept-all? true}))
        {:middleware
         (cond-> [wrap-cors]
           (seq auth-backends)
           (conj #(apply wrap-authentication % auth-backends)))})
      wrap-log))


(defmethod ig/pre-init-spec :blaze/rest-api [_]
  (s/keys
    :req-un
    [:blaze/base-url
     :blaze/version
     :blaze.fhir/structure-definition-repo
     :blaze.db/node
     :blaze.db/search-param-registry
     :blaze.rest-api/db-sync-timeout]
    :opt-un
    [:blaze/context-path
     ::auth-backends
     ::search-system-handler
     ::transaction-handler
     ::history-system-handler
     ::resource-patterns
     ::operations
     ::metadata-handler
     ::admin-handler
     :blaze.db/enforce-referential-integrity]))


(defmethod ig/init-key :blaze/rest-api
  [_
   {:keys [base-url context-path db-sync-timeout structure-definition-repo]
    :as context}]
  (log/info "Init FHIR RESTful API with base URL:" (str base-url context-path)
            "and a database sync timeout of" db-sync-timeout "ms")
  (handler
    (-> context
        (dissoc :structure-definition-repo)
        (assoc :structure-definitions (sdr/resources structure-definition-repo)))))


(reg-collector ::requests-total
  metrics/requests-total)


(reg-collector ::request-duration-seconds
  metrics/request-duration-seconds)


(reg-collector ::parse-duration-seconds
  resource/parse-duration-seconds)


(reg-collector ::generate-duration-seconds
  output/generate-duration-seconds)
