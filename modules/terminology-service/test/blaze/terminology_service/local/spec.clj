(ns blaze.terminology-service.local.spec
  (:require
   [blaze.fhir.spec]
   [blaze.terminology-service.local :as-alias local]
   [clojure.spec.alpha :as s]))

(s/def ::local/tx-resource
  (s/or :code-system :fhir/CodeSystem :value-set :fhir/ValueSet))

(s/def ::local/tx-resources
  (s/coll-of ::local/tx-resource))
