(ns blaze.terminology-service.local.code-system.loinc.spec
  (:require
   [blaze.fhir.spec]
   [clojure.spec.alpha :as s]))

(s/def :loinc/code-systems
  (s/coll-of :fhir/CodeSystem))

(s/def :loinc/concept-index
  map?)

(s/def :loinc/context
  (s/keys :req-un [:loinc/code-systems :loinc/concept-index]))
