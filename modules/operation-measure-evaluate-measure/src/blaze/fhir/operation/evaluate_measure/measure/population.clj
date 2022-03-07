(ns blaze.fhir.operation.evaluate-measure.measure.population
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.fhir.operation.evaluate-measure.cql :as cql]
    [blaze.fhir.operation.evaluate-measure.measure.util :as u]))


(defn- population-path [group-idx population-idx]
  (format "Measure.group[%d].population[%d]" group-idx population-idx))


(defn- evaluate-expression [{:keys [subject-handle] :as context} expression]
  (if subject-handle
    (cql/evaluate-individual-expression context subject-handle expression)
    (cql/evaluate-expression context expression)))


(defn evaluate
  {:arglists '([context population-idx population])}
  [{:keys [group-idx] :as context} population-idx
   {:keys [code criteria]}]
  (let [population-path-fn #(population-path group-idx population-idx)]
    (when-ok [expression (u/expression population-path-fn criteria)
              result (evaluate-expression context expression)]
      (u/population context :fhir.MeasureReport.group/population code result))))
