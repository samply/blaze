(ns blaze.fhir.type-metadata.spec
  (:require
   [clojure.spec.alpha :as s])
  (:import
   [blaze.fhir.spec.type TypeMetadata]))

(s/def :blaze.fhir/type-metadata-registry
  (s/map-of keyword? #(instance? TypeMetadata %)))
