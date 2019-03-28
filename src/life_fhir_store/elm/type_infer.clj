(ns life-fhir-store.elm.type-infer
  (:require
    [camel-snake-kebab.core :refer [->kebab-case-string]]
    [clojure.spec.alpha :as s]
    [life-fhir-store.elm.spec]
    [life-fhir-store.elm.util :as u]))


(defmulti infer-types*
  "Infers types for `expression`s and uses TypeSpecifiers in
  `:life/return-type` to annotate them."
  {:arglists '([context expression])}
  (fn [_ {:keys [type]}]
    (keyword "elm" (->kebab-case-string type))))


(s/fdef infer-types
  :args (s/cat :context any? :expression :elm/expression))

(defn infer-types [context expression]
  (infer-types* context expression))


(s/fdef update-expression-def
  :args (s/cat :defs (s/coll-of :elm/expression-def)
               :name :elm/name
               :expression-def :elm/expression))

(defn- update-expression-def [defs name expression]
  (mapv
    (fn [def]
      (if (= name (:name def))
        (assoc def :expression expression)
        def))
    defs))


(s/fdef infer-expression-def-types
  :args (s/cat :context (s/keys :req-un [:elm/library])
               :expression-def :elm/expression-def))

(defn- infer-expression-def-types
  {:arglists '([context expression-def])}
  [context {:keys [name expression] eval-context :context}]
  (let [expression (infer-types (assoc context :eval-context eval-context) expression)]
    (update-in context [:library :statements :def]
               update-expression-def name expression)))


(defn infer-library-types
  [{{expression-defs :def} :statements :as library}]
  (:library (reduce infer-expression-def-types {:library library} expression-defs)))


(defmethod infer-types* :default
  [_ expression]
  expression)


(defn named-type-specifier [name]
  {:type "NamedTypeSpecifier" :name name})


(defn elm-type-specifier [elm-name]
  (named-type-specifier (str "{urn:hl7-org:elm-types:r1}" elm-name)))


(defn list-type-specifier [element-type]
  {:type "ListTypeSpecifier" :elementType element-type})


(defn named-list-type-specifier [name]
  (list-type-specifier (named-type-specifier name)))


;; 2. Structured Values

;; 2.3. Property
(defn- infer-path-type [type path]
  (let [[ns name] (u/parse-qualified-name type)]
    (case ns
      "http://hl7.org/fhir"
      (case name
        "Observation"
        (case path
          "subject"
          "{http://hl7.org/fhir}Patient"
          "effective"
          "{http://hl7.org/fhir}dateTime"                   ;; TODO: HACK
          nil)
        nil)
      nil)))

(defn- infer-property-return-type [expression name path]
  (let [type (infer-path-type name path)]
    (cond-> expression
      type
      (assoc :life/return-type (named-type-specifier type)))))

(defmethod infer-types* :elm/property
  [context {:keys [source scope path] :as expression}]
  (let [{source-type :life/return-type :as source}
        (some->> source (infer-types context))
        scope-type (get-in context [:life/scope-types scope])]
    (cond-> expression
      source
      (assoc :source source)
      scope-type
      (infer-property-return-type scope-type path)
      source-type
      (assoc :life/source-type (:name source-type))
      scope-type
      (assoc :life/source-type scope-type))))



;; 9. Reusing Logic

;; 9.2. ExpressionRef
(defn- find-by-name [name coll]
  (first (filter #(= name (:name %)) coll)))

(defn- resolve-expression-def-return-type
  {:arglists '([context expression-def])}
  [{:keys [eval-context]}
   {{:life/keys [return-type]} :expression def-eval-context :context}]
  (when return-type
    (if (and (= "Population" eval-context) (= "Patient" def-eval-context))
      (list-type-specifier return-type)
      return-type)))

(defmethod infer-types* :elm/expression-ref
  [{{{expression-defs :def} :statements} :library :as context}
   {:keys [name] :as expression}]
  ;; TODO: look into other libraries (:libraryName)
  (let [{eval-context :context :as expression-def} (find-by-name name expression-defs)
        return-type (resolve-expression-def-return-type context expression-def)]
    (cond-> expression
      return-type
      (assoc :life/return-type return-type)
      eval-context
      (assoc :life/eval-context eval-context))))


;; 9.4. FunctionRef
(defn- infer-function-ref-return-type
  [{:keys [name] :as expression}]
  ;; TODO: look into other libraries (:libraryName)
  (case name
    "ToQuantity"
    (assoc expression :life/return-type (elm-type-specifier "Quantity"))
    expression))

(defmethod infer-types* :elm/function-ref
  [context expression]
  (-> expression
      (update :operand #(mapv (partial infer-types context) %))
      (infer-function-ref-return-type)))



;; 10. Queries

;; 10.1. Query
(defn- infer-source-type
  "Infers the types on query sources and assocs them into the context under
  [:life/scope-types `alias`]."
  [context {:keys [alias expression]}]
  (let [{{scope-type :elementType} :life/return-type} (infer-types context expression)]
    (cond-> context
      scope-type
      (assoc-in [:life/scope-types alias] (:name scope-type)))))


(defn- infer-relationship-types
  [context {equiv-operands :equivOperand such-that :suchThat :as relationship}]
  (let [context (infer-source-type context relationship)]
    (cond-> relationship
      (seq equiv-operands)
      (update :equivOperand #(mapv (partial infer-types context) %))
      (some? such-that)
      (update :suchThat #(infer-types context %)))))


(defn- infer-all-relationship-types
  [context expression]
  (update expression :relationship (partial mapv (partial infer-relationship-types context))))


(defn- infer-query-where-type
  [context {:keys [where] :as expression}]
  (cond-> expression
    where
    (assoc :where (infer-types context where))))


(defn- infer-query-return-type
  [context
   {{return :expression} :return
    [{first-source :expression}] :source
    :as expression}]
  (if return
    (let [{:life/keys [return-type] :as return} (infer-types context return)]
      (cond-> (assoc-in expression [:return :expression] return)
        return-type
        (assoc :life/return-type (list-type-specifier return-type))))
    (let [{:life/keys [return-type] :as first-source} (infer-types context first-source)]
      (cond-> (assoc-in expression [:source 0 :expression] first-source)
        return-type
        (assoc :life/return-type return-type)))))


(defmethod infer-types* :elm/query
  [context {sources :source :as expression}]
  (let [context (reduce infer-source-type context sources)]
    (->> expression
         (infer-all-relationship-types context)
         (infer-query-where-type context)
         (infer-query-return-type context))))



;; 11. External Data

;; 11.1. Retrieve
(defmethod infer-types* :elm/retrieve
  [_ {data-type :dataType :as expression}]
  (assoc expression :life/return-type (named-list-type-specifier data-type)))



;; 12. Comparison Operators

(derive :elm/equal :elm/comparison-operator)
(derive :elm/equivalent :elm/comparison-operator)
(derive :elm/greater :elm/comparison-operator)
(derive :elm/greater-or-equal :elm/comparison-operator)
(derive :elm/less :elm/comparison-operator)
(derive :elm/less-or-equal :elm/comparison-operator)
(derive :elm/not-equal :elm/comparison-operator)

(defmethod infer-types* :elm/comparison-operator
  [context expression]
  (-> expression
      (update :operand #(mapv (partial infer-types context) %))
      (assoc :life/return-type (named-type-specifier "{urn:hl7-org:elm-types:r1}Boolean"))))


;; 13. Logical Operators

(derive :elm/and :elm/binary-logical-operator)
(derive :elm/implies :elm/binary-logical-operator)
(derive :elm/or :elm/binary-logical-operator)
(derive :elm/xor :elm/binary-logical-operator)

(defmethod infer-types* :elm/binary-logical-operator
  [context expression]
  (-> expression
      (update :operand #(mapv (partial infer-types context) %))
      (assoc :life/return-type (named-type-specifier "{urn:hl7-org:elm-types:r1}Boolean"))))

(defmethod infer-types* :elm/not
  [context expression]
  (-> expression
      (update :operand #(infer-types context %))
      (assoc :life/return-type (named-type-specifier "{urn:hl7-org:elm-types:r1}Boolean"))))



;; 16. Arithmetic Operators

;; 16.1. Abs
(defmethod infer-types* :elm/abs
  [context {:keys [operand] :as expression}]
  (let [{:life/keys [return-type] :as operand} (infer-types context operand)]
    (assoc expression :operand operand :life/return-type return-type)))



;; 18. Date and Time Operators

;; 18.11. DurationBetween
(defmethod infer-types* :elm/duration-between
  [context expression]
  (-> expression
      (update :operand #(mapv (partial infer-types context) %))
      (assoc :life/return-type (named-type-specifier "{urn:hl7-org:elm-types:r1}Integer"))))



;; 20. List Operators

;; 20.25. SingletonFrom
(defmethod infer-types* :elm/singleton-from
  [context {:keys [operand] :as expression}]
  (let [{{element-type :elementType} :life/return-type :as operand}
        (infer-types context operand)]
    (cond-> (assoc expression :operand operand)
      element-type
      (assoc :life/return-type element-type))))


;; 21. Aggregate Operators

;; 21.4. Count
(defmethod infer-types* :elm/count
  [context {:keys [source] :as expression}]
  (assoc expression
    :source (infer-types context source)
    :life/return-type (elm-type-specifier "Integer")))


;; 22. Type Operators

;; 22.1. As
(defmethod infer-types* :elm/as
  [context {as-type :asType as-type-specifier :asTypeSpecifier :as expression}]
  (cond-> (update expression :operand #(infer-types context %))
    as-type
    (assoc :life/return-type (named-type-specifier as-type))
    as-type-specifier
    (assoc :life/return-type as-type-specifier)))

;; 22.24. ToQuantity
(defmethod infer-types* :elm/to-quantity
  [_ expression]
  (assoc expression :life/return-type (elm-type-specifier "Quantity")))
