(ns blaze.interaction.update
  "FHIR update interaction.

  https://www.hl7.org/fhir/http.html#update"
  (:require
    [blaze.db.api :as d]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [manifold.deferred :as md]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [taoensso.timbre :as log])
  (:import
    [java.time ZonedDateTime ZoneId]
    [java.time.format DateTimeFormatter]))


(set! *warn-on-reflection* true)


(def ^:private gmt (ZoneId/of "GMT"))


(defn- last-modified [{:blaze.db.tx/keys [instant]}]
  (->> (ZonedDateTime/ofInstant instant gmt)
       (.format DateTimeFormatter/RFC_1123_DATE_TIME)))


(defn- validate-resource [type id body]
  (cond
    (not (map? body))
    (md/error-deferred
      {::anom/category ::anom/incorrect
       :fhir/issue "structure"
       :fhir/operation-outcome "MSG_JSON_OBJECT"})

    (not= type (:resourceType body))
    (md/error-deferred
      {::anom/category ::anom/incorrect
       :fhir/issue "invariant"
       :fhir/operation-outcome "MSG_RESOURCE_TYPE_MISMATCH"})

    (not (contains? body :id))
    (md/error-deferred
      {::anom/category ::anom/incorrect
       :fhir/issue "required"
       :fhir/operation-outcome "MSG_RESOURCE_ID_MISSING"})

    (not= id (:id body))
    (md/error-deferred
      {::anom/category ::anom/incorrect
       :fhir/issue "invariant"
       :fhir/operation-outcome "MSG_RESOURCE_ID_MISMATCH"})

    (not (fhir-spec/valid? body))
    (md/error-deferred
      {::anom/category ::anom/incorrect
       ::anom/message "Resource invalid."
       :fhir/issue "invariant"})

    :else
    body))


(defn- build-response
  [router headers type id old-resource db]
  (let [new-resource (d/resource db type id)
        return-preference (handler-util/preference headers "return")
        vid (-> new-resource :meta :versionId)
        {:blaze.db/keys [tx]} (meta new-resource)]
    (cond->
      (-> (cond
            (= "minimal" return-preference)
            nil
            (= "OperationOutcome" return-preference)
            {:resourceType "OperationOutcome"}
            :else
            new-resource)
          (ring/response)
          (ring/status (if old-resource 200 201))
          (ring/header "Last-Modified" (last-modified tx))
          (ring/header "ETag" (str "W/\"" vid "\"")))
      (nil? old-resource)
      (ring/header
        "Location" (fhir-util/versioned-instance-url router type id vid)))))


(defn- handler-intern [node]
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params
        :keys [headers body]
        ::reitit/keys [router]}]
    (log/debug (format "PUT [base]/%s/%s" type id))
    (let [db (d/db node)]
      (-> (validate-resource type id body)
          (md/chain' #(d/submit-tx node [[:put %]]))
          (md/chain'
            #(build-response
               router headers type id (d/resource db type id) %))
          (md/catch' handler-util/error-response)))))


(defn handler [node]
  (-> (handler-intern node)
      (wrap-observe-request-duration "update")))


(defmethod ig/init-key :blaze.interaction/update
  [_ {:keys [node]}]
  (log/info "Init FHIR update interaction handler")
  (handler node))
