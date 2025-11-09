(ns blaze.fhir.spec-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.fhir.parsing-context.spec]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.spec]
   [blaze.fhir.writing-context.spec]
   [blaze.util-spec]
   [clojure.alpha.spec :as s2]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom])
  (:import
   [java.io OutputStream]))

(s/fdef fhir-spec/type-exists?
  :args (s/cat :type string?)
  :ret boolean?)

(s/def :blaze.fhir.spec/choices-spec
  (s/spec (s/cat :op #(= `s2/or %)
                 :choices (s/* (s/cat :key keyword? :spec some?)))))

(s/fdef fhir-spec/fhir-type
  :args (s/cat :x any?)
  :ret (s/nilable :fhir/type))

(s/fdef fhir-spec/primitive?
  :args (s/cat :spec any?)
  :ret boolean?)

(s/fdef fhir-spec/primitive-val?
  :args (s/cat :spec any?)
  :ret boolean?)

(s/fdef fhir-spec/parse-json
  :args (s/cat :context :blaze.fhir/parsing-context :type (s/? string?)
               :source some?)
  :ret (s/or :resource :fhir/Resource :anomaly ::anom/anomaly))

(s/fdef fhir-spec/parse-cbor
  :args (s/cat :context :blaze.fhir/parsing-context :type string?
               :source bytes? :variant (s/? :blaze.resource/variant))
  :ret (s/or :resource :fhir/Resource :anomaly ::anom/anomaly))

(s/fdef fhir-spec/write-json
  :args (s/cat :context :blaze.fhir/writing-context
               :out #(instance? OutputStream %) :value any?))

(s/fdef fhir-spec/write-json-as-bytes
  :args (s/cat :context :blaze.fhir/writing-context :value any?)
  :ret bytes?)

(s/fdef fhir-spec/write-json-as-string
  :args (s/cat :context :blaze.fhir/writing-context :value any?)
  :ret string?)

(s/fdef fhir-spec/write-cbor
  :args (s/cat :context :blaze.fhir/writing-context :resource any?)
  :ret bytes?)

(s/fdef fhir-spec/conform-xml
  :args (s/cat :x any?)
  :ret (s/or :resource :fhir/Resource :anomaly ::anom/anomaly))

(s/fdef fhir-spec/unform-xml
  :args (s/cat :resource any?))
