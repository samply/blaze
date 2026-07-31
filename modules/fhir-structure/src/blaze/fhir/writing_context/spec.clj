(ns blaze.fhir.writing-context.spec
  (:require
   [clojure.spec.alpha :as s])
  (:import
   [blaze.fhir.writing TypeHandler]))

(s/def :blaze.fhir/writing-context
  (s/map-of keyword? #(instance? TypeHandler %)))
