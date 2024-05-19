(ns blaze.fhir.spec.spec
  (:require
   [blaze.fhir.spec.impl]
   [blaze.fhir.spec.references :as fsr]
   [clojure.alpha.spec :as s2]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]))

(s/def :fhir.resource/type
  (s/and string? #(re-matches #"[A-Z]([A-Za-z0-9_]){0,254}" %)))

(s/def :fhir/type
  (s/and
   keyword?
   #(some-> (namespace %) (str/starts-with? "fhir"))
   #(re-matches #"[A-Za-z]([A-Za-z0-9_]){0,254}" (name %))))

(s/def :blaze.resource/id
  (s/and string? #(re-matches #"[A-Za-z0-9\-\.]{1,64}" %)))

(s/def :blaze.fhir/literal-ref-tuple
  (s/tuple :fhir.resource/type :blaze.resource/id))

(s/def :blaze.fhir/literal-ref
  (s/and string?
         (s/conformer #(or (fsr/split-literal-ref %) ::s/invalid))))

(s/def :blaze/resource
  #(s2/valid? :fhir/Resource %))

(s/def :fhir/CodeableConcept
  #(s2/valid? :fhir/CodeableConcept %))

(s/def :fhir/Expression
  #(s2/valid? :fhir/Expression %))

(s/def :fhir.Measure/group
  #(s2/valid? :fhir.Measure/group %))

(s/def :fhir.Measure.group/stratifier
  #(s2/valid? :fhir.Measure.group/stratifier %))

(s/def :fhir.Measure.group/population
  #(s2/valid? :fhir.Measure.group/population %))
