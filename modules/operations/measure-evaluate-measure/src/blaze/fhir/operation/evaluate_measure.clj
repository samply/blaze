(ns blaze.fhir.operation.evaluate-measure
  "Main entry point into the $evaluate-measure operation."
  (:require
    [blaze.executors :as ex :refer [executor?]]
    [blaze.fhir.operation.evaluate-measure.handler.impl :as impl]
    [blaze.fhir.operation.evaluate-measure.measure :as measure]
    [blaze.fhir.operation.evaluate-measure.middleware.params
     :refer [wrap-coerce-params]]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.module :refer [reg-collector]]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [ring.middleware.params :refer [wrap-params]]
    [taoensso.timbre :as log])
  (:import
    [java.time Clock]))


(set! *warn-on-reflection* true)


(defn handler [clock conn executor]
  (-> (impl/handler clock conn executor)
      (wrap-coerce-params)
      (wrap-params)
      (wrap-observe-request-duration "operation-evaluate-measure")))


(s/def ::executor
  executor?)


(defmethod ig/pre-init-spec ::handler [_]
  (s/keys :req-un [:blaze.db/node ::executor]))


(defmethod ig/init-key ::handler
  [_ {:keys [node executor]}]
  (log/info "Init FHIR $evaluate-measure operation handler")
  (handler (Clock/systemDefaultZone) node executor))


(defn- executor-init-msg []
  (format "Init FHIR $evaluate-measure operation executor with %d threads"
          (.availableProcessors (Runtime/getRuntime))))


(defmethod ig/init-key ::executor
  [_ _]
  (log/info (executor-init-msg))
  (ex/cpu-bound-pool "blaze-evaluate-measure-operation-%d"))


(derive ::executor :blaze.metrics/thread-pool-executor)


(reg-collector ::compile-duration-seconds
  measure/compile-duration-seconds)


(reg-collector ::evaluate-duration-seconds
  measure/evaluate-duration-seconds)
