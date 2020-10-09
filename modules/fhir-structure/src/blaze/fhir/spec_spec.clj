(ns blaze.fhir.spec-spec
  (:require
    [blaze.fhir.spec :as fhir-spec]
    [clojure.alpha.spec :as s2]
    [clojure.spec.alpha :as s]))


(s/fdef fhir-spec/type-exists?
  :args (s/cat :type string?))


(s/def :blaze.fhir.spec/choices-spec
  (s/spec (s/cat :op #(= `s2/or %)
                 :choices (s/* (s/cat :key keyword? :spec some?)))))


(s/fdef fhir-spec/primitive?
  :args (s/cat :spec any?)
  :ret boolean?)


(s/fdef fhir-spec/unform-json
  :args (s/cat :resource :blaze/resource))


(s/fdef fhir-spec/unform-cbor
  :args (s/cat :resource :blaze/resource))


(s/fdef fhir-spec/unform-xml
  :args (s/cat :resource :blaze/resource))


(s/fdef fhir-spec/fhir-type
  :args (s/cat :x any?)
  :ret (s/nilable boolean?))
