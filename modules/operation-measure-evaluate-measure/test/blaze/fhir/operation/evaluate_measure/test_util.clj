(ns blaze.fhir.operation.evaluate-measure.test-util
  (:require
    [blaze.async.comp :as ac]
    [blaze.handler.util :as handler-util]))


(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))


(defn extract-txs-body [more]
  (if (vector? (first more))
    [(first more) (next more)]
    [[] more]))
