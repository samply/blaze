(ns blaze.middleware.fhir.error
  "Converts exceptional completed futures into an OperationOutcome response."
  (:require
    [blaze.handler.util :as handler-util]))


(defn wrap-error [handler]
  (fn [request respond _]
    (handler request respond #(respond (handler-util/error-response %)))))
