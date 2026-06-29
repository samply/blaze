(ns blaze.interaction.create
  "FHIR create interaction.

  https://www.hl7.org/fhir/http.html#create"
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.fhir.response.create :as response]
   [blaze.handler.util :as handler-util]
   [blaze.interaction.util :as iu]
   [blaze.module :as m]
   [blaze.util.clauses :as uc]
   [blaze.validator :as validator]
   [blaze.validator.spec]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [ring.util.codec :as ring-codec]
   [taoensso.timbre :as log]))

(defn- create-op [resource conditional-clauses]
  (cond-> [:create resource]
    (seq conditional-clauses)
    (conj conditional-clauses)))

(defn- conditional-clauses [{:strs [if-none-exist]}]
  (when-not (str/blank? if-none-exist)
    (-> if-none-exist ring-codec/form-decode uc/search-clauses)))

(defn- response-context [{:keys [headers] :as request} db-after]
  (let [return-preference (handler-util/preference headers "return")]
    (cond-> (assoc request :blaze/db db-after)
      return-preference
      (assoc :blaze.preference/return return-preference))))

(defn- create-resource
  [{:keys [node]}
   {{{:fhir.resource/keys [type]} :data} ::reitit/match
    :keys [headers]
    :as request}
   {:keys [id] :as resource}]
  (let [conditional-clauses (conditional-clauses headers)
        tx-op (create-op resource conditional-clauses)]
    (-> (d/transact node [tx-op])
        (ac/then-compose
         (fn [db-after]
           (if-let [handle (d/resource-handle db-after type id)]
             (response/build-response
              (response-context request db-after) tx-op nil handle)
             (-> (d/type-query db-after type conditional-clauses)
                 (ac/then-compose
                  (fn [handles]
                    (let [handle (coll/first handles)]
                      (response/build-response
                       (response-context request db-after) tx-op handle handle)))))))))))

(defn- resource-content-not-found-msg [{:blaze.db/keys [resource-handle]}]
  (format "The resource `%s/%s` was successfully created but it's content with hash `%s` was not found during response creation."
          (name (:fhir/type resource-handle)) (:id resource-handle)
          (:hash resource-handle)))

(defn- handler [{:keys [validator] :as context}]
  (fn [{:keys [body] :as request}]
    (let [resource (assoc (iu/strip-meta body) :id (m/luid context))]
      (-> (cond-> (ac/completed-future resource)
            validator (ac/then-compose (partial validator/validate validator)))
          (ac/then-compose (partial create-resource context request))
          (ac/exceptionally
           (fn [e]
             (cond-> e
               (ba/not-found? e)
               (assoc
                ::anom/category ::anom/fault
                ::anom/message (resource-content-not-found-msg e)
                :fhir/issue "incomplete"))))))))

(defn- check-resource [type body]
  (cond
    (nil? body)
    (ba/incorrect
     "Missing HTTP body."
     :fhir/issue "invalid")

    (not= type (-> body :fhir/type name))
    (iu/resource-type-mismatch-anom type body)))

(defn- wrap-resource-check [handler]
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        :keys [body] :as request}]
    (if-ok [_ (check-resource type body)]
      (handler request)
      ac/completed-future)))

(defmethod m/pre-init-spec :blaze.interaction/create [_]
  (s/keys :req-un [:blaze.db/node :blaze/clock :blaze/rng-fn]
          :opt-un [:blaze/validator]))

(defmethod ig/init-key :blaze.interaction/create [_ context]
  (log/info "Init FHIR create interaction handler")
  (-> (handler context)
      (wrap-resource-check)))
