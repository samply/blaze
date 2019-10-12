(ns blaze.interaction.create
  "FHIR create interaction.

  https://www.hl7.org/fhir/http.html#create"
  (:require
    [blaze.executors :refer [executor?]]
    [blaze.fhir.response.create :as response]
    [blaze.handler.fhir.util :as handler-fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.terminology-service :refer [term-service?]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [integrant.core :as ig]
    [manifold.deferred :as md]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


(defn- validate-resource [type body]
  (cond
    (not (map? body))
    (md/error-deferred
      {::anom/category ::anom/incorrect
       :fhir/issue "structure"
       :fhir/operation-outcome "MSG_JSON_OBJECT"})

    (not= type (get body "resourceType"))
    (md/error-deferred
      {::anom/category ::anom/incorrect
       :fhir/issue "invariant"
       :fhir/operation-outcome "MSG_RESOURCE_TYPE_MISMATCH"})

    :else
    body))


(defn- handler-intern [transaction-executor conn term-service]
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        :keys [headers body]
        ::reitit/keys [router]}]
    (let [return-preference (handler-util/preference headers "return")
          id (str (d/squuid))]
      (-> (validate-resource type body)
          (md/chain' #(assoc % "id" id))
          (md/chain'
            #(handler-fhir-util/upsert-resource
               transaction-executor
               conn
               term-service
               (d/db conn)
               :server-assigned-id
               %))
          (md/chain'
            #(response/build-created-response
               router return-preference (:db-after %) type id))
          (md/catch' handler-util/error-response)))))


(s/def :handler.fhir/create fn?)


(s/fdef handler
  :args
  (s/cat
    :transaction-executor executor?
    :conn ::ds/conn
    :term-service term-service?)
  :ret :handler.fhir/create)

(defn handler
  ""
  [transaction-executor conn term-service]
  (-> (handler-intern transaction-executor conn term-service)
      (wrap-observe-request-duration "create")))


(defmethod ig/init-key :blaze.interaction/create
  [_ {:database/keys [transaction-executor conn] :keys [term-service]}]
  (log/info "Init FHIR create interaction handler")
  (handler transaction-executor conn term-service))
