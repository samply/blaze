(ns blaze.fhir.response.create
  (:require
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.handler.fhir.util :as fhir-util]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn location-header [response context type id vid]
  (->> (fhir-util/versioned-instance-url context type id vid)
       (ring/header response "Location")))

(defn- body
  [{:blaze/keys [db] return-preference :blaze.preference/return} new-handle]
  (cond
    (identical? :blaze.preference.return/minimal return-preference)
    (ac/completed-future nil)
    (identical? :blaze.preference.return/OperationOutcome return-preference)
    (ac/completed-future {:fhir/type :fhir/OperationOutcome})
    :else
    (d/pull db new-handle)))

(defn- keep? [[op]]
  (identical? :keep op))

(defn build-response
  [{:blaze/keys [db] :as context} tx-op old-handle {:keys [id] :as new-handle}]
  (let [type (name (fhir-spec/fhir-type new-handle))
        tx (d/tx db (:t new-handle))
        vid (str (:blaze.db/t tx))
        created (and (not (keep? tx-op))
                     (or (nil? old-handle) (identical? :delete (:op old-handle))))]
    (log/trace (format "build-response of %s/%s with vid = %s" type id vid))
    (do-sync [body (body context new-handle)]
      (cond->
       (-> (ring/response body)
           (ring/status (if created 201 200))
           (ring/header "Last-Modified" (fhir-util/last-modified tx))
           (ring/header "ETag" (str "W/\"" vid "\"")))
        created
        (location-header context type id vid)))))
