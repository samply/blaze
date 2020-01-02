(ns blaze.fhir.response.create
  (:require
    [blaze.db.api :as d]
    [blaze.handler.fhir.util :as fhir-util]
    [ring.util.response :as ring])
  (:import
    [java.time ZonedDateTime ZoneId]
    [java.time.format DateTimeFormatter]))


(set! *warn-on-reflection* true)


(def ^:private gmt (ZoneId/of "GMT"))


(defn- last-modified [{:blaze.db.tx/keys [instant]}]
  (->> (ZonedDateTime/ofInstant instant gmt)
       (.format DateTimeFormatter/RFC_1123_DATE_TIME)))


(defn build-created-response
  "Builds a 201 Created response of resource with `type` and `id` from `db`.

  The `router` is used to generate the absolute URL of the Location header and
  `return-preference` is used to decide which type of body is returned."
  [router return-preference db type id]
  (let [resource (d/resource db type id)
        {:blaze.db/keys [tx]} (meta resource)
        vid (-> resource :meta :versionId)]
    (-> (ring/created
          (fhir-util/versioned-instance-url router type id vid)
          (cond
            (= "minimal" return-preference)
            nil
            (= "OperationOutcome" return-preference)
            {:resourceType "OperationOutcome"}
            :else
            resource))
        (ring/header "Last-Modified" (last-modified tx))
        (ring/header "ETag" (str "W/\"" vid "\"")))))
