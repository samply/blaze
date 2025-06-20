(ns blaze.fhir.parsing-context.spec
  (:require
   [blaze.fhir.parsing-context :as-alias parsing-context]
   [clojure.spec.alpha :as s]))

(s/def :blaze.fhir/parsing-context
  (s/map-of keyword? fn?))

(s/def ::parsing-context/fail-on-unknown-property
  boolean?)

(s/def ::parsing-context/include-summary-only
  boolean?)

(s/def ::parsing-context/use-regex
  boolean?)
