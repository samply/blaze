(ns blaze.interaction.read
  "FHIR read interaction.

  https://www.hl7.org/fhir/http.html#read"
  (:require
    [blaze.anomaly :refer [ex-anom]]
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
    [blaze.db.spec]
    [blaze.handler.util :as handler-util]
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


(defn- etag [{:blaze.db/keys [t]}]
  (str "W/\"" t "\""))


(defn- db [node vid type id]
  (cond
    (and vid (re-matches #"\d+" vid))
    (let [vid (Long/parseLong vid)]
      (-> (d/sync node vid) (ac/then-apply #(d/as-of % vid))))

    vid
    (ac/failed-future
      (ex-anom
        {::anom/category ::anom/not-found
         ::anom/message (format "Resource `/%s/%s` with versionId `%s` was not found." type id vid)
         :fhir/issue "not-found"}))

    :else
    (ac/completed-future (d/db node))))


(defn- handler-intern [node]
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id vid]} :path-params}]
    (-> (db node vid type id)
        (ac/then-compose
          (fn [db]
            (if-let [{:keys [op t] :as handle} (d/resource-handle db type id)]
              (if (identical? :delete op)
                (let [tx (d/tx db t)]
                  (-> (handler-util/operation-outcome
                        {:fhir/issue "deleted"})
                      (ring/response)
                      (ring/status 410)
                      (ring/header "Last-Modified" (last-modified tx))
                      (ring/header "ETag" (etag tx))
                      (ac/completed-future)))
                (-> (d/pull node handle)
                    (ac/then-apply
                      (fn [resource]
                        (let [{:blaze.db/keys [tx]} (meta resource)]
                          (-> (ring/response resource)
                              (ring/header "Last-Modified" (last-modified tx))
                              (ring/header "ETag" (etag tx))))))))
              (-> (handler-util/error-response
                    {::anom/category ::anom/not-found
                     :fhir/issue "not-found"
                     ::anom/message (format "Resource `/%s/%s` not found" type id)})
                  (ac/completed-future)))))
        (ac/exceptionally handler-util/error-response))))


(defn wrap-interaction-name [handler]
  (fn [{{:keys [vid]} :path-params :as request}]
    (-> (handler request)
        (ac/then-apply #(assoc % :fhir/interaction-name (if vid "vread" "read"))))))


(defn handler [node]
  (-> (handler-intern node)
      (wrap-interaction-name)
      (wrap-observe-request-duration)))


(defmethod ig/pre-init-spec :blaze.interaction/read [_]
  (s/keys :req-un [:blaze.db/node]))


(defmethod ig/init-key :blaze.interaction/read
  [_ {:keys [node]}]
  (log/info "Init FHIR read interaction handler")
  (handler node))
