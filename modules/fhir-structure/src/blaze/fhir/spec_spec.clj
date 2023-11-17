(ns blaze.fhir.spec-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.spec]
   [clojure.alpha.spec :as s2]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef fhir-spec/type-exists?
  :args (s/cat :type string?))

(s/def :blaze.fhir.spec/choices-spec
  (s/spec (s/cat :op #(= `s2/or %)
                 :choices (s/* (s/cat :key keyword? :spec some?)))))

(s/fdef fhir-spec/primitive?
  :args (s/cat :spec any?)
  :ret boolean?)

(s/fdef fhir-spec/parse-json
  :args (s/cat :source some?)
  :ret (s/or :data some? :anomaly ::anom/anomaly))

(s/fdef fhir-spec/conform-json
  :args (s/cat :x any?)
  :ret (s/or :resource :blaze/resource :anomaly ::anom/anomaly))

(s/fdef fhir-spec/unform-json
  :args (s/cat :resource any?)
  :ret bytes?)

(s/fdef fhir-spec/parse-cbor
  :args (s/cat :source some?)
  :ret (s/or :data some? :anomaly ::anom/anomaly))

(s/fdef fhir-spec/conform-cbor
  :args (s/cat :x any?)
  :ret (s/or :resource :blaze/resource :anomaly ::anom/anomaly))

(s/fdef fhir-spec/unform-cbor
  :args (s/cat :resource any?)
  :ret bytes?)

(s/fdef fhir-spec/conform-xml
  :args (s/cat :x any?)
  :ret (s/or :resource :blaze/resource :anomaly ::anom/anomaly))

(s/fdef fhir-spec/unform-xml
  :args (s/cat :resource any?))

(s/fdef fhir-spec/fhir-type
  :args (s/cat :x any?)
  :ret (s/nilable :fhir/type))
