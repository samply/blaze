(ns blaze.fhir.operation.evaluate-measure.spec
  (:require
    [clojure.spec.alpha :as s]
    [datomic-spec.core :as ds]))


(s/def ::measure-entity
  (s/and ::ds/entity #(contains? % :Measure/id)))
