(ns blaze.terminology-service.spec
  (:require
   [blaze.db.spec]
   [blaze.fhir.spec.spec]
   [blaze.terminology-service.code-system-validate-code :as-alias cs-validate-code]
   [blaze.terminology-service.expand-value-set :as-alias expand-vs]
   [blaze.terminology-service.protocols :as p]
   [blaze.terminology-service.request :as-alias request]
   [blaze.terminology-service.value-set-validate-code :as-alias vs-validate-code]
   [clojure.spec.alpha :as s]))

(s/def :blaze/terminology-service
  #(satisfies? p/TerminologyService %))

(s/def ::request/url
  string?)

(s/def ::request/code-system
  :fhir/CodeSystem)

(s/def ::request/value-set
  :fhir/ValueSet)

(s/def ::request/value-set-version
  string?)

(s/def ::request/count
  nat-int?)

(s/def ::request/include-designations
  boolean?)

(s/def ::request/include-definition
  boolean?)

(s/def ::request/active-only
  boolean?)

(s/def ::request/code
  string?)

(s/def ::request/system
  string?)

(s/def ::request/version
  string?)

(s/def ::request/coding
  :fhir/Coding)

(s/def ::request/exclude-nested
  boolean?)

(s/def ::request/system-versions
  (s/coll-of :fhir/canonical))

(s/def ::request/properties
  (s/coll-of string?))

(s/def ::request/tx-resource
  (s/or :code-system :fhir/CodeSystem :value-set :fhir/ValueSet))

(s/def ::request/tx-resources
  (s/coll-of ::request/tx-resource))

;; parameters are taken from: http://hl7.org/fhir/R4/codesystem-operation-validate-code.html
(s/def ::cs-validate-code/request
  (s/keys
   :opt-un
   [:blaze.resource/id
    ::request/url
    ::request/code-system
    ::request/code
    ::request/version
    ::request/coding]))

;; parameters are taken from: http://hl7.org/fhir/R4/valueset-operation-expand.html
(s/def ::expand-vs/request
  (s/keys
   :opt-un
   [:blaze.resource/id
    ::request/url
    ::request/value-set-version
    ::request/count
    ::request/include-designations
    ::request/include-definition
    ::request/active-only
    ::request/exclude-nested
    ::request/properties
    ::request/system-versions
    ::request/tx-resources]))

;; parameters are taken from: http://hl7.org/fhir/R4/valueset-operation-validate-code.html
(s/def ::vs-validate-code/request
  (s/keys
   :opt-un
   [:blaze.resource/id
    ::request/url
    ::request/value-set
    ::request/value-set-version
    ::request/code
    ::request/system
    ::request/coding]))
