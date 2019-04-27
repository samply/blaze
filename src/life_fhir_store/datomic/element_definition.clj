(ns life-fhir-store.datomic.element-definition
  "Schema for element definition."
  (:require
    [datomic-tools.schema :refer [defattr]]))


(defattr :ElementDefinition/path
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one
  :db/unique :db.unique/identity)


(defattr :ElementDefinition/isSummary
  :db/valueType :db.type/boolean
  :db/cardinality :db.cardinality/one)
