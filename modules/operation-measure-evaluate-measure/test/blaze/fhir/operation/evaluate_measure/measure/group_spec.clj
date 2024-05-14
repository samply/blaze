(ns blaze.fhir.operation.evaluate-measure.measure.group-spec
  (:require
   [blaze.fhir.operation.evaluate-measure.measure.group :as group]
   [blaze.fhir.spec.spec]
   [blaze.luid :as-alias luid]
   [blaze.luid.spec]
   [clojure.spec.alpha :as s]))

(s/fdef group/combine-op-count
  :args (s/cat :luid-generator ::luid/generator
               :code (s/nilable :fhir/CodeableConcept))
  :ret fn?)

(s/fdef group/combine-op-count-stratifier
  :args (s/cat :luid-generator ::luid/generator
               :code (s/nilable :fhir/CodeableConcept)
               :stratifiers (s/coll-of :fhir.Measure.group/stratifier))
  :ret fn?)

(s/fdef group/combine-op-subject-list
  :args (s/cat :luid-generator ::luid/generator
               :code (s/nilable :fhir/CodeableConcept))
  :ret fn?)

(s/fdef group/combine-op-subject-list-stratifier
  :args (s/cat :luid-generator ::luid/generator
               :code (s/nilable :fhir/CodeableConcept)
               :stratifiers (s/coll-of :fhir.Measure.group/stratifier))
  :ret fn?)
