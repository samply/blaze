(ns blaze.fhir.writing-context.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def :blaze.fhir/writing-context
  (s/map-of keyword? fn?))
