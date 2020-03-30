(ns blaze.fhir.spec-spec
  (:require
    [blaze.fhir.spec :as fhir-spec]
    [clojure.spec.alpha :as s]))


(s/fdef fhir-spec/type-exists?
  :args (s/cat :type string?))


(s/fdef fhir-spec/child-specs
  :args (s/cat :spec keyword?))
