(ns blaze.fhir.operation.evaluate-measure.measure.population-spec
  (:require
    [blaze.fhir.operation.evaluate-measure.measure.population :as population]
    [blaze.fhir.operation.evaluate-measure.measure.population.spec]
    [clojure.spec.alpha :as s]))


(s/fdef population/evaluate
  :args (s/cat :context ::population/context :idx nat-int? :population map?))
