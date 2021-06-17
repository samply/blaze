(ns blaze.fhir.response.create
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
    [blaze.fhir.spec.type :as type]
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


(defn- location-header [response base-url router type id vid]
  (ring/header
    response
    "Location"
    (fhir-util/versioned-instance-url base-url router type id vid)))


(defn build-response
  [base-url router return-preference db old-handle {:keys [id] :as new-handle}]
  (let [type (name (type/type new-handle))
        tx (d/tx db (:t new-handle))
        vid (str (:blaze.db/t tx))
        created (or (nil? old-handle) (identical? :delete (:op old-handle)))]
    (log/trace (format "build-response of %s/%s with vid = %s" type id vid))
    (-> (cond
          (= "minimal" return-preference)
          (ac/completed-future nil)
          (= "OperationOutcome" return-preference)
          (ac/completed-future {:fhir/type :fhir/OperationOutcome})
          :else
          (d/pull db new-handle))
        (ac/then-apply
          (fn [body]
            (cond->
              (-> (ring/response body)
                  (ring/status (if created 201 200))
                  (ring/header "Last-Modified" (last-modified tx))
                  (ring/header "ETag" (str "W/\"" vid "\"")))
              created
              (location-header base-url router type id vid)))))))
