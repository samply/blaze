(ns blaze.interaction.create
  "FHIR create interaction.

  https://www.hl7.org/fhir/http.html#create"
  (:require
    [blaze.anomaly :as ba]
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
    [blaze.db.spec]
    [blaze.fhir.response.create :as response]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.create.spec]
    [blaze.interaction.util :as iu]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [ring.util.codec :as ring-codec]
    [taoensso.timbre :as log]))


(defn- resource-type-mismatch-msg [type body]
  (format "Resource type `%s` doesn't match the endpoint type `%s`."
          (-> body :fhir/type name) type))


(defn- validate-resource [type body]
  (cond
    (nil? body)
    (ba/incorrect
      "Missing HTTP body."
      :fhir/issue "invalid")

    (not= type (-> body :fhir/type name))
    (ba/incorrect
      (resource-type-mismatch-msg type body)
      :fhir/issue "invariant"
      :fhir/operation-outcome "MSG_RESOURCE_TYPE_MISMATCH")

    :else body))


(defn- create-op [resource conditional-clauses]
  (cond-> [:create resource]
    (seq conditional-clauses)
    (conj conditional-clauses)))


(defn- conditional-clauses [headers]
  (some-> headers (get "if-none-exist") ring-codec/form-decode iu/clauses))


(defn- response-context [{:keys [headers] :as request} db-after]
  (let [return-preference (handler-util/preference headers "return")]
    (cond-> (assoc request :blaze/db db-after)
      return-preference
      (assoc :blaze.preference/return return-preference))))


(defn- handler [{:keys [node executor] :as context}]
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        :keys [headers body]
        :as request}]
    (let [id (iu/luid context)
          conditional-clauses (conditional-clauses headers)]
      (-> (ac/completed-future (validate-resource type body))
          (ac/then-apply #(assoc % :id id))
          (ac/then-compose
            #(d/transact node [(create-op % conditional-clauses)]))
          ;; it's important to switch to the executor here, because otherwise
          ;; the central indexing thread would execute response building.
          (ac/then-apply-async identity executor)
          (ac/then-compose
            (fn [db-after]
              (if-let [handle (d/resource-handle db-after type id)]
                (response/build-response
                  (response-context request db-after) nil handle)
                (let [handle (first (d/type-query db-after type conditional-clauses))]
                  (response/build-response
                    (response-context request db-after) handle handle)))))))))


(defmethod ig/pre-init-spec :blaze.interaction/create [_]
  (s/keys :req-un [:blaze.db/node ::executor :blaze/clock :blaze/rng-fn]))


(defmethod ig/init-key :blaze.interaction/create [_ context]
  (log/info "Init FHIR create interaction handler")
  (-> (handler context)
      (wrap-observe-request-duration "create")))
