(ns blaze.interaction.search.params
  (:require
    [blaze.handler.fhir.util :as fhir-util]
    [clojure.string :as str]))


(defn- clauses [query-params]
  (into
    []
    (comp
      (remove (fn [[k]] (and (str/starts-with? k "_") (not (#{"_id" "_list"} k)))))
      (mapcat (fn [[k v]] (mapv #(into [k] (str/split % #",")) (fhir-util/to-seq v)))))
    query-params))


(defn- summary?
  "Returns true iff a summary result is requested."
  [{summary "_summary" :as query-params}]
  (or (zero? (fhir-util/page-size query-params)) (= "count" summary)))


(defn decode
  [query-params]
  {:clauses (clauses query-params)
   :summary? (summary? query-params)
   :page-size (fhir-util/page-size query-params)
   :page-id (fhir-util/page-id query-params)
   :page-offset (fhir-util/page-offset query-params)})
