(ns blaze.interaction.search.params.include
  (:require
    [blaze.handler.fhir.util :as fhir-util]
    [clojure.string :as str]))


(defn- include-value [v]
  (let [[source-type code target-type] (str/split v #":")]
    [source-type
     (cond-> {:code code}
       target-type
       (assoc :target-type target-type))]))


(defn- include-defs* [name query-params]
  (into
    {}
    (comp
      (filter (fn [[k]] (= name k)))
      (mapcat (fn [[_k v]] (mapv include-value (fhir-util/to-seq v)))))
    query-params))


(defn include-defs [query-params]
  (let [direct (include-defs* "_include" query-params)
        iterate (include-defs* "_include:iterate" query-params)]
    (cond-> nil
      (seq direct)
      (assoc :direct direct)
      (seq iterate)
      (assoc :iterate iterate))))
