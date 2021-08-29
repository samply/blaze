(ns blaze.interaction.update
  "FHIR update interaction.

  https://www.hl7.org/fhir/http.html#update"
  (:require
    [blaze.anomaly :as ba]
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
    [blaze.fhir.response.create :as response]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.update.spec]
    [blaze.interaction.util :as iu]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


(defn- type-mismatch-msg [resource-type type]
  (format "Invalid update interaction of a %s at a %s endpoint."
          resource-type type))


(defn- id-mismatch-msg [resource-id id]
  (format "The resource id `%s` doesn't match the endpoints id `%s`."
          resource-id id))


(defn- validate-resource [type id body]
  (cond
    (nil? body)
    (ba/incorrect
      "Missing HTTP body."
      :fhir/issue "invalid")

    (not= type (-> body :fhir/type name))
    (ba/incorrect
      (type-mismatch-msg (-> body :fhir/type name) type)
      :fhir/issue "invariant"
      :fhir/operation-outcome "MSG_RESOURCE_TYPE_MISMATCH")

    (not (contains? body :id))
    (ba/incorrect
      "Missing resource id."
      :fhir/issue "required"
      :fhir/operation-outcome "MSG_RESOURCE_ID_MISSING")

    (not= id (:id body))
    (ba/incorrect
      (id-mismatch-msg (:id body) id)
      :fhir/issue "invariant"
      :fhir/operation-outcome "MSG_RESOURCE_ID_MISMATCH")

    :else body))


(defn- tx-op [resource if-match-t]
  (cond-> [:put resource]
    if-match-t
    (conj if-match-t)))


(defn- response-context [{:keys [headers] :as request} db-after]
  (let [return-preference (handler-util/preference headers "return")]
    (cond-> (assoc request :blaze/db db-after)
      return-preference
      (assoc :blaze.preference/return return-preference))))


(defn- db-before [db-after]
  (d/as-of db-after (dec (d/basis-t db-after))))


(defn- handler [{:keys [node executor]}]
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params
        :keys [body]
        {:strs [if-match]} :headers
        :as request}]
    (-> (ac/completed-future (validate-resource type id body))
        (ac/then-compose
          #(d/transact node [(tx-op % (iu/etag->t if-match))]))
        ;; it's important to switch to the executor here, because otherwise
        ;; the central indexing thread would execute response building.
        (ac/then-apply-async identity executor)
        (ac/then-compose
          (fn [db-after]
            (response/build-response
              (response-context request db-after)
              (d/resource-handle (db-before db-after) type id)
              (d/resource-handle db-after type id)))))))


(defmethod ig/pre-init-spec :blaze.interaction/update [_]
  (s/keys :req-un [:blaze.db/node ::executor]))


(defmethod ig/init-key :blaze.interaction/update [_ context]
  (log/info "Init FHIR update interaction handler")
  (-> (handler context)
      (wrap-observe-request-duration "update")))
