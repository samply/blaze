(ns blaze.handler.fhir.history-instance
  "FHIR history interaction on a single resource.

  https://www.hl7.org/fhir/http.html#history"
  (:require
    [blaze.datomic.util :as util]
    [blaze.handler.fhir.history.util :as history-util]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [ring.middleware.params :refer [wrap-params]]
    [ring.util.response :as ring]))


(defn- resource-eid [db type id]
  (:db/id (util/resource db type id)))


(defn- total* [db eid]
  (let [resource (d/entity db eid)]
    ;; test for resource existence since `d/entity` always returns something
    ;; when called with an eid
    (if (:instance/version resource)
      (util/ordinal-version resource)
      0)))


(defn- total [db since-t eid]
  (let [total (total* db eid)]
    (if since-t
      (- total (total* (d/as-of db since-t) eid))
      total)))


(defn- build-response [base-uri db since-t params eid transactions]
  (ring/response
    {:resourceType "Bundle"
     :type "history"
     :total (total db since-t eid)
     :entry
     (into
       []
       (comp
         (take (fhir-util/page-size params))
         (map #(history-util/build-entry base-uri db % eid)))
       transactions)}))


(defn- handler-intern [base-uri conn]
  (fn [{{:keys [type id]} :path-params :keys [query-params]}]
    (let [db (d/db conn)]
      (if-let [eid (resource-eid db type id)]
        (let [since-t (history-util/since-t db query-params)
              since-db (if since-t (d/since db since-t) db)
              transactions (util/instance-transaction-history since-db eid)]
          (build-response base-uri db since-t query-params eid transactions))
        (handler-util/error-response
          {::anom/category ::anom/not-found
           :fhir/issue "not-found"})))))


(s/def :handler.fhir/history-instance fn?)


(s/fdef handler
  :args (s/cat :base-uri string? :conn ::ds/conn)
  :ret :handler.fhir/history-instance)

(defn handler
  ""
  [base-uri conn]
  (-> (handler-intern base-uri conn)
      (wrap-params)
      (wrap-observe-request-duration "history-instance")))
