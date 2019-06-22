(ns blaze.spec
  (:require
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as sg]))



;; ---- FHIR Element Definition -----------------------------------------------

(s/def :ElementDefinition/path
  string?)


(s/def :ElementDefinition/max
  string?)


(s/def :ElementDefinition.type/code
  string?)


(s/def :ElementDefinition.type/_code
  map?)


(s/def :ElementDefinition/type
  (s/coll-of
    (s/keys :req [:ElementDefinition.type/code])))


(s/def :ElementDefinition/isSummary
  boolean?)


(s/def :ElementDefinition.un/type
  (s/coll-of
    (s/keys :req-un [(or :ElementDefinition.type/code
                         :ElementDefinition.type/_code)])))


(s/def :ElementDefinition.binding/strength
  string?)


(s/def :ElementDefinition.binding/valueSet
  string?)


(s/def :ElementDefinition.un/binding
  (s/keys :req-un [:ElementDefinition.binding/strength]
          :opt-un [:ElementDefinition.binding/valueSet]))


(s/def :fhir/ElementDefinition
  (s/keys :req [:ElementDefinition/path]
          :opt [:ElementDefinition/max
                :ElementDefinition/type]))


(s/def :fhir.un/ElementDefinition
  (s/keys :req-un [:ElementDefinition/path]
          :opt-un [:ElementDefinition/max
                   :ElementDefinition.un/type
                   :ElementDefinition.un/binding]))



;; ---- FHIR Structure Definition ---------------------------------------------

(s/def :StructureDefinition/name
  string?)


(s/def :StructureDefinition/kind
  #{"primitive-type" "complex-type" "resource" "logical"})


(s/def :StructureDefinition.snapshot/element
  (s/coll-of :fhir/ElementDefinition))


(s/def :StructureDefinition.snapshot.un/element
  (s/coll-of :fhir.un/ElementDefinition))


(s/def :StructureDefinition/snapshot
  (s/keys :req [:StructureDefinition.snapshot/element]))


(s/def :StructureDefinition.un/snapshot
  (s/keys :req-un [:StructureDefinition.snapshot.un/element]))


(s/def :fhir/StructureDefinition
  (s/keys :req [:StructureDefinition/name
                :StructureDefinition/kind]
          :opt [:StructureDefinition/snapshot]))


(s/def :fhir.un/StructureDefinition
  (s/keys :req-un [:StructureDefinition/name
                   :StructureDefinition/kind]
          :opt-un [:StructureDefinition.un/snapshot]))



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


(s/def :fhir/resourceType
  string?)


(s/def :fhir/id
  string?)


(s/def :fhir/resource
  (s/keys :req-un [:fhir/resourceType :fhir/id]))


(s/def :fhir/issue
  #{"invalid"
    "structure"
    "required"
    "value"
    "invariant"
    "security"
    "login"
    "unknown"
    "expired"
    "forbidden"
    "suppressed"
    "processing"
    "not-supported"
    "duplicate"
    "multiple-matches"
    "not-found"
    "too-long"
    "code-invalid"
    "extension"
    "too-costly"
    "business-rule"
    "conflict"
    "transient"
    "lock-error"
    "no-store"
    "exception"
    "timeout"
    "incomplete"
    "throttled"
    "informational"})


(s/def :fhir/operation-outcome
  #{"DELETE_MULTIPLE_MATCHES"
    "MSG_AUTH_REQUIRED"
    "MSG_BAD_FORMAT"
    "MSG_BAD_SYNTAX"
    "MSG_CANT_PARSE_CONTENT"
    "MSG_CANT_PARSE_ROOT"
    "MSG_CREATED"
    "MSG_DATE_FORMAT"
    "MSG_DELETED"
    "MSG_DELETED_DONE"
    "MSG_DELETED_ID"
    "MSG_DUPLICATE_ID"
    "MSG_ERROR_PARSING"
    "MSG_ID_INVALID"
    "MSG_ID_TOO_LONG"
    "MSG_INVALID_ID"
    "MSG_JSON_OBJECT"
    "MSG_LOCAL_FAIL"
    "MSG_NO_EXIST"
    "MSG_NO_MATCH"
    "MSG_NO_MODULE"
    "MSG_NO_SUMMARY"
    "MSG_OP_NOT_ALLOWED"
    "MSG_PARAM_CHAINED"
    "MSG_PARAM_INVALID"
    "MSG_PARAM_MODIFIER_INVALID"
    "MSG_PARAM_NO_REPEAT"
    "MSG_PARAM_UNKNOWN"
    "MSG_RESOURCE_EXAMPLE_PROTECTED"
    "MSG_RESOURCE_ID_FAIL"
    "MSG_RESOURCE_ID_MISMATCH"
    "MSG_RESOURCE_ID_MISSING"
    "MSG_RESOURCE_NOT_ALLOWED"
    "MSG_RESOURCE_REQUIRED"
    "MSG_RESOURCE_TYPE_MISMATCH"
    "MSG_SORT_UNKNOWN"
    "MSG_TRANSACTION_DUPLICATE_ID"
    "MSG_TRANSACTION_MISSING_ID"
    "MSG_UNHANDLED_NODE_TYPE"
    "MSG_UNKNOWN_CONTENT"
    "MSG_UNKNOWN_OPERATION"
    "MSG_UNKNOWN_TYPE"
    "MSG_UPDATED"
    "MSG_VERSION_AWARE"
    "MSG_VERSION_AWARE_CONFLICT"
    "MSG_VERSION_AWARE_URL"
    "MSG_WRONG_NS"
    "SEARCH_MULTIPLE"
    "SEARCH_NONE"
    "UPDATE_MULTIPLE_MATCHES"})


(s/def :fhir.issue/expression
  (s/or :coll (s/coll-of string?)
        :string string?))
