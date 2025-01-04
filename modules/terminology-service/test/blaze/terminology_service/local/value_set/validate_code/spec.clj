(ns blaze.terminology-service.local.value-set.validate-code.spec
  (:require
   [blaze.db.spec]
   [blaze.fhir.spec]
   [blaze.terminology-service.local.value-set.validate-code :as-alias vs-validate-code]
   [clojure.spec.alpha :as s]))

(s/def ::vs-validate-code/context
  (s/keys :req-un [:blaze.db/db]))

(s/def ::code
  string?)

(s/def ::system
  string?)

(s/def ::version
  string?)

(s/def ::display
  string?)

(s/def ::vs-validate-code/clause
  (s/keys :req-un [::code] :opt-un [::system ::version ::display]))

(s/def ::active-only
  boolean?)

(s/def ::vs-validate-code/params
  (s/keys :req-un [::vs-validate-code/clause] :opt-un [::active-only]))
