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

(s/def :blaze.resource/variant
  #{:complete :summary})

(s/def :fhir/Resource
  #(s2/valid? :fhir/Resource %))

(s/def :fhir/CodeableConcept
  #(s2/valid? :fhir/CodeableConcept %))

(s/def :fhir/Coding
  #(s2/valid? :fhir/Coding %))

(s/def :fhir/Expression
  #(s2/valid? :fhir/Expression %))

(s/def :fhir/Quantity
  #(s2/valid? :fhir/Quantity %))

(s/def :fhir/Task
  #(s2/valid? :fhir/Task %))

(s/def :fhir.Task/input
  #(s2/valid? :fhir.Task/input %))

(s/def :fhir.Task/output
  #(s2/valid? :fhir.Task/output %))

(s/def :fhir/Measure
  #(s2/valid? :fhir/Measure %))

(s/def :fhir.Measure/group
  #(s2/valid? :fhir.Measure/group %))

(s/def :fhir/Bundle
  #(s2/valid? :fhir/Bundle %))

(s/def :fhir.Bundle/entry
  #(s2/valid? :fhir.Bundle/entry %))

(s/def :fhir.Measure.group/stratifier
  #(s2/valid? :fhir.Measure.group/stratifier %))

(s/def :fhir.Measure.group/population
  #(s2/valid? :fhir.Measure.group/population %))

(s/def :fhir/instant
  #(s2/valid? :fhir/instant %))

(s/def :fhir/dateTime
  #(s2/valid? :fhir/dateTime %))

(s/def :fhir/canonical
  #(s2/valid? :fhir/canonical %))

(s/def :fhir/CodeSystem
  #(s2/valid? :fhir/CodeSystem %))

(s/def :fhir.CodeSystem/concept
  #(s2/valid? :fhir.CodeSystem/concept %))

(s/def :fhir/ValueSet
  #(s2/valid? :fhir/ValueSet %))

(s/def :fhir.ValueSet.compose.include/concept
  #(s2/valid? :fhir.ValueSet.compose.include/concept %))

(s/def :fhir.ValueSet.compose.include/filter
  #(s2/valid? :fhir.ValueSet.compose.include/filter %))

(s/def :fhir.ValueSet.expansion/contains
  #(s2/valid? :fhir.ValueSet.expansion/contains %))

(s/def :fhir/Parameters
  #(s2/valid? :fhir/Parameters %))

(s/def :fhir/OperationOutcome
  #(s2/valid? :fhir/OperationOutcome %))

(s/def :fhir.OperationOutcome/issue
  #(s2/valid? :fhir.OperationOutcome/issue %))

(s/def :fhir/GraphDefinition
  #(s2/valid? :fhir/GraphDefinition %))
