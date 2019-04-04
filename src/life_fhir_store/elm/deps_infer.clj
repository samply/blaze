(ns life-fhir-store.elm.deps-infer
  (:require
    [camel-snake-kebab.core :refer [->kebab-case-string]]
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [life-fhir-store.elm.spec]))


(defmulti infer-deps
  "Infers dependencies for `expression`s and annotate them with
  `:life/deps`."
  {:arglists '([expression])}
  (fn [{:keys [type]}]
    (assert type)
    (keyword "elm.deps.type" (->kebab-case-string type))))


(defn- update-expression-defs [expression-defs]
  (mapv #(update % :expression infer-deps) expression-defs))


(s/fdef infer-library-deps
  :args (s/cat :library :elm/library))

(defn infer-library-deps [library]
  (update-in library [:statements :def] update-expression-defs))


(defmethod infer-deps :default
  [expression]
  expression)



;; 2. Structured Values

;; 2.3. Property
(defmethod infer-deps :elm.deps.type/property
  [{:keys [source scope] :as expression}]
  (let [source (some-> source infer-deps)]
    (cond-> expression
      source
      (assoc :source source :life/deps (:life/deps source))
      scope
      (assoc :life/scopes #{scope}))))



;; 8. Expressions

;; 8.3. UnaryExpression
(defmethod infer-deps :elm.deps.type/unary-expression
  [{:keys [operand] :as expression}]
  (let [{:life/keys [deps scopes] :as operand} (infer-deps operand)]
    (assoc expression
      :operand operand
      :life/deps deps
      :life/scopes scopes)))


;; 8.4. BinaryExpression
;; 8.5. TernaryExpression
;; 8.6. NaryExpression
(defmethod infer-deps :elm.deps.type/multiary-expression
  [{operands :operand :as expression}]
  (let [operands (mapv infer-deps operands)]
    (assoc expression
      :operand operands
      :life/deps (transduce (map :life/deps) set/union operands)
      :life/scopes (transduce (map :life/scopes) set/union operands))))


;; 8.7. AggregateExpression
(defmethod infer-deps :elm.deps.type/aggregate-expression
  [{:keys [source] :as expression}]
  (let [{:life/keys [deps] :as source} (infer-deps source)]
    (assoc expression :source source :life/deps deps)))



;; 9. Reusing Logic

;; 9.2. ExpressionRef
(defmethod infer-deps :elm.deps.type/expression-ref
  [{:keys [name] :as expression}]
  (assoc expression :life/deps #{name}))


;; 9.4. FunctionRef
(defmethod infer-deps :elm.deps.type/function-ref
  [{operands :operand :as expression}]
  (let [operands (mapv infer-deps operands)]
    (assoc expression
      :operand operands
      :life/deps (transduce (map :life/deps) set/union operands)
      :life/scopes (transduce (map :life/scopes) set/union operands))))



;; 10. Queries

;; 10.1. Query
(defn- infer-source-deps [{:keys [expression] :as source}]
  (let [{:life/keys [deps] :as expression} (infer-deps expression)]
    (assoc source :expression expression :life/deps deps)))


(defn- infer-relationship-deps
  [{source :expression equiv-operands :equivOperand such-that :suchThat
    :as relationship}]
  (let [{source-deps :life/deps :as source} (infer-deps source)
        equiv-operands (mapv infer-deps equiv-operands)
        {such-that-deps :life/deps :as such-that} (some-> such-that infer-deps)]
    (cond->
      (assoc (infer-source-deps relationship)
        :expression source
        :life/deps (apply set/union source-deps such-that-deps (map :life/deps equiv-operands)))
      (seq equiv-operands)
      (assoc :equivOperand equiv-operands)
      (some? such-that)
      (assoc :suchThat such-that))))


(defmethod infer-deps :elm.deps.type/query
  [{sources :source relationships :relationship :as expression}]
  (let [sources (mapv infer-source-deps sources)
        relationships (mapv infer-relationship-deps relationships)
        source-deps (transduce (map :life/deps) set/union sources)
        relationship-deps (transduce (map :life/deps) set/union relationships)
        deps (set/union source-deps relationship-deps)]
    (assoc expression
      :source sources
      :relationship relationships
      :life/deps deps)))


;; 10.3. AliasRef
(defmethod infer-deps :elm.deps.type/alias-ref
  [{:keys [name] :as expression}]
  (assoc expression :life/scopes #{name}))



;; 12. Comparison Operators

;; 12.1. Equal
(derive :elm.deps.type/equal :elm.deps.type/multiary-expression)


;; 12.2. Equivalent
(derive :elm.deps.type/equivalent :elm.deps.type/multiary-expression)


;; 12.3. Greater
(derive :elm.deps.type/greater :elm.deps.type/multiary-expression)


;; 12.4. GreaterOrEqual
(derive :elm.deps.type/greater-or-equal :elm.deps.type/multiary-expression)


;; 12.5. Less
(derive :elm.deps.type/less :elm.deps.type/multiary-expression)


;; 12.6. LessOrEqual
(derive :elm.deps.type/less-or-equal :elm.deps.type/multiary-expression)


;; 12.7. NotEqual
(derive :elm.deps.type/not-equal :elm.deps.type/multiary-expression)



;; 13. Logical Operators

;; 13.1. And
(derive :elm.deps.type/and :elm.deps.type/multiary-expression)


;; 13.2. Implies
(derive :elm.deps.type/implies :elm.deps.type/multiary-expression)


;; 13.3. Not
(derive :elm.deps.type/not :elm.deps.type/unary-expression)


;; 13.4. Or
(derive :elm.deps.type/or :elm.deps.type/multiary-expression)


;; 13.4. Xor
(derive :elm.deps.type/xor :elm.deps.type/multiary-expression)



;; 16. Arithmetic Operators

;; 16.1. Abs
(derive :elm.deps.type/abs :elm.deps.type/unary-expression)



;; 18. Date and Time Operators

;; 18.11. DurationBetween
(derive :elm.deps.type/duration-between :elm.deps.type/multiary-expression)



;; 19. Interval Operators

;; 19.15. Intersect
(derive :elm.deps.type/intersect :elm.deps.type/multiary-expression)


;; 19.30. Union
(derive :elm.deps.type/union :elm.deps.type/multiary-expression)



;; 21. Aggregate Operators

;; 21.1. AllTrue
(derive :elm.deps.type/all-true :elm.deps.type/aggregate-expression)


;; 21.2. AnyTrue
(derive :elm.deps.type/any-true :elm.deps.type/aggregate-expression)


;; 21.3. Avg
(derive :elm.deps.type/avg :elm.deps.type/aggregate-expression)


;; 21.4. Count
(derive :elm.deps.type/count :elm.deps.type/aggregate-expression)


;; 21.5. GeometricMean
(derive :elm.deps.type/geometric-mean :elm.deps.type/aggregate-expression)


;; 21.6. Product
(derive :elm.deps.type/product :elm.deps.type/aggregate-expression)


;; 21.7. Max
(derive :elm.deps.type/max :elm.deps.type/aggregate-expression)


;; 21.8. Median
(derive :elm.deps.type/median :elm.deps.type/aggregate-expression)


;; 21.9. Min
(derive :elm.deps.type/min :elm.deps.type/aggregate-expression)


;; 21.10. Mode
(derive :elm.deps.type/mode :elm.deps.type/aggregate-expression)


;; 21.11. PopulationVariance
(derive :elm.deps.type/population-variance :elm.deps.type/aggregate-expression)


;; 21.12. PopulationStdDev
(derive :elm.deps.type/population-std-dev :elm.deps.type/aggregate-expression)


;; 21.13. Sum
(derive :elm.deps.type/sum :elm.deps.type/aggregate-expression)


;; 21.14. StdDev
(derive :elm.deps.type/std-dev :elm.deps.type/aggregate-expression)


;; 21.15. Variance
(derive :elm.deps.type/variance :elm.deps.type/aggregate-expression)



;; 22. Type Operators

;; 22.1. As
(derive :elm.deps.type/as :elm.deps.type/unary-expression)
