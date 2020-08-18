(ns blaze.interaction.create
  "FHIR create interaction.

  https://www.hl7.org/fhir/http.html#create"
  (:require
    [blaze.anomaly :refer [ex-anom]]
    [blaze.async-comp :as ac]
    [blaze.db.api :as d]
    [blaze.fhir.response.create :as response]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.create.spec]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.uuid :refer [random-uuid]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


(defn- resource-type-mismatch-msg [type body]
  (format "Resource type `%s` doesn't match the endpoint type `%s`."
          (get body :resourceType) type))


(defn- validate-resource [type body]
  (cond
    (not (map? body))
    (throw
      (ex-anom
        {::anom/category ::anom/incorrect
         ::anom/message "Expect a JSON object."
         :fhir/issue "structure"
         :fhir/operation-outcome "MSG_JSON_OBJECT"}))

    (not= type (get body :resourceType))
    (throw
      (ex-anom
        {::anom/category ::anom/incorrect
         ::anom/message (resource-type-mismatch-msg type body)
         :fhir/issue "invariant"
         :fhir/operation-outcome "MSG_RESOURCE_TYPE_MISMATCH"}))

    (not (fhir-spec/valid? body))
    (throw
      (ex-anom
        {::anom/category ::anom/incorrect
         ::anom/message "Resource invalid."
         :fhir/issues (:fhir/issues (fhir-spec/explain-data body))}))

    :else
    body))


(defn- handler-intern [node executor]
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        :keys [headers body]
        ::reitit/keys [router]}]
    (let [return-preference (handler-util/preference headers "return")
          id (str (random-uuid))]
      (-> (ac/supply (validate-resource type body))
          (ac/then-apply #(assoc % :id id))
          (ac/then-compose #(d/transact node [[:create %]]))
          ;; it's important to switch to the transaction executor here, because
          ;; otherwise the central indexing thread would execute response
          ;; building.
          (ac/then-apply-async identity executor)
          (ac/then-compose
            #(response/build-created-response
               router return-preference % type id))
          (ac/exceptionally handler-util/error-response)))))


(defn handler [node executor]
  (-> (handler-intern node executor)
      (wrap-observe-request-duration "create")))


(defmethod ig/pre-init-spec :blaze.interaction/create [_]
  (s/keys :req-un [:blaze.db/node ::executor]))


(defmethod ig/init-key :blaze.interaction/create
  [_ {:keys [node executor]}]
  (log/info "Init FHIR create interaction handler")
  (handler node executor))
