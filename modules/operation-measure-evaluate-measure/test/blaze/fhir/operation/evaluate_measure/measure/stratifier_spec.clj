(ns blaze.fhir.operation.evaluate-measure.measure.stratifier-spec
  (:require
    [blaze.fhir.operation.evaluate-measure.cql-spec]
    [blaze.fhir.operation.evaluate-measure.measure :as-alias measure]
    [blaze.fhir.operation.evaluate-measure.measure.spec]
    [blaze.fhir.operation.evaluate-measure.measure.stratifier :as stratifier]
    [blaze.fhir.operation.evaluate-measure.measure.util-spec]
    [clojure.spec.alpha :as s]))


(s/def ::handles
  (s/coll-of ::measure/handles))


(s/def ::evaluated-populations
  (s/keys :req-un [::handles]))


(s/def ::context
  (s/keys :req-un [::measure/report-type]))


(s/fdef stratifier/evaluate
  :args (s/cat :context ::context :evaluated-populations ::evaluated-populations
               :stratifier map?))
