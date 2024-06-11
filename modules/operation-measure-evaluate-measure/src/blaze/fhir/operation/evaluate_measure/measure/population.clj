(ns blaze.fhir.operation.evaluate-measure.measure.population
  (:require
   [blaze.anomaly :refer [if-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.fhir.operation.evaluate-measure.cql :as cql]
   [blaze.fhir.operation.evaluate-measure.measure.util :as u]
   [blaze.fhir.spec.type :as type]))

(defn- population-path [group-idx population-idx]
  (format "Measure.group[%d].population[%d]" group-idx population-idx))

(defn- evaluate-expression
  [{:keys [subject-handle subject-type] :as context}
   expression-name]
  (if subject-handle
    (cql/evaluate-individual-expression context subject-handle expression-name)
    (cql/evaluate-expression context expression-name subject-type)))

(defn evaluate
  "Returns a CompletableFuture that will complete with a tuple of population
  code and result or will complete exceptionally with an anomaly in case of
  errors."
  {:arglists '([context idx population])}
  [{:keys [group-idx] :as context} idx {:keys [code criteria]}]
  (let [population-path-fn #(population-path group-idx idx)]
    (if-ok [expression-name (u/expression-name population-path-fn criteria)]
      (do-sync [result (evaluate-expression context expression-name)]
        [code result])
      ac/completed-future)))

(defn population [code count]
  {:fhir/type :fhir.MeasureReport.group/population
   :count (type/integer count)
   :code code})
