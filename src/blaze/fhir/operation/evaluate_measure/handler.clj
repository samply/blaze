(ns blaze.fhir.operation.evaluate-measure.handler
  "Main entry point into the Evaluate-Measure-handler."
  (:require
    [blaze.executors :as ex]
    [blaze.fhir.operation.evaluate-measure.handler.impl :as impl]
    [blaze.fhir.operation.evaluate-measure.middleware.params :refer [wrap-coerce-params]]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.terminology-service :refer [term-service?]]
    [clojure.spec.alpha :as s]
    [datomic-spec.core :as ds]
    [ring.middleware.params :refer [wrap-params]])
  (:import
    [java.time Clock]))


(s/def :handler.fhir.operation/evaluate-measure fn?)


(s/fdef handler
  :args (s/cat :clock #(instance? Clock %)
               :conn ::ds/conn
               :term-service term-service?
               :executor ex/executor?)
  :ret :handler.fhir.operation/evaluate-measure)

(defn handler
  ""
  [clock conn term-service executor]
  (-> (impl/handler clock conn term-service executor)
      (wrap-coerce-params)
      (wrap-params)
      (wrap-observe-request-duration "operation-evaluate-measure")))
