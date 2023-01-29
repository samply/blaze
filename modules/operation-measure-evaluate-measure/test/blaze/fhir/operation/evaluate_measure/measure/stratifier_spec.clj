(ns blaze.fhir.operation.evaluate-measure.measure.stratifier-spec
  (:require
    [blaze.fhir.operation.evaluate-measure.cql-spec]
    [blaze.fhir.operation.evaluate-measure.measure.spec]
    [blaze.fhir.operation.evaluate-measure.measure.stratifier :as stratifier]
    [blaze.fhir.operation.evaluate-measure.measure.stratifier.spec]
    [blaze.fhir.operation.evaluate-measure.measure.util-spec]
    [clojure.spec.alpha :as s]))


(s/fdef stratifier/evaluate
  :args (s/cat :context ::stratifier/context
               :evaluated-populations ::stratifier/evaluated-populations
               :stratifier map?))
