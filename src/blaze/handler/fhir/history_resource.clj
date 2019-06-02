(ns blaze.handler.fhir.history-resource
  "FHIR history interaction on a single resource.

  https://www.hl7.org/fhir/http.html#history"
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.util :as util]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.exception :refer [wrap-exception]]
    [blaze.middleware.json :refer [wrap-json]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [ring.middleware.params :refer [wrap-params]]
    [ring.util.response :as ring])
  (:import
    [java.time Instant]
    [java.util Date]))


(defn- resource-eid [db type id]
  (and (util/cached-entity db (keyword type))
       (:db/id (util/resource db type id))))


(defn- transactions
  "Returns a seq of all transactions on resource with `eid`. Newest first."
  [db eid]
  (into
    []
    (comp
      (filter :added)
      (take 50)
      (map #(d/entity db (:tx %))))
    (d/datoms (d/history db) :eavt eid :version)))


(defn- method [version]
  (cond
    (zero? version) "POST"
    (util/deleted? version) "DELETE"
    :else "PUT"))


(defn- url [base-uri type id version]
  (cond-> (str base-uri "/" type)
    (not (zero? version))
    (str "/" id)))


(defn status [version]
  (cond
    (zero? version) "201"
    (util/deleted? version) "204"
    ;; TODO: differentiate between PUT 201 und 200
    :else "200"))


(defn- build-entry [base-uri db type id eid transaction]
  (let [t (d/tx->t (:db/id transaction))
        db (d/as-of db t)
        resource (d/entity db eid)]
    (cond->
      {:fullUrl (str base-uri "/" type "/" id)
       :request
       {:method (method (:version resource))
        :url (url base-uri type id (:version resource))}
       :response
       {:status (status (:version resource))
        :etag (str "W/\"" t "\"")
        :lastModified (str (util/tx-instant transaction))}}
      (not (util/deleted? (:version resource)))
      (assoc :resource (pull/pull-resource* db type resource)))))


(defn- build-response [base-uri db type id eid transactions]
  (ring/response
    {:resourceType "Bundle"
     :type "history"
     :entry (mapv #(build-entry base-uri db type id eid %) transactions)}))


(defn- since [db {since "_since"}]
  (cond-> db
    since
    (d/since (Date/from (Instant/parse since)))))


(defn handler-intern [base-uri conn]
  (fn [{{:keys [type id]} :route-params :keys [query-params]}]
    (let [db (d/db conn)]
      (if-let [eid (resource-eid db type id)]
        (let [transactions (transactions (since db query-params) eid)]
          (build-response base-uri db type id eid transactions))
        (handler-util/error-response
          {::anom/category ::anom/not-found
           :fhir/issue "not-found"})))))


(s/def :handler.fhir/read fn?)


(s/fdef handler
  :args (s/cat :base-uri string? :conn ::ds/conn)
  :ret :handler.fhir/read)

(defn handler
  ""
  [base-uri conn]
  (-> (handler-intern base-uri conn)
      (wrap-params)
      (wrap-json)
      (wrap-exception)))
