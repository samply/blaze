(ns blaze.handler.fhir.history-type
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


(defn- changed-resources [db transaction]
  (into
    []
    (map #(d/entity db %))
    (d/q
      '[:find [?e ...] :in $ ?t :where [?e :instance/version _ ?t]]
      db (:db/id transaction))))


(defn- build-entry [base-uri db type transaction]
  (let [t (d/tx->t (:db/id transaction))
        db (d/as-of db t)
        resources (changed-resources db transaction)]
    (for [resource resources]
      (let [id ((util/resource-id-attr type) resource)]
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


(defn- total* [db type]
  (- (get (d/entity db (keyword type)) :type/version 0)))


(defn- total [db since-t type]
  (let [total (total* db type)]
    (if since-t
      (- total (total* (d/as-of db since-t) type))
      total)))


(defn- build-response [base-uri db since-t params type transactions]
  (ring/response
    {:resourceType "Bundle"
     :type "history"
     :total (total db since-t type)
     :entry
     (into
       []
       (comp
         (mapcat #(build-entry base-uri db type %))
         (take (fhir-util/page-size params)))
       transactions)}))


(defn- handler-intern [base-uri conn]
  (fn [{{:keys [type]} :path-params :keys [query-params]}]
    (let [db (d/db conn)
          since-t (history-util/since-t db query-params)
          since-db (if since-t (d/since db since-t) db)
          transactions (util/type-transaction-history since-db type)]
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
