(ns blaze.fhir.operation.evaluate-measure.measure.population-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.operation.evaluate-measure.measure.population :as population]
   [blaze.fhir.operation.evaluate-measure.measure.population.spec]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(s/fdef population/evaluate
  :args (s/cat :context ::population/context :idx nat-int? :population map?)
  :ret ac/completable-future?)

(s/fdef population/population
  :args (s/cat :code :fhir/CodeableConcept :count int?)
  :ret :fhir.Measure.group/population)
