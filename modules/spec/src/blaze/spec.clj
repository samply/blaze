(ns blaze.spec
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [java-time.api :as time])
  (:import
   [java.time.temporal Temporal]
   [java.util Random]))

;; The base URL of Blaze without :blaze/context-path
(s/def :blaze/base-url
  (s/and string? (complement #(str/ends-with? % "/"))))

(s/def :blaze/version
  string?)

(s/def :blaze/release-date
  #(instance? Temporal %))

(s/def :fhir/version
  #{"4.0.1" "6.0.0-ballot3"})

;; The context path of Blaze that is appended to the :blaze/base-url
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

(s/def :blaze/cancelled?
  (s/fspec :args (s/cat) :ret (s/nilable ::anom/anomaly)))

(s/def :blaze/page-id
  (s/and string? #(re-matches #"[A-Za-z0-9-_]+" %)))

;; ---- DB ------------------------------------------------------------------

(s/def :blaze.db.query.clause/code
  string?)

(s/def :blaze.db.query/search-clause
  (s/cat :code :blaze.db.query.clause/code :values (s/+ string?)))

(s/def :blaze.db.query/search-clauses
  (s/coll-of :blaze.db.query/search-clause :kind vector?))

(s/def :blaze.db.query/sort-direction
  #{:asc :desc})

(s/def :blaze.db.query/sort-clause
  (s/tuple #{:sort} :blaze.db.query.clause/code :blaze.db.query/sort-direction))

(s/def :blaze.db.query/clause
  (s/or :search-clause :blaze.db.query/search-clause
        :sort-clause :blaze.db.query/sort-clause))

(s/def :blaze.db.query/clauses
  (s/coll-of :blaze.db.query/clause :kind vector?))

(s/def :blaze/java-tool-options
  string?)

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
    "deleted"
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

(s/def :http/status
  (s/and int? #(<= 100 % 599)))

(s/def :http/headers
  (s/coll-of (s/tuple string? string?)))

;; ---- Clojure ---------------------------------------------------------------

(s/def :clojure/binding-form (s/or :symbol simple-symbol?
                                   :map-destructuring map?
                                   :list-destructuring vector?))

(s/def :clojure/binding (s/cat :binding :clojure/binding-form :expr any?))

(s/def :clojure/bindings (s/and vector? (s/* :clojure/binding)))
