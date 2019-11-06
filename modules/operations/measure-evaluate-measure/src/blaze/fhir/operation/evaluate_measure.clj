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
    :transaction-executor executor?
    :conn ::ds/conn
    :term-service term-service?
    :executor executor?)
  :ret :handler.fhir.operation/evaluate-measure)

(defn handler
  ""
  [clock transaction-executor conn term-service executor]
  (-> (impl/handler clock transaction-executor conn term-service executor)
      (wrap-coerce-params)
      (wrap-params)
      (wrap-observe-request-duration "operation-evaluate-measure")))


(s/def ::clock
  #(instance? Clock %))


(s/def ::term-service
  term-service?)


(s/def ::executor
  executor?)


(defmethod ig/pre-init-spec ::handler [_]
  (s/keys :req-un [::clock ::term-service ::executor]))


(defmethod ig/init-key ::handler
  [_
   {:database/keys [transaction-executor conn]
    :keys [clock term-service executor]}]
  (log/info "Init FHIR $evaluate-measure operation handler")
  (handler clock transaction-executor conn term-service executor))


(defmethod ig/init-key ::executor
  [_ _]
  (log/info "Init FHIR $evaluate-measure operation executor")
  (ex/cpu-bound-pool "evaluate-measure-operation-%d"))


(derive ::executor :blaze.metrics/thread-pool-executor)


(reg-collector ::compile-duration-seconds
  measure/compile-duration-seconds)


(reg-collector ::evaluate-duration-seconds
  measure/evaluate-duration-seconds)
