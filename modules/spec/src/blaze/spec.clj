(ns blaze.spec
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [java-time :as time])
  (:import
    [java.util Random]))


(s/def :blaze/base-url
  (s/and string? (complement #(str/ends-with? % "/"))))


(s/def :blaze/context-path
  (s/and
    string?
    (s/or
      :empty str/blank?
      :non-empty (s/and
                   #(str/starts-with? % "/")
                   (complement #(str/ends-with? % "/"))))))


(s/def :blaze/clock
  time/clock?)


(s/def :blaze/rng
  #(instance? Random %))


(s/def :blaze/rng-fn
  fn?)



;; ---- FHIR ------------------------------------------------------------------

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
