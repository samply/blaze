(ns blaze.fhir.operation.evaluate-measure.measure.spec
  (:require
    [clojure.spec.alpha :as s]))


(s/def :blaze.fhir.operation.evaluate-measure/report-type
  #{"subject" "subject-list" "population"})


(s/def :blaze.fhir.operation.evaluate-measure/subject-ref
  (s/or :id :blaze.resource/id
        :local-ref (s/tuple :fhir.resource/type :blaze.resource/id)))
