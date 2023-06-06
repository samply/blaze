(ns blaze.interaction.delete
  "FHIR delete interaction.

  https://www.hl7.org/fhir/http.html#delete"
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
    [blaze.db.spec]
    [blaze.interaction.delete.spec]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [taoensso.timbre :as log])
  (:import
    [java.time ZoneId ZonedDateTime]
    [java.time.format DateTimeFormatter]))


(set! *warn-on-reflection* true)


(def ^:private gmt (ZoneId/of "GMT"))


(defn- last-modified [{:blaze.db.tx/keys [instant]}]
  (->> (ZonedDateTime/ofInstant instant gmt)
       (.format DateTimeFormatter/RFC_1123_DATE_TIME)))


(defn- build-response [db]
  (let [tx (d/tx db (d/basis-t db))]
    (-> (ring/response nil)
        (ring/status 204)
        (ring/header "Last-Modified" (last-modified tx))
        (ring/header "ETag" (str "W/\"" (:blaze.db/t tx) "\"")))))


(defmethod ig/pre-init-spec :blaze.interaction/delete [_]
  (s/keys :req-un [:blaze.db/node ::executor]))


(defmethod ig/init-key :blaze.interaction/delete [_ {:keys [node executor]}]
  (log/info "Init FHIR delete interaction handler")
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params}]
    (-> (d/transact node [[:delete type id]])
        ;; it's important to switch to the executor here,
        ;; because otherwise the central indexing thread would execute
        ;; response building.
        (ac/then-apply-async build-response executor))))
