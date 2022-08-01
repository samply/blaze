(ns blaze.fhir.operation.evaluate-measure.measure.population
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.fhir.operation.evaluate-measure.cql :as cql]
    [blaze.fhir.operation.evaluate-measure.measure.util :as u]))


(defn- population-path [group-idx population-idx]
  (format "Measure.group[%d].population[%d]" group-idx population-idx))


(defn- evaluate-expression
  [{:keys [subject-handle subject-type population-basis] :as context}
   expression-name]
  (if subject-handle
    (when (cql/evaluate-individual-expression context subject-handle
                                              expression-name)
      [{:population-handle subject-handle
        :subject-handle subject-handle}])
    (cql/evaluate-expression context expression-name subject-type
                             (or population-basis :boolean))))


(defn evaluate
  "Returns a map of :result, :handles, :luids and :tx-ops."
  {:arglists '([context idx population])}
  [{:keys [group-idx] :as context} idx {:keys [code criteria]}]
  (let [population-path-fn #(population-path group-idx idx)]
    (when-ok [expression-name (u/expression population-path-fn criteria)
              handles (evaluate-expression context expression-name)]
      (-> (u/population context :fhir.MeasureReport.group/population code handles)
          (assoc :handles handles)))))
