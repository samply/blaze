(ns blaze.elm.type-infer
  (:require
    [blaze.elm.spec]
    [clojure.spec.alpha :as s]
    [cuerdas.core :as str]))


(defmulti infer-types*
  "Infers :life/source-type for expressions."
  {:arglists '([context expression])}
  (fn [_ {:keys [type]}]
    (keyword "elm" (str/kebab type))))


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

;; 2.1. Tuple
(defmethod infer-types* :elm/tuple
  [context expression]
  (let [infer-types #(infer-types* context %)
        infer-types #(update % :value infer-types)]
    (update expression :element #(mapv infer-types %))))


;; 2.3. Property
(defmethod infer-types* :elm/property
  [context {:keys [source scope] :as expression}]
  (let [source (some->> source (infer-types context))
        scope-type (get-in context [:life/scope-types scope])]
    (cond-> expression
      source
      (assoc :source source)
      scope-type
      (assoc :life/source-type scope-type))))



;; 9. Reusing Logic

;; 9.2. ExpressionRef
(defn- find-by-name [name coll]
  (first (filter (comp #{name} :name) coll)))

(defmethod infer-types* :elm/expression-ref
  [{{{expression-defs :def} :statements} :library}
   {:keys [name] :as expression}]
  ;; TODO: look into other libraries (:libraryName)
  (if-let [{eval-context :context} (find-by-name name expression-defs)]
    (assoc expression :life/eval-context eval-context)
    (throw (ex-info (str "Missing expression-def `" name "`.")
                    {:expression-ref expression
                     :expression-defs expression-defs}))))


;; 9.4. FunctionRef
(defmethod infer-types* :elm/function-ref
  [context expression]
  (update expression :operand #(mapv (partial infer-types context) %)))



;; 10. Queries

;; 10.1. Query
(defn- infer-source-type [context source]
  (update source :expression #(infer-types context %)))


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
    (let [return (infer-types context return)]
      (assoc-in expression [:return :expression] return))
    (let [first-source (infer-types context first-source)]
      (assoc-in expression [:source 0 :expression] first-source))))


(defn- extract-scope-types [sources]
  (reduce
    (fn [r {:keys [alias] {result-type :resultTypeSpecifier} :expression}]
      (assoc r alias (-> result-type :elementType :name)))
    {}
    sources))


(defmethod infer-types* :elm/query
  [context {sources :source :as expression}]
  (let [sources (mapv #(infer-source-type context %) sources)
        context (assoc context :life/scope-types (extract-scope-types sources))]
    (->> (assoc expression :source sources)
         (infer-all-relationship-types context)
         (infer-query-where-type context)
         (infer-query-return-type context))))


(defmethod infer-types* :elm/unary-expression
  [context expression]
  (update expression :operand #(infer-types context %)))


(defmethod infer-types* :elm/multiary-expression
  [context expression]
  (update expression :operand #(mapv (partial infer-types context) %)))



;; 12. Comparison Operators

(derive :elm/equal :elm/multiary-expression)
(derive :elm/equivalent :elm/multiary-expression)
(derive :elm/greater :elm/multiary-expression)
(derive :elm/greater-or-equal :elm/multiary-expression)
(derive :elm/less :elm/multiary-expression)
(derive :elm/less-or-equal :elm/multiary-expression)



;; 13. Logical Operators

(derive :elm/and :elm/multiary-expression)
(derive :elm/implies :elm/multiary-expression)
(derive :elm/or :elm/multiary-expression)
(derive :elm/xor :elm/multiary-expression)
(derive :elm/not :elm/unary-expression)



;; 15. Conditional Operators

;; 15.1. Case
(defn- infer-item-types [context]
  #(-> %
       (update :when (partial infer-types context))
       (update :then (partial infer-types context))))


(defmethod infer-types* :elm/case
  [context {:keys [comparand] :as expression}]
  (cond->
    (-> expression
        (update :caseItem #(mapv (infer-item-types context) %))
        (update :else #(infer-types context %)))
    comparand
    (assoc :comparand (infer-types context comparand))))


;; 15.2. If
(defmethod infer-types* :elm/if
  [context expression]
  (-> expression
      (update :condition #(infer-types context %))
      (update :then #(infer-types context %))
      (update :else #(infer-types context %))))



;; 16. Arithmetic Operators

;; 16.1. Abs
(derive :elm/abs :elm/unary-expression)


;; 16.2. Add
(derive :elm/add :elm/multiary-expression)


;; 16.3. Ceiling
(derive :elm/ceiling :elm/unary-expression)


;; 16.4. Divide
(derive :elm/divide :elm/multiary-expression)


;; 16.5. Exp
(derive :elm/exp :elm/unary-expression)


;; 16.6. Floor
(derive :elm/floor :elm/unary-expression)


;; 16.7. Log
(derive :elm/log :elm/multiary-expression)


;; 16.8. Ln
(derive :elm/ln :elm/unary-expression)


;; 16.11. Modulo
(derive :elm/modulo :elm/multiary-expression)


;; 16.12. Multiply
(derive :elm/multiply :elm/multiary-expression)


;; 16.13. Negate
(derive :elm/negate :elm/unary-expression)


;; 16.14. Power
(derive :elm/power :elm/multiary-expression)


;; 16.15. Predecessor
(derive :elm/predecessor :elm/unary-expression)


;; 16.16. Round
(derive :elm/round :elm/unary-expression)


;; 16.17. Subtract
(derive :elm/subtract :elm/multiary-expression)


;; 16.18. Successor
(derive :elm/successor :elm/unary-expression)


;; 16.19. Truncate
(derive :elm/truncate :elm/unary-expression)


;; 16.20. TruncatedDivide
(derive :elm/truncated-divide :elm/multiary-expression)



;; 18. Date and Time Operators

;; 18.6. Date
(defmethod infer-types* :elm/date
  [context {:keys [year month day] :as expression}]
  (cond-> expression
    year
    (assoc :year (infer-types context year))
    month
    (assoc :month (infer-types context month))
    day
    (assoc :day (infer-types context day))))


;; 18.11. DurationBetween
(derive :elm/duration-between :elm/multiary-expression)



;; 19. Interval Operators

;; 19.1. Interval
(defmethod infer-types* :elm/interval
  [context
   {:keys [low high]
    low-closed-expression :lowClosedExpression
    high-closed-expression :highClosedExpression
    :as expression}]
  (cond-> expression
    low
    (assoc :low (infer-types context low))
    high
    (assoc :high (infer-types context high))
    low-closed-expression
    (assoc :lowClosedExpression (infer-types context low-closed-expression))
    high-closed-expression
    (assoc :highClosedExpression (infer-types context high-closed-expression))))


;; 19.5. Contains
(derive :elm/contains :elm/multiary-expression)



;; 20. List Operators

;; 20.8. Exists
(derive :elm/exists :elm/unary-expression)


;; 20.25. SingletonFrom
(derive :elm/singleton-from :elm/unary-expression)



;; 21. Aggregate Operators

;; 21.4. Count
(defmethod infer-types* :elm/count
  [context expression]
  (update expression :source #(infer-types context %)))


;; 22. Type Operators

;; 22.1. As
(derive :elm/as :elm/unary-expression)


;; 22.21. ToDate
(derive :elm/to-date :elm/unary-expression)


;; 22.22. ToDateTime
(derive :elm/to-date-time :elm/unary-expression)


;; 22.25. ToList
(derive :elm/to-list :elm/unary-expression)


;; 22.26. ToQuantity
(derive :elm/to-quantity :elm/unary-expression)



;; 23. Clinical Operators

;; 23.4. CalculateAgeAt
(derive :elm/calculate-age-at :elm/multiary-expression)
