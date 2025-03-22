(ns blaze.fhir.parsing-context.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def :blaze.fhir/parsing-context
  (s/map-of keyword? fn?))
