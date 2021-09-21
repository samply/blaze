(ns blaze.fhir.operation.evaluate-measure.spec
  (:require
    [blaze.executors :as ex]
    [clojure.spec.alpha :as s]))


(s/def :blaze.fhir.operation.evaluate-measure/executor
  ex/executor?)
