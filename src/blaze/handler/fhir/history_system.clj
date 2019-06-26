(ns blaze.handler.fhir.history-system
  "FHIR history interaction on thw whole system.

  https://www.hl7.org/fhir/http.html#history"
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.util :as util]
    [blaze.handler.fhir.history.util :as history-util]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [ring.middleware.params :refer [wrap-params]]
    [ring.util.response :as ring]))


(defn- build-entry [base-uri log db transaction]
  (let [t (d/tx->t (:db/id transaction))
        db (d/as-of db t)
        resources (history-util/changed-resources log db t)]
    (for [resource resources]
      (let [type (util/resource-type resource)
            id ((util/resource-id-attr type) resource)]
        (cond->
          {:fullUrl (str base-uri "/fhir/" type "/" id)
           :request
           {:method (history-util/method resource)
            :url (history-util/url base-uri type id (:instance/version resource))}
           :response
           {:status (history-util/status resource)
            :etag (str "W/\"" t "\"")
            :lastModified (str (util/tx-instant transaction))}}
          (not (util/deleted? resource))
          (assoc :resource (pull/pull-resource* db type resource)))))))


(defn- total* [db]
  (- (get (d/entity db :system) :system/version 0)))


(defn- total [db since-t]
  (let [total (total* db)]
    (if since-t
      (- total (total* (d/as-of db since-t)))
      total)))


(defn- build-response [base-uri log db since-t params transactions]
  (ring/response
    {:resourceType "Bundle"
     :type "history"
     :total (total db since-t)
     :entry
     (into
       []
       (comp
         (mapcat #(build-entry base-uri log db %))
         (take (fhir-util/page-size params)))
       transactions)}))


(defn- handler-intern [base-uri conn]
  (fn [{:keys [query-params]}]
    (let [db (d/db conn)
          since-t (history-util/since-t db query-params)
          since-db (if since-t (d/since db since-t) db)
          transactions (util/system-transaction-history since-db)]
      (build-response base-uri (d/log conn) db since-t query-params
                      transactions))))


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
