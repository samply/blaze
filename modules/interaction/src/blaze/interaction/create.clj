(ns blaze.interaction.create
  "FHIR create interaction.

  https://www.hl7.org/fhir/http.html#create"
  (:require
    [blaze.db.api :as d]
    [blaze.fhir.response.create :as response]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.uuid :refer [random-uuid]]
    [cognitect.anomalies :as anom]
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

    (not= type (get body :resourceType))
    (md/error-deferred
      {::anom/category ::anom/incorrect
       :fhir/issue "invariant"
       :fhir/operation-outcome "MSG_RESOURCE_TYPE_MISMATCH"})

    (not (fhir-spec/valid? body))
    (md/error-deferred
      {::anom/category ::anom/incorrect
       ::anom/message "Resource invalid."
       :fhir/issue "invariant"})

    :else
    body))


(defn- handler-intern [node]
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        :keys [headers body]
        ::reitit/keys [router]}]
    (let [return-preference (handler-util/preference headers "return")
          id (str (random-uuid))]
      (-> (validate-resource type body)
          (md/chain' #(assoc % :id id))
          (md/chain' #(d/submit-tx node [[:create %]]))
          (md/chain'
            #(response/build-created-response
               router return-preference % type id))
          (md/catch' handler-util/error-response)))))


(defn handler [node]
  (-> (handler-intern node)
      (wrap-observe-request-duration "create")))


(defmethod ig/init-key :blaze.interaction/create
  [_ {:keys [node]}]
  (log/info "Init FHIR create interaction handler")
  (handler node))
