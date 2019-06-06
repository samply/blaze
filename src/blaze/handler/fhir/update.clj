(ns blaze.handler.fhir.update
  "FHIR update interaction.

  https://www.hl7.org/fhir/http.html#update"
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.util :as util]
    [blaze.handler.fhir.util :as handler-fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [manifold.deferred :as md]
    [ring.util.response :as ring]
    [ring.util.time :as ring-time]))


(defn- validate-resource [type id body]
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

    (not= id (get body "id"))
    (md/error-deferred
      {::anom/category ::anom/incorrect
       :fhir/issue "invariant"
       :fhir/operation-outcome "MSG_RESOURCE_ID_MISMATCH"})

    :else
    body))


(defn- build-response [base-uri headers type id old-resource {db :db-after}]
  (let [basis-transaction (util/basis-transaction db)
        last-modified (:db/txInstant basis-transaction)
        return-preference (handler-util/preference headers "return")]
    (cond->
      (-> (cond
            (= "minimal" return-preference)
            nil
            (= "OperationOutcome" return-preference)
            {:resourceType "OperationOutcome"}
            :else
            (pull/pull-resource db type id))
          (ring/response)
          (ring/status (if old-resource 200 201))
          (ring/header "Last-Modified" (ring-time/format-date last-modified))
          (ring/header "ETag" (str "W/\"" (d/basis-t db) "\"")))
      (nil? old-resource)
      (ring/header "Location" (str base-uri "/fhir/" type "/" id)))))


(defn- handler-intern [base-uri conn]
  (fn [{{:keys [type id]} :path-params :keys [headers body]}]
    (let [db (d/db conn)]
      (if (util/cached-entity db (keyword type))
        (-> (validate-resource type id body)
            (md/chain' #(handler-fhir-util/upsert-resource conn db -2 %))
            (md/chain' #(build-response base-uri headers type id (util/resource db type id) %))
            (md/catch' handler-util/error-response))
        (handler-util/error-response
          {::anom/category ::anom/not-found
           :fhir/issue "not-found"})))))


(s/def :handler.fhir/update fn?)


(s/fdef handler
  :args (s/cat :base-uri string? :conn ::ds/conn)
  :ret :handler.fhir/update)

(defn handler
  ""
  [base-uri conn]
  (-> (handler-intern base-uri conn)
      (wrap-observe-request-duration "update")))
