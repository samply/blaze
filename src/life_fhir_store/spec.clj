(ns life-fhir-store.spec
  (:require
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as sg]))



;; ---- FHIR Element Definition -----------------------------------------------

(s/def :fhir.element-definition/path
  string?)

(s/def :fhir.element-definition/max
  string?)

(s/def :fhir.element-definition.type/code
  string?)

(s/def :fhir.element-definition/type
  (s/coll-of
    (s/keys :req-un [:fhir.element-definition.type/code])))

(s/def :fhir/element-definition
  (s/keys :req-un [:fhir.element-definition/path]
          :opt-un [:fhir.element-definition/max
                   :fhir.element-definition/type]))



;; ---- FHIR Structure Definition ---------------------------------------------

(s/def :fhir.structure-definition/id
  string?)

(s/def :fhir.structure-definition/name
  string?)

(s/def :fhir.structure-definition/element
  (s/coll-of :fhir/element-definition))

(s/def :fhir.structure-definition/snapshot
  (s/keys :req-un [:fhir.structure-definition/element]))

(s/def :fhir.structure-definition/differential
  (s/keys :req-un [:fhir.structure-definition/element]))

(s/def :fhir/structure-definition
  (s/keys :req-un [:fhir.structure-definition/id
                   :fhir.structure-definition/name]
          :opt-un [:fhir.structure-definition/snapshot
                   :fhir.structure-definition/differential]))



;; ---- Life Element Definition -----------------------------------------------

(s/def :life.element-definition/key
  keyword?)

(s/def :life.element-definition/type
  (s/keys :req [:life.element-definition.type/code]))

(s/def :life.element-definition/max
  (s/or :size nat-int? :limit #{:*}))

(s/def :life.element-definition/summary?
  boolean?)

(s/def :life/element-definition
  (s/keys :req [:life.element-definition/key
                :life.element-definition/type]
          :opt [:life.element-definition/max]))



;; ---- Life Structure Definition ---------------------------------------------

(s/def :life.structure-definition/id
  string?)

(s/def :life.structure-definition/name
  string?)

(s/def :life.structure-definition/elements
  (s/map-of :life.element-definition/key :life/element-definition))

(s/def :life/structure-definition
  (s/keys :req [:life.structure-definition/id
                :life.structure-definition/name]
          :opt [:life.structure-definition/elements]))



;; ---- FHIR ------------------------------------------------------------------

(s/def :fhir.coding/system
  (s/with-gen string? #(s/gen #{"http://loinc.org"})))

(s/def :fhir.coding/code
  (s/with-gen string? #(s/gen #{"39156-5" "29463-7"})))

(s/def :fhir/coding
  (s/keys :req-un [:fhir.coding/system :fhir.coding/code]))

(s/def :fhir.observation/resourceType
  #{"Observation"})

(s/def :fhir.observation/id
  (s/with-gen string? #(sg/fmap str (sg/uuid))))

(s/def :fhir.observation/status
  #{"final"})

(s/def :fhir.codeable-concept/coding
  (s/every :fhir/coding :min-count 1 :max-count 1))

(s/def :fhir/codeable-concept
  (s/keys :req-un [:fhir.codeable-concept/coding]))

(s/def :fhir.observation/code
  :fhir/codeable-concept)

(s/def :fhir.patient/reference
  string?)

(s/def :fhir.reference/patient
  (s/keys :req-un [:fhir.patient/reference]))

(s/def :fhir.observation/subject
  :fhir.reference/patient)

(s/def :fhir.quantity/value
  (s/with-gen double? #(sg/double* {:min 0 :max 100})))

(s/def :fhir.observation/valueQuantity
  (s/keys :req-un [:fhir.quantity/value]))

(s/def :fhir/observation
  (s/keys :req-un [:fhir.observation/resourceType
                   :fhir.observation/id
                   :fhir.observation/status
                   :fhir.observation/code
                   :fhir.observation/subject
                   :fhir.observation/valueQuantity]))

(comment

  )
