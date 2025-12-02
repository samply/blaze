(ns blaze.fhir.operation.evaluate-measure.measure.stratifier
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.fhir.operation.evaluate-measure.cql :as cql]
   [blaze.fhir.operation.evaluate-measure.measure.util :as u]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.luid :as luid]
   [blaze.util :refer [conj-vec str]]))

(defn- quantity-value [value]
  (let [code (-> value :code type/value)]
    (cond-> (str (-> value :value type/value)) code (str " " code))))

(defn- value-concept
  "Converts `value` into a CodeableConcept so that it can be used in a
  Stratifier."
  [value]
  (let [type (type/type value)]
    (cond
      (identical? :fhir/CodeableConcept type)
      value

      (identical? :fhir/Quantity type)
      (type/codeable-concept
       {:text (type/string (quantity-value value))})

      (fhir-spec/primitive-val? value)
      (type/codeable-concept {:text (type/string (str (type/value value)))})

      (nil? value)
      (type/codeable-concept {:text #fhir/string "null"})

      :else
      (type/codeable-concept {:text (type/string (str value))}))))

(defn- stratum-value-extension [value]
  (type/extension
   {:url "http://hl7.org/fhir/5.0/StructureDefinition/extension-MeasureReport.group.stratifier.stratum.value"
    :value value}))

(defn stratum-count [[value populations]]
  (cond-> {:fhir/type :fhir.MeasureReport.group.stratifier/stratum
           :value (value-concept value)
           :population
           (mapv
            (fn [[code count]]
              {:fhir/type :fhir.MeasureReport.group.stratifier.stratum/population
               :code code
               :count (type/integer count)})
            populations)}

    (identical? :fhir/Quantity (type/type value))
    (assoc :extension [(stratum-value-extension value)])))

(defn- stratum-subject-list-populations [context populations]
  (reduce
   (fn [{::luid/keys [generator] :keys [result tx-ops]} [code handles]]
     (let [list-id (luid/head generator)]
       {:result
        (conj
         result
         (cond->
          {:fhir/type :fhir.MeasureReport.group.stratifier.stratum/population
           :count (type/integer (count handles))
           :subjectResults (u/list-reference list-id)}
           code
           (assoc :code code)))
        ::luid/generator (luid/next generator)
        :tx-ops (into tx-ops (u/population-tx-ops list-id handles))}))
   (assoc context :result [])
   populations))

(defn stratum-subject-list
  "Creates a stratum from `value` and `populations` in case subject lists are
  requested.

  Uses ::luid/generator from `context` as generator for list ids and :tx-ops
  from `context` to append the transaction operators for creating the subject
  lists.

  Returns a map of the stratum as :result, the new ::luid/generator and the
  appended :tx-ops."
  [context value populations]
  (update
   (stratum-subject-list-populations context populations)
   :result
   #(cond-> {:fhir/type :fhir.MeasureReport.group.stratifier/stratum
             :value (value-concept value)
             :population %}

      (identical? :fhir/Quantity (type/type value))
      (assoc :extension [(stratum-value-extension value)]))))

(defn- stratifier-path [group-idx stratifier-idx]
  (format "Measure.group[%d].stratifier[%d]" group-idx stratifier-idx))

(defn- reduce-op** [{:keys [report-type] :as context} name]
  (let [stratum-update
        (if (= "subject-list" report-type)
          conj-vec
          (fn [count _handle] (inc (or count 0))))]
    (when-ok [evaluator (cql/stratum-expression-evaluator context name)]
      (fn [db]
        (let [context (assoc context :db db)]
          (fn
            ([] {})
            ([ret handle]
             (if-ok [stratum (evaluator context handle)]
               (update ret stratum stratum-update handle)
               reduced))))))))

(defn- reduce-op*
  [{:keys [group-idx stratifier-idx] :as context} {:keys [criteria]}]
  (let [path-fn #(stratifier-path group-idx stratifier-idx)]
    (when-ok [expression-name (u/expression-name path-fn criteria)]
      (reduce-op** context expression-name))))

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
    (let [path-fn #(stratifier-component-path context)]
      (when-ok [expression-name (u/expression-name path-fn criteria)]
        [code expression-name]))))

(defn- extract-stratifier-component*
  ([_context x] x)
  ([context results [idx component]]
   (if-ok [[code expression-name] (extract-stratifier-component
                                   (assoc context :component-idx idx)
                                   component)]
     (-> (update results :codes conj code)
         (update :expression-names conj expression-name))
     reduced)))

(defn extract-stratifier-components
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

(defn components [codes values]
  (mapv
   (fn [code value]
     (cond-> {:fhir/type :fhir.MeasureReport.group.stratifier.stratum/component
              :code code
              :value (value-concept value)}

       (identical? :fhir/Quantity (type/type value))
       (assoc :extension [(stratum-component-value-extension value)])))
   codes
   values))

(defn multi-component-stratum-count [codes [values populations]]
  {:fhir/type :fhir.MeasureReport.group.stratifier/stratum
   :component (components codes values)
   :population
   (mapv
    (fn [[code count]]
      {:fhir/type :fhir.MeasureReport.group.stratifier.stratum/population
       :code code
       :count (type/integer count)})
    populations)})

(defn multi-component-stratum-subject-list
  "Creates a stratum with multiple components with `codes` from `values` and
  `populations` in case subject lists are requested.

  Uses ::luid/generator from `context` as generator for list ids and :tx-ops
  from `context` to append the transaction operators for creating the subject
  lists.

  Returns a map of the stratum as :result, the new ::luid/generator and the
  appended :tx-ops."
  [codes context values populations]
  (update
   (stratum-subject-list-populations context populations)
   :result
   #(cond-> {:fhir/type :fhir.MeasureReport.group.stratifier/stratum
             :component (components codes values)
             :population %}

      (identical? :fhir/Quantity (type/type values))
      (assoc :extension [(stratum-value-extension values)]))))

(defn- multi-component-reduce-op* [{:keys [report-type] :as context} expression-names]
  (let [stratum-update
        (if (= "subject-list" report-type)
          conj-vec
          (fn [count _handle] (inc (or count 0))))]
    (when-ok [evaluators (cql/stratum-expression-evaluators context expression-names)]
      (fn [db]
        (let [context (assoc context :db db)]
          (fn
            ([] {})
            ([ret handle]
             (if-ok [stratum (cql/evaluate-multi-component-stratum context handle evaluators)]
               (update ret stratum stratum-update handle)
               reduced))))))))

(defn- multi-component-reduce-op
  [context {:keys [component]}]
  (when-ok [{:keys [expression-names]} (extract-stratifier-components context component)]
    (multi-component-reduce-op* context expression-names)))

(defn reduce-op
  "Returns a function that when called with a database will return a reduce
  operator or an anomaly in case of errors.

  The reduce operator will accumulate strata over handles."
  {:arglists '([context stratifier])}
  [context {:keys [component] :as stratifier}]
  (if (seq component)
    (multi-component-reduce-op context stratifier)
    (reduce-op* context stratifier)))
