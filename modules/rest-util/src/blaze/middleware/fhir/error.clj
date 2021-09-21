(ns blaze.middleware.fhir.error
  "Converts exceptional completed futures into an OperationOutcome response."
  (:require
    [blaze.async.comp :as ac]
    [blaze.handler.util :as handler-util]))


(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))
