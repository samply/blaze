(ns blaze.handler.fhir.update
  "FHIR update endpoint.

  https://www.hl7.org/fhir/http.html#update"
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.transaction :as tx]
    [blaze.datomic.util :as util]
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
      (if-not (= id (get body "id"))
        (md/error-deferred
          {::anom/category ::anom/incorrect
           :fhir/issue "invariant"
           :fhir/operation-outcome "MSG_RESOURCE_ID_MISMATCH"})
        body))))


(defn- update-resource
  [conn resource & {:keys [max-retries] :or {max-retries 5}}]
  (md/loop [retried 0
            db (d/db conn)]
    ;; TODO: the database has to be at least to be newer (sync on next t)
    (-> (tx/transact-async conn (tx/resource-update db resource))
        (md/catch'
          (fn [{::anom/keys [category] :as anomaly}]
            (if (and (< retried max-retries) (= ::anom/conflict category))
              (-> (d/sync conn (inc (d/basis-t db)))
                  (md/chain #(md/recur (inc retried) %)))
              (md/error-deferred anomaly)))))))


(defn- build-response [base-uri headers type id {db :db-after}]
  (let [{:keys [version]} (d/entity db [(keyword type "id") id])
        last-modified (:db/txInstant (util/basis-transaction db))
        return-preference (handler-util/preference headers "return")]
    (cond->
      (-> (cond
            (= "representation" return-preference)
            (pull/pull-resource db type id)
            (= "OperationOutcome" return-preference)
            {:resourceType "OperationOutcome"})
          (ring/response)
          (ring/status
            (cond
              (zero? version) 201
              (#{"representation" "OperationOutcome"} return-preference) 200
              :else 204))
          (ring/header "Last-Modified" (ring-time/format-date last-modified))
          (ring/header "ETag" (str "W/\"" (d/basis-t db) "\"")))
      (zero? version)
      (ring/header "Location" (str base-uri "/fhir/" type "/" id)))))


(defn handler-intern [base-uri conn]
  (fn [{{:keys [type id]} :route-params :keys [headers body]}]
    (-> (validate-resource type id body)
        (md/chain' #(update-resource conn %))
        (md/chain' #(build-response base-uri headers type id %))
        (md/catch' handler-util/error-response))))


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
