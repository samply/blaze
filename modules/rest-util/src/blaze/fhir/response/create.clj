(ns blaze.fhir.response.create
  (:require
    [blaze.async.comp :as ac :refer [do-sync]]
    [blaze.db.api :as d]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.handler.fhir.util :as fhir-util]
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


(defn- location-header [response context type id vid]
  (let [url (fhir-util/versioned-instance-url context type id vid)]
    (ring/header response "Location" url)))


(defn- body
  [{:blaze/keys [db] return-preference :blaze.preference/return} new-handle]
  (cond
    (identical? :blaze.preference.return/minimal return-preference)
    (ac/completed-future nil)
    (identical? :blaze.preference.return/OperationOutcome return-preference)
    (ac/completed-future {:fhir/type :fhir/OperationOutcome})
    :else
    (d/pull db new-handle)))


(defn build-response
  [{:blaze/keys [db] :as context} old-handle {:keys [id] :as new-handle}]
  (let [type (name (fhir-spec/fhir-type new-handle))
        tx (d/tx db (:t new-handle))
        vid (str (:blaze.db/t tx))
        created (or (nil? old-handle) (identical? :delete (:op old-handle)))]
    (log/trace (format "build-response of %s/%s with vid = %s" type id vid))
    (do-sync [body (body context new-handle)]
      (cond->
        (-> (ring/response body)
            (ring/status (if created 201 200))
            (ring/header "Last-Modified" (last-modified tx))
            (ring/header "ETag" (str "W/\"" vid "\"")))
        created
        (location-header context type id vid)))))
