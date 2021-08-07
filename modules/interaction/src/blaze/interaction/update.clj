(ns blaze.interaction.update
  "FHIR update interaction.

  https://www.hl7.org/fhir/http.html#update"
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
    [blaze.fhir.response.create :as response]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.update.spec]
    [blaze.interaction.util :as iu]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


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


(defn- tx-op [resource if-match-t]
  (cond-> [:put resource]
    if-match-t
    (conj if-match-t)))


(defn- db-before [db-after]
  (d/as-of db-after (dec (d/basis-t db-after))))


(defn- handler [{:keys [node executor]}]
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params
        :keys [body]
        {:strs [if-match] :as headers} :headers
        :blaze/keys [base-url]
        ::reitit/keys [router]}]
    (-> (ac/supply (validate-resource type id body))
        (ac/then-compose
          #(d/transact node [(tx-op % (iu/etag->t if-match))]))
        ;; it's important to switch to the executor here, because otherwise
        ;; the central indexing thread would execute response building.
        (ac/then-apply-async identity executor)
        (ac/then-compose
          (fn [db-after]
            (response/build-response
              base-url router (handler-util/preference headers "return")
              db-after
              (d/resource-handle (db-before db-after) type id)
              (d/resource-handle db-after type id))))
        (ac/exceptionally handler-util/error-response))))


(defmethod ig/pre-init-spec :blaze.interaction/update [_]
  (s/keys :req-un [:blaze.db/node ::executor]))


(defmethod ig/init-key :blaze.interaction/update [_ context]
  (log/info "Init FHIR update interaction handler")
  (-> (handler context)
      (wrap-observe-request-duration "update")))
