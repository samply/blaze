(ns blaze.interaction.update
  "FHIR update interaction.

  https://www.hl7.org/fhir/http.html#update"
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.fhir.response.create :as response]
   [blaze.fhir.util :as fu]
   [blaze.handler.util :as handler-util]
   [blaze.interaction.util :as iu]
   [blaze.module :as m]
   [blaze.validator :as validator]
   [blaze.validator.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [taoensso.timbre :as log]))

(defn- response-context [{:keys [headers] :as request} db-after]
  (let [return-preference (handler-util/preference headers "return")]
    (cond-> (assoc request :blaze/db db-after)
      return-preference
      (assoc :blaze.preference/return return-preference))))

(defn- update-resource
  [{:keys [node]}
   {{:strs [if-match if-none-match]} :headers :as request}
   {:fhir/keys [type] :keys [id] :as resource}]
  (if-ok [tx-op (iu/update-tx-op (d/db node) resource if-match if-none-match)]
    (-> (d/transact node [tx-op])
        (ac/then-compose
         (fn [db-after]
           (let [[new-handle old-handle] (into [] (take 2) (d/instance-history db-after (name type) id))]
             (response/build-response
              (response-context request db-after)
              tx-op
              old-handle
              new-handle)))))
    ac/completed-future))

(defn- resource-content-not-found-msg [{:blaze.db/keys [resource-handle]}]
  (format "The resource `%s/%s` was successfully updated but it's content with hash `%s` was not found during response creation."
          (name (:fhir/type resource-handle)) (:id resource-handle)
          (:hash resource-handle)))

(defn- handler [{:keys [validator] :as context}]
  (fn [{:keys [body] :as request}]
    (-> (cond-> (ac/completed-future (iu/strip-meta body))
          validator (ac/then-compose (partial validator/validate validator)))
        (ac/then-compose
         (fn [resource]
           (ac/retry2 #(update-resource context request resource)
                      #(and (ba/conflict? %) (= "keep" (-> % :blaze.db/tx-cmd :op))
                            (nil? (:http/status %))))))
        (ac/exceptionally
         (fn [e]
           (cond-> e
             (ba/not-found? e)
             (assoc
              ::anom/category ::anom/fault
              ::anom/message (resource-content-not-found-msg e)
              :fhir/issue "incomplete")))))))

(defn- id-mismatch-msg [resource-id id]
  (format "The resource id `%s` doesn't match the endpoints id `%s`."
          resource-id id))

(defn- check-resource [type id body]
  (cond
    (nil? body)
    (ba/incorrect
     "Missing HTTP body."
     :fhir/issue "invalid")

    (not= type (-> body :fhir/type name))
    (iu/resource-type-mismatch-anom type body)

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

    (->> body :meta :tag (some fu/subsetted?))
    (ba/incorrect
     "Resources with tag SUBSETTED may be incomplete and so can't be used in updates."
     :fhir/issue "processing")))

(defn- wrap-resource-check [handler]
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params :keys [body] :as request}]
    (if-ok [_ (check-resource type id body)]
      (handler request)
      ac/completed-future)))

(defmethod m/pre-init-spec :blaze.interaction/update [_]
  (s/keys :req-un [:blaze.db/node]
          :opt-un [:blaze/validator]))

(defmethod ig/init-key :blaze.interaction/update [_ context]
  (log/info "Init FHIR update interaction handler")
  (-> (handler context)
      (wrap-resource-check)))
