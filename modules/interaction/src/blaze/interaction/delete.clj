(ns blaze.interaction.delete
  "FHIR delete interaction.

  https://www.hl7.org/fhir/http.html#delete"
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.delete.spec]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
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


(defn- handler-intern [node executor]
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params}]
    (let [db (d/db node)]
      (if-let [{:keys [op t]} (d/resource-handle db type id)]
        (if (identical? :delete op)
          (-> (build-response* (d/tx db t))
              (ac/completed-future))
          (-> (d/transact node [[:delete type id]])
              ;; it's important to switch to the transaction executor here,
              ;; because otherwise the central indexing thread would execute
              ;; response building.
              (ac/then-apply-async build-response executor)))
        (ac/completed-future
          (handler-util/error-response
            {::anom/category ::anom/not-found
             :fhir/issue "not-found"}))))))


(defn handler [node executor]
  (-> (handler-intern node executor)
      (wrap-observe-request-duration "delete")))


(defmethod ig/pre-init-spec :blaze.interaction/delete [_]
  (s/keys :req-un [:blaze.db/node ::executor]))


(defmethod ig/init-key :blaze.interaction/delete
  [_ {:keys [node executor]}]
  (log/info "Init FHIR delete interaction handler")
  (handler node executor))
