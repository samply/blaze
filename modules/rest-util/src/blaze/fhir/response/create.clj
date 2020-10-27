(ns blaze.fhir.response.create
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
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


(defn- build-created-response-msg [type id vid]
  (format "build-created-response of %s/%s with vid = %s" type id vid))


(defn build-created-response
  "Builds a 201 Created response of resource with `type` and `id` from `db`.

  The `router` is used to generate the absolute URL of the Location header and
  `return-preference` is used to decide which type of body is returned."
  [router return-preference db type id]
  (let [handle (d/resource-handle db type id)
        tx (d/tx db (d/last-updated-t handle))
        vid (str (:blaze.db/t tx))]
    (log/trace (build-created-response-msg type id vid))
    (-> (cond
          (= "minimal" return-preference)
          (ac/completed-future nil)
          (= "OperationOutcome" return-preference)
          (ac/completed-future {:fhir/type :fhir/OperationOutcome})
          :else
          (d/pull db handle))
        (ac/then-apply
          (fn [body]
            (-> (ring/created
                  (fhir-util/versioned-instance-url router type id vid)
                  body)
                (ring/header "Last-Modified" (last-modified tx))
                (ring/header "ETag" (str "W/\"" vid "\""))))))))
