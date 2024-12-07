(ns blaze.terminology-service.local.concept-spec
  (:require
   [blaze.fhir.spec.spec]
   [blaze.terminology-service.local.concept :as concept]
   [clojure.spec.alpha :as s]))

(s/fdef concept/expand-code-system
  :args (s/cat :code-system :fhir/CodeSystem
               :value-set-concepts (s/coll-of :fhir.ValueSet.compose.include/concept))
  :ret (s/coll-of :fhir.ValueSet.expansion/contains))
