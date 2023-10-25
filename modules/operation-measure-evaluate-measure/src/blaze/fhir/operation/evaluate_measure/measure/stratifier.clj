(ns blaze.fhir.operation.evaluate-measure.measure.stratifier
  (:require
    [blaze.anomaly :as ba :refer [if-ok when-ok]]
    [blaze.async.comp :as ac :refer [do-sync]]
    [blaze.fhir.operation.evaluate-measure.cql :as cql]
    [blaze.fhir.operation.evaluate-measure.measure.util :as u]
    [blaze.fhir.spec.type :as type]))


(defn- value-concept [value]
  (let [type (type/type value)]
    (cond
      (identical? :fhir/CodeableConcept type)
      value

      (identical? :fhir/Quantity type)
      (type/codeable-concept
        {:text (cond-> (str (:value value)) (:code value) (str " " (:code value)))})

      :else
      (type/codeable-concept
        {:text (type/string (if (nil? value) "null" (str value)))}))))


(defn- stratum-value-extension [value]
  (type/extension
    {:url "http://hl7.org/fhir/5.0/StructureDefinition/extension-MeasureReport.group.stratifier.stratum.value"
     :value value}))


(defn- stratum* [population value]
  (cond-> {:fhir/type :fhir.MeasureReport.group.stratifier/stratum
           :value (value-concept value)
           :population [population]}

    (identical? :fhir/Quantity (type/type value))
    (assoc :extension [(stratum-value-extension value)])))


(defn- stratum [context population-code value handles]
  (-> (u/population
        context :fhir.MeasureReport.group.stratifier.stratum/population
        population-code handles)
      (update :result stratum* value)))


(defn- stratifier* [strata code]
  (cond-> {:fhir/type :fhir.MeasureReport.group/stratifier
           :stratum (vec (sort-by (comp type/value :text :value) strata))}
    code
    (assoc :code [code])))


(defn- stratifier [{:keys [luids] :as context} code population-code strata]
  (-> (reduce-kv
        (fn [{:keys [luids] :as ret} value handles]
          (->> (stratum (assoc context :luids luids) population-code value handles)
               (u/merge-result ret)))
        {:result [] :luids luids :tx-ops []}
        strata)
      (update :result stratifier* code)))


(defn- stratifier-path [group-idx stratifier-idx]
  (format "Measure.group[%d].stratifier[%d]" group-idx stratifier-idx))


(defn- calc-strata [{:keys [population-basis] :as context} name handles]
  (if (nil? population-basis)
    (cql/calc-strata context name handles)
    (cql/calc-function-strata context name handles)))


(defn- evaluate-stratifier
  {:arglists '([context evaluated-populations stratifier])}
  [{:keys [group-idx stratifier-idx] :as context} evaluated-populations
   {:keys [code criteria]}]
  (if-ok [name (u/expression #(stratifier-path group-idx stratifier-idx) criteria)]
    (do-sync [strata (calc-strata context name (-> evaluated-populations :handles first))]
      (stratifier context code (-> evaluated-populations :result first :code) strata))
    ac/completed-future))


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


(defn- stratum-component-value-extension [value]
  (type/extension
    {:url "http://hl7.org/fhir/5.0/StructureDefinition/extension-MeasureReport.group.stratifier.stratum.component.value"
     :value value}))


(defn- multi-component-stratum* [population codes values]
  {:fhir/type :fhir.MeasureReport.group.stratifier/stratum
   :component
   (mapv
     (fn [code value]
       (cond-> {:fhir/type :fhir.MeasureReport.group.stratifier.stratum/component
                :code code
                :value (value-concept value)}

         (identical? :fhir/Quantity (type/type value))
         (assoc :extension [(stratum-component-value-extension value)])))
     codes
     values)
   :population [population]})


(defn- multi-component-stratum [context codes population-code values result]
  (-> (u/population
        context :fhir.MeasureReport.group.stratifier.stratum/population
        population-code result)
      (update :result multi-component-stratum* codes values)))


(defn- multi-component-stratifier* [strata codes]
  {:fhir/type :fhir.MeasureReport.group/stratifier
   :code codes
   :stratum (vec (sort-by (comp #(mapv (comp type/value :text :value) %) :component) strata))})


(defn- multi-component-stratifier
  [{:keys [luids] :as context} codes population-code strata]
  (-> (reduce-kv
        (fn [{:keys [luids] :as ret} values result]
          (->> (multi-component-stratum (assoc context :luids luids) codes
                                        population-code values result)
               (u/merge-result ret)))
        {:result [] :luids luids :tx-ops []}
        strata)
      (update :result multi-component-stratifier* codes)))


(defn- evaluate-multi-component-stratifier
  [context evaluated-populations {:keys [component]}]
  (if-ok [{:keys [codes expression-names]} (extract-stratifier-components context component)]
    (do-sync [strata (cql/calc-multi-component-strata
                       context
                       expression-names
                       (-> evaluated-populations :handles first))]
      (multi-component-stratifier context codes
                                  (-> evaluated-populations :result first :code)
                                  strata))
    ac/completed-future))


(defn evaluate
  {:arglists '([context evaluated-populations stratifier])}
  [context evaluated-populations {:keys [component] :as stratifier}]
  (if (seq component)
    (evaluate-multi-component-stratifier context evaluated-populations stratifier)
    (evaluate-stratifier context evaluated-populations stratifier)))
