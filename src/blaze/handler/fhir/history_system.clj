(ns blaze.handler.fhir.history-system
  "FHIR history interaction on thw whole system.

  https://www.hl7.org/fhir/http.html#history"
  (:require
    [blaze.datomic.util :as datomic-util]
    [blaze.handler.fhir.history.util :as history-util]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [ring.middleware.params :refer [wrap-params]]
    [ring.util.response :as ring]))


(defn- total* [db]
  (- (get (d/entity db :system) :system/version 0)))


(defn- total [db since-t]
  (let [total (total* db)]
    (if since-t
      (- total (total* (d/as-of db since-t)))
      total)))


(defn- expand-resources
  "Returns tuples of `transaction` and resource eid of resources changed in
  transaction."
  [transaction]
  (for [resource (:tx/resources transaction)]
    [transaction (:db/id resource)]))


(defn- build-response [base-uri db since-t params transactions]
  (ring/response
    {:resourceType "Bundle"
     :type "history"
     :total (total db since-t)
     :entry
     (into
       []
       (comp
         (mapcat expand-resources)
         (take (fhir-util/page-size params))
         (map (fn [[tx eid]] (history-util/build-entry base-uri db tx eid))))
       transactions)}))


(defn- handler-intern [base-uri conn]
  (fn [{:keys [query-params]}]
    (let [db (d/db conn)
          since-t (history-util/since-t db query-params)
          since-db (if since-t (d/since db since-t) db)
          transactions (datomic-util/system-transaction-history since-db)]
      (build-response base-uri db since-t query-params transactions))))


(s/def :handler.fhir/history-system fn?)


(s/fdef handler
  :args (s/cat :base-uri string? :conn ::ds/conn)
  :ret :handler.fhir/history-system)

(defn handler
  ""
  [base-uri conn]
  (-> (handler-intern base-uri conn)
      (wrap-params)
      (wrap-observe-request-duration "history-system")))
