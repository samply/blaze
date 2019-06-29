(ns blaze.handler.fhir.history-type
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


(defn- total*
  "Total number of resource changes of `type` in `db`."
  [db type]
  (- (get (d/entity db (keyword type)) :type/version 0)))


(defn- total
  "Total number of resource changes since `t` (optional) of `type` in `db`."
  [db t type]
  (let [total (total* db type)]
    (if t
      (- total (total* (d/as-of db t) type))
      total)))


(defn- expand-resources
  "Returns tuples of `transaction` and resource eid of resources changed in
  transaction with are of `type`."
  [type transaction]
  (eduction
    (filter #(= type (datomic-util/resource-type %)))
    (map #(vector transaction (:db/id %)))
    (:tx/resources transaction)))


(defn- build-response [base-uri db since-t params type transactions]
  (ring/response
    {:resourceType "Bundle"
     :type "history"
     :total (total db since-t type)
     :entry
     (into
       []
       (comp
         (mapcat #(expand-resources type %))
         (take (fhir-util/page-size params))
         (map (fn [[tx eid]] (history-util/build-entry base-uri db tx eid))))
       transactions)}))


(defn- handler-intern [base-uri conn]
  (fn [{{:keys [type]} :path-params :keys [query-params]}]
    (let [db (d/db conn)
          since-t (history-util/since-t db query-params)
          since-db (if since-t (d/since db since-t) db)
          transactions (datomic-util/type-transaction-history since-db type)]
      (build-response base-uri db since-t query-params type transactions))))


(s/def :handler.fhir/history-type fn?)


(s/fdef handler
  :args (s/cat :base-uri string? :conn ::ds/conn)
  :ret :handler.fhir/history-type)

(defn handler
  ""
  [base-uri conn]
  (-> (handler-intern base-uri conn)
      (wrap-params)
      (wrap-observe-request-duration "history-type")))
