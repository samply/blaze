(ns blaze.fhir.operation.evaluate-measure.spec
  (:require
   [blaze.executors :as ex]
   [blaze.fhir.operation.evaluate-measure :as-alias measure]
   [clojure.spec.alpha :as s]
   [java-time.api :as time]))

(s/def ::measure/executor
  ex/executor?)

(s/def ::measure/num-threads
  pos-int?)

(s/def ::measure/timeout
  time/duration?)

(s/def :blaze.fhir.operation.evaluate-measure.timeout/millis
  nat-int?)
