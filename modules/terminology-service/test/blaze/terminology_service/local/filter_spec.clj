(ns blaze.terminology-service.local.filter-spec
  (:require
   [blaze.fhir.spec.spec]
   [blaze.terminology-service.local.filter :as filter]
   [clojure.spec.alpha :as s]))

(s/fdef filter/expand-code-system
  :args (s/cat :code-system :fhir/CodeSystem
               :filters (s/coll-of :fhir.ValueSet.compose.include/filter))
  :ret (s/coll-of :fhir.ValueSet.expansion/contains))
