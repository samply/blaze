(ns blaze.fhir.operation.evaluate-measure.measure.population
  (:require
   [blaze.anomaly :refer [when-ok]]
   [blaze.fhir.operation.evaluate-measure.cql :as cql]
   [blaze.fhir.operation.evaluate-measure.measure.util :as u]))

(defn- population-path [group-idx population-idx]
  (format "Measure.group[%d].population[%d]" group-idx population-idx))

(defn- evaluate-expression
  [{:keys [subject-handle subject-type return-handles? population-basis]
    :as context}
   expression-name]
  (if subject-handle
    (if (cql/evaluate-individual-expression context subject-handle
                                            expression-name)
      (if return-handles?
        [{:population-handle subject-handle
          :subject-handle subject-handle}]
        1)
      (if return-handles? [] 0))
    (cql/evaluate-expression context expression-name subject-type
                             (or population-basis :boolean))))

(defn evaluate
  "Returns a map of :result, :handles, :luids and :tx-ops."
  {:arglists '([context idx population])}
  [{:keys [group-idx return-handles?] :as context} idx {:keys [code criteria]}]
  (let [population-path-fn #(population-path group-idx idx)]
    (when-ok [expression-name (u/expression population-path-fn criteria)
              result (evaluate-expression context expression-name)]
      (if return-handles?
        (-> (u/population context :fhir.MeasureReport.group/population code result)
            (assoc :handles result))
        (u/population-count context :fhir.MeasureReport.group/population code result)))))
