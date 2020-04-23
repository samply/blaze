(ns blaze.interaction.delete
  "FHIR delete interaction.

  https://www.hl7.org/fhir/http.html#delete"
  (:require
    [blaze.db.api :as d]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [manifold.deferred :as md]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [taoensso.timbre :as log])
  (:import
    [java.time ZonedDateTime ZoneId]
    [java.time.format DateTimeFormatter]))


(set! *warn-on-reflection* true)


(def ^:private gmt (ZoneId/of "GMT"))


(defn- last-modified [{:blaze.db.tx/keys [instant]}]
  (->> (ZonedDateTime/ofInstant instant gmt)
       (.format DateTimeFormatter/RFC_1123_DATE_TIME)))


(defn- build-response* [tx]
  (-> (ring/response nil)
      (ring/status 204)
      (ring/header "Last-Modified" (last-modified tx))
      (ring/header "ETag" (str "W/\"" (:blaze.db/t tx) "\""))))


(defn- build-response [db]
  (build-response* (d/tx db (d/basis-t db))))


(defn- handler-intern [node]
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params}]
    (log/debug (format "DELETE [base]/%s/%s" type id))
    (if-let [{:blaze.db/keys [op tx]} (meta (d/resource (d/db node) type id))]
      (if (identical? :delete op)
        (build-response* tx)
        (-> (d/submit-tx node [[:delete type id]])
            (md/chain' build-response)))
      (handler-util/error-response
        {::anom/category ::anom/not-found
         :fhir/issue "not-found"}))))


(defn handler [node]
  (-> (handler-intern node)
      (wrap-observe-request-duration "delete")))


(defmethod ig/init-key :blaze.interaction/delete
  [_ {:keys [node]}]
  (log/info "Init FHIR delete interaction handler")
  (handler node))
