(ns blaze.fhir.operation.evaluate-measure.measure.stratifier
  (:require
    [blaze.anomaly :as ba :refer [if-ok when-ok]]
    [blaze.fhir.operation.evaluate-measure.cql :as cql]
    [blaze.fhir.operation.evaluate-measure.measure.util :as u]
    [blaze.fhir.spec.type :as type]))


(defn- value-concept [value]
  (type/codeable-concept {:text (str (if (nil? value) "null" value))}))


(defn- stratum* [population value]
  {:fhir/type :fhir.MeasureReport.group.stratifier/stratum
   :value (value-concept value)
   :population [population]})


(defn- stratum [context population-code [value result]]
  (-> (u/population
        context :fhir.MeasureReport.group.stratifier.stratum/population
        population-code result)
      (update :result stratum* value)))


(defn- stratifier* [strata code]
  (cond-> {:fhir/type :fhir.MeasureReport.group/stratifier
           :stratum (sort-by (comp :text :value) strata)}
    code
    (assoc :code [code])))


(defn- stratifier [{:keys [luids] :as context} code population-code strata]
  (-> (reduce
        (fn [{:keys [luids] :as ret} x]
          (->> (stratum (assoc context :luids luids) population-code x)
               (u/merge-result ret)))
        {:result [] :luids luids :tx-ops []}
        strata)
      (update :result stratifier* code)))


(defn- stratifier-path [group-idx stratifier-idx]
  (format "Measure.group[%d].stratifier[%d]" group-idx stratifier-idx))


(defn- calc-strata
  [{:keys [subject-handle] :as context} population-expression-name
   stratum-expression-name]
  (if subject-handle
    (cql/calc-individual-strata context subject-handle
                                population-expression-name
                                stratum-expression-name)
    (cql/calc-strata context population-expression-name
                     stratum-expression-name)))


(defn- evaluate-single-stratifier
  {:arglists '([context populations stratifier])}
  [{:keys [group-idx stratifier-idx] :as context} populations
   {:keys [code criteria]}]
  (when-ok [expression (u/expression #(stratifier-path group-idx stratifier-idx)
                                     criteria)
            strata (calc-strata
                     context
                     (-> populations first :criteria :expression)
                     expression)]
    (stratifier context code (-> populations first :code) strata)))


(defn- stratifier-component-path [{:keys [group-idx stratifier-idx component-idx]}]
  (format "Measure.group[%d].stratifier[%d].component[%d]"
          group-idx stratifier-idx component-idx))


(defn- extract-stratifier-component
  "Extracts code and expression-name from `stratifier-component`."
  [context {:keys [code criteria]}]
  (if (nil? code)
    (ba/incorrect
      "Missing code."
      :fhir/issue "required"
      :fhir.issue/expression (str (stratifier-component-path context) ".code"))
    (when-ok [expression (u/expression #(stratifier-component-path context)
                                       criteria)]
      [code expression])))


(defn- extract-stratifier-component*
  ([_context x] x)
  ([context results [idx component]]
   (if-ok [[code expression-name] (extract-stratifier-component
                                    (assoc context :component-idx idx)
                                    component)]
     (-> (update results :codes conj code)
         (update :expression-names conj expression-name))
     reduced)))


(defn- extract-stratifier-components
  "Extracts code and expression-name from each of `stratifier-components`."
  [context stratifier-components]
  (transduce
    (map-indexed vector)
    (partial extract-stratifier-component* context)
    {:codes []
     :expression-names []}
    stratifier-components))


(defn- multi-component-stratum* [population codes values]
  {:fhir/type :fhir.MeasureReport.group.stratifier/stratum
   :component
   (mapv
     (fn [code value]
       {:fhir/type :fhir.MeasureReport.group.stratifier.stratum/component
        :code code
        :value (value-concept value)})
     codes
     values)
   :population [population]})


(defn- multi-component-stratum [context codes population-code [values result]]
  (-> (u/population
        context :fhir.MeasureReport.group.stratifier.stratum/population
        population-code result)
      (update :result multi-component-stratum* codes values)))


(defn- multi-component-stratifier* [strata codes]
  {:fhir/type :fhir.MeasureReport.group/stratifier
   :code codes
   :stratum (sort-by (comp #(mapv (comp :text :value) %) :component) strata)})


(defn- multi-component-stratifier
  [{:keys [luids] :as context} codes population-code strata]
  (-> (reduce
        (fn [{:keys [luids] :as ret} x]
          (->> (multi-component-stratum (assoc context :luids luids) codes
                                        population-code x)
               (u/merge-result ret)))
        {:result [] :luids luids :tx-ops []}
        strata)
      (update :result multi-component-stratifier* codes)))


(defn- evaluate-multi-component-stratifier
  [context populations {:keys [component]}]
  (when-ok [results (extract-stratifier-components context component)]
    (let [{:keys [codes expression-names]} results]
      (when-ok [strata (cql/calc-multi-component-strata
                         context
                         (-> populations first :criteria :expression)
                         expression-names)]
        (multi-component-stratifier context codes (-> populations first :code)
                                    strata)))))


(defn evaluate
  {:arglists '([context populations stratifier])}
  [context populations {:keys [component] :as stratifier}]
  (if (seq component)
    (evaluate-multi-component-stratifier context populations stratifier)
    (evaluate-single-stratifier context populations stratifier)))
