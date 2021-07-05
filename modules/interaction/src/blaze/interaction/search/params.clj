(ns blaze.interaction.search.params
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.interaction.search.params.include :as include]
    [clojure.string :as str]))


(defn- remove-query-param? [[k]]
  (and (str/starts-with? k "_")
       (not (#{"_id" "_list" "_profile"} k))
       (not (str/starts-with? k "_has"))))


(defn- clauses [query-params]
  (into
    []
    (comp
      (remove remove-query-param?)
      (mapcat (fn [[k v]] (mapv #(into [k] (str/split % #",")) (fhir-util/to-seq v)))))
    query-params))


(defn- summary?
  "Returns true iff a summary result is requested."
  [{summary "_summary" :as query-params}]
  (or (zero? (fhir-util/page-size query-params)) (= "count" summary)))


(defn decode [handling query-params]
  (when-ok [include-defs (include/include-defs handling query-params)]
    {:clauses (clauses query-params)
     :include-defs include-defs
     :summary? (summary? query-params)
     :summary (get query-params "_summary")
     :page-size (fhir-util/page-size query-params)
     :page-type (fhir-util/page-type query-params)
     :page-id (fhir-util/page-id query-params)
     :page-offset (fhir-util/page-offset query-params)}))
