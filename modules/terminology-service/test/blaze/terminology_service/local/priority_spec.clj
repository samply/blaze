(ns blaze.terminology-service.local.priority-spec
  (:require
   [blaze.fhir.spec.spec]
   [blaze.terminology-service.local.priority :as priority]
   [clojure.spec.alpha :as s]))

(s/fdef priority/sort-by-priority
  :args (s/cat :resources (s/coll-of (s/or :code-system :fhir/CodeSystem :value-set :fhir/ValueSet)))
  :ret (s/coll-of (s/or :code-system :fhir/CodeSystem :value-set :fhir/ValueSet)))
