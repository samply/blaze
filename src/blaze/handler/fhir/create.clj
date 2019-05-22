(ns blaze.handler.fhir.create
  "FHIR create interaction.

  https://www.hl7.org/fhir/http.html#create"
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.util :as util]
    [blaze.handler.fhir.util :as handler-fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.exception :refer [wrap-exception]]
    [blaze.middleware.json :refer [wrap-json]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [manifold.deferred :as md]
    [ring.util.response :as ring]
    [ring.util.time :as ring-time]))


(defn- validate-resource [type id body]
  (if-not (map? body)
    (md/error-deferred
      {::anom/category ::anom/incorrect
       :fhir/issue "structure"
       :fhir/operation-outcome "MSG_JSON_OBJECT"})
    (if-not (= type (get body "resourceType"))
      (md/error-deferred
        {::anom/category ::anom/incorrect
         :fhir/issue "invariant"
         :fhir/operation-outcome "MSG_RESOURCE_TYPE_MISMATCH"})
      (assoc body "id" id))))


(defn- build-response [base-uri headers type id {db :db-after}]
  (let [last-modified (:db/txInstant (util/basis-transaction db))
        return-preference (handler-util/preference headers "return")
        versionId (d/basis-t db)]
    (-> (ring/created
          (str base-uri "/fhir/" type "/" id "/_history/" versionId)
          (cond
            (= "minimal" return-preference)
            nil
            (= "OperationOutcome" return-preference)
            {:resourceType "OperationOutcome"}
            :else
            (pull/pull-resource db type id)))
        (ring/header "Last-Modified" (ring-time/format-date last-modified))
        (ring/header "ETag" (str "W/\"" versionId "\"")))))


(defn handler-intern [base-uri conn]
  (fn [{{:keys [type]} :route-params :keys [headers body]}]
    (let [id (str (d/squuid))]
      (-> (validate-resource type id body)
          (md/chain' #(handler-fhir-util/update-resource conn %))
          (md/chain' #(build-response base-uri headers type id %))
          (md/catch' handler-util/error-response)))))


(s/def :handler.fhir/update fn?)


(s/fdef handler
  :args (s/cat :base-uri string? :conn ::ds/conn)
  :ret :handler.fhir/update)

(defn handler
  ""
  [base-uri conn]
  (-> (handler-intern base-uri conn)
      (wrap-json)
      (wrap-exception)))
