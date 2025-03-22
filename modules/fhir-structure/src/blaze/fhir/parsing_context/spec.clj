(ns blaze.fhir.parsing-context.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def :blaze.fhir/parsing-context
  (s/map-of keyword? fn?))

(s/def :blaze.fhir.parsing-context/fail-on-unknown-property
  boolean?)

(s/def :blaze.fhir.parsing-context/include-summary-only
  boolean?)

(s/def :blaze.fhir.parsing-context/use-regex
  boolean?)
