(ns blaze.middleware.fhir.id
  "This middleware validates the resource id given in the path param `id` and
  the version id given in the path param `vid`, if any.

  It has to be used on all routes containing an `{id}` path param, so that
  only valid resource ids and version ids reach the database layer."
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(defn- invalid-id-anom [kind id]
  (ba/incorrect
   (format "%s id `%s` is invalid." kind id)
   :fhir/issue "value"
   :fhir/operation-outcome "MSG_ID_INVALID"))

(defn- valid-id? [id]
  (s/valid? :blaze.resource/id id))

(defn wrap-invalid-id
  "Returns a handler that completes with an incorrect anomaly if the path param
  `id` isn't a valid resource id or the path param `vid`, if present, isn't a
  valid version id. Calls `handler` otherwise.

  Version ids are FHIR ids like resource ids."
  [handler]
  (fn [{{:keys [id vid]} :path-params :as request}]
    (cond
      (not (valid-id? id))
      (ac/completed-future (invalid-id-anom "Resource" id))

      (and vid (not (valid-id? vid)))
      (ac/completed-future (invalid-id-anom "Version" vid))

      :else
      (handler request))))
