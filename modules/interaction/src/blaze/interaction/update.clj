(ns blaze.interaction.update
  "FHIR update interaction.

  https://www.hl7.org/fhir/http.html#update"
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.async-comp :as ac]
    [blaze.db.api :as d]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.update.spec]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
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
    (nil? body)
    (throw-anom
      ::anom/incorrect
      "Missing HTTP body."
      :fhir/issue "invalid")

    (not= type (-> body :fhir/type name))
    (throw-anom
      ::anom/incorrect
      (format "Invalid update interaction of a %s at a %s endpoint."
              (-> body :fhir/type name) type)
      :fhir/issue "invariant"
      :fhir/operation-outcome "MSG_RESOURCE_TYPE_MISMATCH")

    (not (contains? body :id))
    (throw-anom
      ::anom/incorrect
      "Missing resource id."
      :fhir/issue "required"
      :fhir/operation-outcome "MSG_RESOURCE_ID_MISSING")

    (not= id (:id body))
    (throw-anom
      ::anom/incorrect
      (format "The resource id `%s` doesn't match the endpoints id `%s`."
              (:id body) id)
      :fhir/issue "invariant"
      :fhir/operation-outcome "MSG_RESOURCE_ID_MISMATCH")

    :else body))


(defn- build-response
  [router headers type id old-handle db]
  (let [new-handle (d/resource-handle db type id)
        return-preference (handler-util/preference headers "return")
        tx (d/tx db (d/last-updated-t new-handle))
        vid (str (:blaze.db/t tx))
        created (or (nil? old-handle) (d/deleted? old-handle))]
    (log/trace (format "build-response of %s/%s with vid = %s" type id vid))
    (-> (cond
          (= "minimal" return-preference)
          (ac/completed-future nil)
          (= "OperationOutcome" return-preference)
          (ac/completed-future {:fhir/type :fhir/OperationOutcome})
          :else
          (d/pull db new-handle))
        (ac/then-apply
          (fn [body]
            (cond->
              (-> (ring/response body)
                  (ring/status (if created 201 200))
                  (ring/header "Last-Modified" (last-modified tx))
                  (ring/header "ETag" (str "W/\"" vid "\"")))
              created
              (ring/header
                "Location" (fhir-util/versioned-instance-url router type id vid))))))))


(defn- tx-op [resource if-match-t]
  (cond-> [:put resource]
    if-match-t
    (conj if-match-t)))


(defn- handler-intern [node executor]
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params
        :keys [body]
        {:strs [if-match] :as headers} :headers
        ::reitit/keys [router]}]
    (let [db (d/db node)]
      (-> (ac/supply (validate-resource type id body))
          (ac/then-compose
            #(d/transact node [(tx-op % (fhir-util/etag->t if-match))]))
          ;; it's important to switch to the transaction executor here, because
          ;; otherwise the central indexing thread would execute response
          ;; building.
          (ac/then-apply-async identity executor)
          (ac/then-compose
            #(build-response
               router headers type id (d/resource-handle db type id) %))
          (ac/exceptionally handler-util/error-response)))))


(defn handler [node executor]
  (-> (handler-intern node executor)
      (wrap-observe-request-duration "update")))


(defmethod ig/pre-init-spec :blaze.interaction/update [_]
  (s/keys :req-un [:blaze.db/node ::executor]))


(defmethod ig/init-key :blaze.interaction/update
  [_ {:keys [node executor]}]
  (log/info "Init FHIR update interaction handler")
  (handler node executor))
