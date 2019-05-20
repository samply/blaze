(ns blaze.handler.fhir.read
  "FHIR read interaction.

  https://www.hl7.org/fhir/http.html#read"
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
    [ring.util.response :as ring])
  (:import
    [java.time ZonedDateTime ZoneId]
    [java.time.format DateTimeFormatter]))


(def ^:private gmt (ZoneId/of "GMT"))


(defn- last-modified [resource]
  (->> (ZonedDateTime/ofInstant (:last-transaction-instant (meta resource)) gmt)
       (.format DateTimeFormatter/RFC_1123_DATE_TIME)))


(defn- etag [resource]
  (str "W/\"" (:version-id (meta resource)) "\""))


(defn- pull-resource [db type id]
  (and (util/cached-entity db (keyword type))
       (pull/pull-resource db type id)))


(defn handler-intern [conn]
  (fn [{{:keys [type id]} :route-params}]
    (if-let [resource (pull-resource (d/db conn) type id)]
      (if (:deleted (meta resource))
        (-> (handler-util/operation-outcome
              {:fhir/issue "deleted"})
            (ring/response)
            (ring/status 410)
            (ring/header "Last-Modified" (last-modified resource))
            (ring/header "ETag" (etag resource)))
        (-> (ring/response resource)
            (ring/header "Last-Modified" (last-modified resource))
            (ring/header "ETag" (etag resource))))
      (handler-util/error-response
        {::anom/category ::anom/not-found
         :fhir/issue "not-found"}))))


(s/def :handler.fhir/read fn?)


(s/fdef handler
  :args (s/cat :conn ::ds/conn)
  :ret :handler.fhir/read)

(defn handler
  ""
  [conn]
  (-> (handler-intern conn)
      (wrap-json)
      (wrap-exception)))
