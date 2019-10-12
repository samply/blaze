(ns blaze.datomic.element-definition
  "Schema for element definition."
  (:require
    [datomic-tools.schema :refer [defattr]]))


(defattr :ElementDefinition/isSummary
  :db/valueType :db.type/boolean
  :db/cardinality :db.cardinality/one)
