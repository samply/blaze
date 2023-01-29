(ns blaze.fhir.operation.evaluate-measure.measure.stratifier.spec
  (:require
    [blaze.fhir.operation.evaluate-measure.cql :as-alias cql]
    [blaze.fhir.operation.evaluate-measure.cql.spec]
    [blaze.fhir.operation.evaluate-measure.measure :as-alias measure]
    [blaze.fhir.operation.evaluate-measure.measure.stratifier :as-alias stratifier]
    [clojure.spec.alpha :as s]))


(s/def ::stratifier/handles
  (s/coll-of ::measure/handles))


(s/def ::stratifier/evaluated-populations
  (s/keys :req-un [::handles]))


(s/def ::stratifier/context
  (s/merge ::cql/context (s/keys :req-un [::measure/report-type])))
