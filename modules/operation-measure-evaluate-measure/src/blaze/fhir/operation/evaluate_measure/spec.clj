(ns blaze.fhir.operation.evaluate-measure.spec
  (:require
   [blaze.executors :as ex]
   [blaze.fhir.operation.evaluate-measure :as-alias evaluate-measure]
   [clojure.spec.alpha :as s]
   [java-time.api :as time]))

(s/def ::evaluate-measure/executor
  ex/executor?)

(s/def ::evaluate-measure/max-size
  nat-int?)

(s/def ::evaluate-measure/timeout
  time/duration?)

(s/def :blaze.fhir.operation.evaluate-measure.timeout/millis
  nat-int?)
