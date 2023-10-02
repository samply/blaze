(ns blaze.fhir.operation.evaluate-measure.measure.population
  (:require
    [blaze.anomaly :refer [if-ok]]
    [blaze.async.comp :as ac :refer [do-sync]]
    [blaze.fhir.operation.evaluate-measure.cql :as cql]
    [blaze.fhir.operation.evaluate-measure.measure.util :as u]))


(defn- population-path [group-idx population-idx]
  (format "Measure.group[%d].population[%d]" group-idx population-idx))


(defn- evaluate-expression
  [{:keys [subject-handle subject-type] :as context}
   expression-name]
  (if subject-handle
    (cql/evaluate-individual-expression context subject-handle expression-name)
    (cql/evaluate-expression context expression-name subject-type)))


(defn evaluate
  "Returns a CompletableFuture that will complete with a map of :result,
  :handles, :luids and :tx-ops or will complete exceptionally with an anomaly in
  case of errors."
  {:arglists '([context idx population])}
  [{:keys [group-idx] :as context} idx {:keys [code criteria]}]
  (let [population-path-fn #(population-path group-idx idx)]
    (if-ok [expression-name (u/expression population-path-fn criteria)]
      (do-sync [result (evaluate-expression context expression-name)]
        [code result])
      ac/completed-future)))


(defn reduce-op
  {:arglists '([context])}
  [{:keys [return-handles? luids] :as context}]
  (if return-handles?
    (fn
      ([]
       {:result [] :handles [] :luids luids :tx-ops []})
      ([ret]
       ret)
      ([{:keys [luids] :as ret} [code result]]
       (->> (-> (u/population (assoc context :luids luids)
                              :fhir.MeasureReport.group/population code result)
                (assoc :handles result))
            (u/merge-result ret))))
    (fn
      ([]
       {:result [] :handles [] :luids luids :tx-ops []})
      ([ret]
       ret)
      ([{:keys [luids] :as ret} [code result]]
       (->> (u/population-count (assoc context :luids luids)
                                :fhir.MeasureReport.group/population code result)
            (u/merge-result ret))))))
