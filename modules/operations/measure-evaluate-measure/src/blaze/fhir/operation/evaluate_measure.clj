(ns blaze.fhir.operation.evaluate-measure
  "Main entry point into the $evaluate-measure operation."
  (:require
    [blaze.executors :as ex :refer [executor?]]
    [blaze.fhir.operation.evaluate-measure.handler.impl :as impl]
    [blaze.fhir.operation.evaluate-measure.middleware.params :refer [wrap-coerce-params]]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.terminology-service :refer [term-service?]]
    [clojure.spec.alpha :as s]
    [datomic-spec.core :as ds]
    [integrant.core :as ig]
    [ring.middleware.params :refer [wrap-params]]
    [taoensso.timbre :as log])
  (:import
    [java.time Clock]))


(s/def :handler.fhir.operation/evaluate-measure fn?)


(s/fdef handler
  :args
  (s/cat
    :clock #(instance? Clock %)
    :conn ::ds/conn
    :term-service term-service?
    :executor executor?)
  :ret :handler.fhir.operation/evaluate-measure)

(defn handler
  ""
  [clock conn term-service executor]
  (-> (impl/handler clock conn term-service executor)
      (wrap-coerce-params)
      (wrap-params)
      (wrap-observe-request-duration "operation-evaluate-measure")))


(defmethod ig/init-key :blaze.fhir.operation.evaluate-measure/handler
  [_ {:keys [clock term-service executor] :database/keys [conn]}]
  (log/debug "Init FHIR $evaluate-measure operation handler")
  (handler clock conn term-service executor))


(defmethod ig/init-key :blaze.fhir.operation.evaluate-measure/executor
  [_ _]
  (ex/cpu-bound-pool "evaluate-measure-operation-%d"))
