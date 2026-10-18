(ns blaze.middleware.fhir.id
  "This middleware validates the resource id given in the path param `id`.

  It has to be used on all routes containing an `{id}` path param, so that
  only valid resource ids reach the database layer."
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(defn- invalid-id-anom [id]
  (ba/incorrect
   (format "Resource id `%s` is invalid." id)
   :fhir/issue "value"
   :fhir/operation-outcome "MSG_ID_INVALID"))

(defn wrap-invalid-id
  "Returns a handler that completes with an incorrect anomaly if the path param
  `id` isn't a valid resource id. Calls `handler` otherwise."
  [handler]
  (fn [{{:keys [id]} :path-params :as request}]
    (if (s/valid? :blaze.resource/id id)
      (handler request)
      (ac/completed-future (invalid-id-anom id)))))
