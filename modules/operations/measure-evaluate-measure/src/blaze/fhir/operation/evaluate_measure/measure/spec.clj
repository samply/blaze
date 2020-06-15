(ns blaze.fhir.operation.evaluate-measure.measure.spec
  (:require
    [clojure.spec.alpha :as s]))


(s/def :blaze.fhir.operation.evaluate-measure/report-type
  #{"subject" "subject-list" "population"})
