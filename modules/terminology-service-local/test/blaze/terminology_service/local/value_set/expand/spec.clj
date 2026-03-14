(ns blaze.terminology-service.local.value-set.expand.spec
  (:require
   [blaze.db.spec]
   [blaze.fhir.spec]
   [blaze.terminology-service.local.value-set.expand :as-alias vs-expand]
   [clojure.spec.alpha :as s]))

(s/def ::count
  nat-int?)

(s/def ::include-designations
  boolean?)

(s/def ::include-definition
  boolean?)

(s/def ::active-only
  boolean?)

(s/def ::exclude-nested
  boolean?)

(s/def ::properties
  (s/coll-of string?))

(s/def ::system-versions
  (s/coll-of :fhir/canonical))

(s/def ::vs-expand/params
  (s/keys
   :opt-un
   [::count
    ::include-designations
    ::include-definition
    ::active-only
    ::exclude-nested
    ::properties
    ::system-versions]))

(s/def ::vs-expand/context
  (s/keys :req-un [:blaze.db/db ::vs-expand/params]))
