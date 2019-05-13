(ns blaze.datomic.search
  (:require
    [datomic.api :as d]))

(defn less [db system code end]
  (d/q
    '[:find [?subject ...]
      :in $ ?system ?code ?end
      :where
      [?coding :Coding/code ?code]
      [?coding :Coding/system ?system]
      [?c :CodeableConcept/coding ?coding]
      [?o :Observation/code ?c]
      [?o :Observation/valueQuantity ?vq]
      [?vq :Quantity/value ?v]
      [(< ?v ?end)]
      [?o :Observation/subject ?subject]]
    db system code end))
