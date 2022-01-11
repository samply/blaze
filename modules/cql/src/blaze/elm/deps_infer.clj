(ns blaze.elm.deps-infer
  (:require
    [blaze.elm.spec]
    [clojure.set :as set]
    [cuerdas.core :as str]))


(defmulti infer-deps
  "Infers dependencies for `expression`s and annotate them with
  `:life/deps` and `:life/scopes`."
  {:arglists '([expression])}
  (fn [{:keys [type]}]
    (assert type)
    (keyword "elm.deps.type" (str/kebab type))))


(defn- update-expression-defs [expression-defs]
  (mapv #(update % :expression infer-deps) expression-defs))


(defn infer-library-deps [library]
  (update-in library [:statements :def] update-expression-defs))


(defmethod infer-deps :default
  [expression]
  expression)



;; 2. Structured Values

;; 2.1. Tuple
(defmethod infer-deps :elm.deps.type/tuple
  [{elements :element :as expression}]
  (let [elements (mapv #(update % :value infer-deps) elements)]
    (assoc expression
      :element elements
      :life/deps (transduce (comp (map :value) (map :life/deps)) set/union elements)
      :life/scopes (transduce (comp (map :value) (map :life/scopes)) set/union elements))))


;; 2.3. Property
(defmethod infer-deps :elm.deps.type/property
  [{:keys [source scope] :as expression}]
  (let [source (some-> source infer-deps)]
    (cond-> expression
      source
      (assoc :source source
             :life/deps (:life/deps source)
             :life/scopes (:life/scopes source))
      scope
      (assoc :life/scopes #{scope}))))



;; 8. Expressions

;; 8.3. UnaryExpression
(defmethod infer-deps :elm.deps.type/unary-expression
  [{:keys [operand] :as expression}]
  (let [{:life/keys [deps scopes] :as operand} (infer-deps operand)]
    (cond->
      (assoc expression :operand operand)
      deps
      (assoc :life/deps deps)
      scopes
      (assoc :life/scopes scopes))))


;; 8.4. BinaryExpression
;; 8.5. TernaryExpression
;; 8.6. NaryExpression
(defmethod infer-deps :elm.deps.type/nary-expression
  [{operands :operand :as expression}]
  (let [operands (mapv infer-deps operands)
        deps (transduce (map :life/deps) set/union operands)
        scopes (transduce (map :life/scopes) set/union operands)]
    (cond->
      (assoc expression :operand operands)
      deps
      (assoc :life/deps deps)
      scopes
      (assoc :life/scopes scopes))))


;; 8.7. AggregateExpression
(defmethod infer-deps :elm.deps.type/aggregate-expression
  [{:keys [source] :as expression}]
  (let [{:life/keys [deps scopes] :as source} (infer-deps source)]
    (cond->
      (assoc expression :source source)
      deps
      (assoc :life/deps deps)
      scopes
      (assoc :life/scopes scopes))))



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
  (let [{:life/keys [deps scopes] :as expression} (infer-deps expression)]
    (assoc source :expression expression :life/deps deps :life/scopes scopes)))


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
  [{sources :source relationships :relationship :keys [where] :as expression}]
  (let [sources (mapv infer-source-deps sources)
        relationships (mapv infer-relationship-deps relationships)
        where (some-> where infer-deps)
        source-deps (transduce (map :life/deps) set/union sources)
        relationship-deps (transduce (map :life/deps) set/union relationships)
        deps (set/union source-deps relationship-deps (:life/deps where))
        scopes (transduce (map :life/scopes) set/union sources)]
    (cond->
      (assoc expression
        :source sources
        :relationship relationships)
      where
      (assoc :where where)
      (seq deps)
      (assoc :life/deps deps)
      (seq scopes)
      (assoc :life/scopes scopes))))


;; 10.3. AliasRef
(defmethod infer-deps :elm.deps.type/alias-ref
  [{:keys [name] :as expression}]
  (assoc expression :life/scopes #{name}))



;; 12. Comparison Operators

;; 12.1. Equal
(derive :elm.deps.type/equal :elm.deps.type/nary-expression)


;; 12.2. Equivalent
(derive :elm.deps.type/equivalent :elm.deps.type/nary-expression)


;; 12.3. Greater
(derive :elm.deps.type/greater :elm.deps.type/nary-expression)


;; 12.4. GreaterOrEqual
(derive :elm.deps.type/greater-or-equal :elm.deps.type/nary-expression)


;; 12.5. Less
(derive :elm.deps.type/less :elm.deps.type/nary-expression)


;; 12.6. LessOrEqual
(derive :elm.deps.type/less-or-equal :elm.deps.type/nary-expression)



;; 13. Logical Operators

;; 13.1. And
(derive :elm.deps.type/and :elm.deps.type/nary-expression)


;; 13.2. Implies
(derive :elm.deps.type/implies :elm.deps.type/nary-expression)


;; 13.3. Not
(derive :elm.deps.type/not :elm.deps.type/unary-expression)


;; 13.4. Or
(derive :elm.deps.type/or :elm.deps.type/nary-expression)


;; 13.4. Xor
(derive :elm.deps.type/xor :elm.deps.type/nary-expression)



;; 15. Conditional Operators

;; 15.1. Case
(defmethod infer-deps :elm.deps.type/case
  [{:keys [comparand else] items :caseItem :as expression}]
  (let [comparand (some-> comparand infer-deps)
        items (mapv #(-> % (update :when infer-deps) (update :then infer-deps)) items)
        else (infer-deps else)
        item-exprs (into (mapv :when items) (map :then) items)
        all (cond-> (conj item-exprs else) comparand (conj comparand))]
    (cond->
      (assoc expression
        :caseItem items
        :else else
        :life/deps (transduce (map :life/deps) set/union all)
        :life/scopes (transduce (map :life/scopes) set/union all))
      comparand
      (assoc :comparand comparand))))


;; 15.2. If
(defmethod infer-deps :elm.deps.type/if
  [{:keys [condition then else] :as expression}]
  (let [condition (infer-deps condition)
        then (infer-deps then)
        else (infer-deps else)
        all [condition then else]]
    (assoc expression
      :condition condition
      :then then
      :else else
      :life/deps (transduce (map :life/deps) set/union all)
      :life/scopes (transduce (map :life/scopes) set/union all))))



;; 16. Arithmetic Operators

;; 16.1. Abs
(derive :elm.deps.type/abs :elm.deps.type/unary-expression)


;; 16.2. Add
(derive :elm.deps.type/add :elm.deps.type/nary-expression)


;; 16.3. Ceiling
(derive :elm.deps.type/ceiling :elm.deps.type/unary-expression)


;; 16.4. Divide
(derive :elm.deps.type/divide :elm.deps.type/nary-expression)


;; 16.5. Exp
(derive :elm.deps.type/exp :elm.deps.type/unary-expression)


;; 16.6. Floor
(derive :elm.deps.type/floor :elm.deps.type/unary-expression)


;; 16.8. Log
(derive :elm.deps.type/log :elm.deps.type/nary-expression)


;; 16.10. Ln
(derive :elm.deps.type/ln :elm.deps.type/unary-expression)


;; 16.13. Modulo
(derive :elm.deps.type/modulo :elm.deps.type/nary-expression)


;; 16.14. Multiply
(derive :elm.deps.type/multiply :elm.deps.type/nary-expression)


;; 16.15. Negate
(derive :elm.deps.type/negate :elm.deps.type/unary-expression)


;; 16.16. Power
(derive :elm.deps.type/power :elm.deps.type/nary-expression)


;; 16.18. Predecessor
(derive :elm.deps.type/predecessor :elm.deps.type/unary-expression)


;; 16.19. Round
(derive :elm.deps.type/round :elm.deps.type/unary-expression)


;; 16.20. Subtract
(derive :elm.deps.type/subtract :elm.deps.type/nary-expression)


;; 16.21. Successor
(derive :elm.deps.type/successor :elm.deps.type/unary-expression)


;; 16.22. Truncate
(derive :elm.deps.type/truncate :elm.deps.type/unary-expression)


;; 16.23. TruncatedDivide
(derive :elm.deps.type/truncated-divide :elm.deps.type/nary-expression)



;; 17. String Operators

;; 17.1. Combine
(defmethod infer-deps :elm.deps.type/combine
  [{:keys [source separator] :as expression}]
  (let [{source-deps :life/deps source-scopes :life/scopes :as source} (infer-deps source)
        {separator-deps :life/deps separator-scopes :life/scopes :as separator} (some-> separator infer-deps)
        deps (set/union source-deps separator-deps)
        scopes (set/union source-scopes separator-scopes)]
    (cond->
      (assoc expression
        :source source)
      separator
      (assoc :separator separator)
      deps
      (assoc :life/deps deps)
      scopes
      (assoc :life/scopes scopes))))


;; 17.2. Concatenate
(derive :elm.deps.type/concatenate :elm.deps.type/nary-expression)


;; 17.3. EndsWith
(derive :elm.deps.type/ends-with :elm.deps.type/nary-expression)


;; 17.6. Indexer
(derive :elm.deps.type/indexer :elm.deps.type/nary-expression)


;; 17.7. LastPositionOf
(defmethod infer-deps :elm.deps.type/last-position-of
  [{:keys [pattern string] :as expression}]
  (let [{pattern-deps :life/deps pattern-scopes :life/scopes :as source} (infer-deps pattern)
        {string-deps :life/deps string-scopes :life/scopes :as separator} (infer-deps string)
        deps (set/union pattern-deps string-deps)
        scopes (set/union pattern-scopes string-scopes)]
    (cond->
      (assoc expression
        :source source)
      separator
      (assoc :separator separator)
      deps
      (assoc :life/deps deps)
      scopes
      (assoc :life/scopes scopes))))


;; 17.8. Length
(derive :elm.deps.type/length :elm.deps.type/unary-expression)


;; 17.9. Lower
(derive :elm.deps.type/lower :elm.deps.type/unary-expression)


;; 17.10. Matches
(derive :elm.deps.type/matches :elm.deps.type/nary-expression)


;; 17.12. PositionOf
(defmethod infer-deps :elm.deps.type/position-of
  [{:keys [pattern string] :as expression}]
  (let [{pattern-deps :life/deps pattern-scopes :life/scopes :as source} (infer-deps pattern)
        {string-deps :life/deps string-scopes :life/scopes :as string} (infer-deps string)
        deps (set/union pattern-deps string-deps)
        scopes (set/union pattern-scopes string-scopes)]
    (cond->
      (assoc expression
        :source source
        :string string)
      deps
      (assoc :life/deps deps)
      scopes
      (assoc :life/scopes scopes))))


;; 17.13. ReplaceMatches
(derive :elm.deps.type/replace-matches :elm.deps.type/nary-expression)


;; 17.14. Split
(defmethod infer-deps :elm.deps.type/split
  [{source :stringToSplit :keys [separator] :as expression}]
  (let [{source-deps :life/deps source-scopes :life/scopes :as source} (infer-deps source)
        {separator-deps :life/deps separator-scopes :life/scopes :as separator} (some-> separator infer-deps)
        deps (set/union source-deps separator-deps)
        scopes (set/union source-scopes separator-scopes)]
    (cond->
      (assoc expression
        :stringToSplit source)
      separator
      (assoc :separator separator)
      deps
      (assoc :life/deps deps)
      scopes
      (assoc :life/scopes scopes))))


;; 17.15. SplitOnMatches
(defmethod infer-deps :elm.deps.type/split-on-matches
  [{source :stringToSplit separator :separatorPattern :as expression}]
  (let [{source-deps :life/deps source-scopes :life/scopes :as source} (infer-deps source)
        {separator-deps :life/deps separator-scopes :life/scopes :as separator} (infer-deps separator)
        deps (set/union source-deps separator-deps)
        scopes (set/union source-scopes separator-scopes)]
    (cond->
      (assoc expression
        :stringToSplit source
        :separatorPattern separator)
      deps
      (assoc :life/deps deps)
      scopes
      (assoc :life/scopes scopes))))


;; 17.16. StartsWith
(derive :elm.deps.type/starts-with :elm.deps.type/nary-expression)


;; 17.17. Substring
(defmethod infer-deps :elm.deps.type/substring
  [{source :stringToSub start :startIndex :keys [length] :as expression}]
  (let [{source-deps :life/deps source-scopes :life/scopes :as source} (infer-deps source)
        {start-deps :life/deps start-scopes :life/scopes :as start} (infer-deps start)
        {length-deps :life/deps length-scopes :life/scopes :as length} (some-> length infer-deps)
        deps (set/union source-deps start-deps length-deps)
        scopes (set/union source-scopes start-scopes length-scopes)]
    (cond->
      (assoc expression
        :stringToSub source
        :startIndex start)
      deps
      (assoc :life/deps deps)
      length
      (assoc :length length)
      scopes
      (assoc :life/scopes scopes))))


;; 17.18. Upper
(derive :elm.deps.type/upper :elm.deps.type/unary-expression)



;; 18. Date and Time Operators

;; 18.11. DurationBetween
(derive :elm.deps.type/duration-between :elm.deps.type/nary-expression)



;; 19. Interval Operators

;; 19.1. Interval
(defmethod infer-deps :elm.deps.type/interval
  [{:keys [low high]
    low-closed-expression :lowClosedExpression
    high-closed-expression :highClosedExpression
    :as expression}]
  (let [low (some-> low infer-deps)
        high (some-> high infer-deps)
        low-closed-expression (some-> low-closed-expression infer-deps)
        high-closed-expression (some-> high-closed-expression infer-deps)
        all [low high low-closed-expression high-closed-expression]]
    (cond->
      (assoc expression
        :life/deps (transduce (map :life/deps) set/union all)
        :life/scopes (transduce (map :life/scopes) set/union all))
      low
      (assoc :low low)
      high
      (assoc :high high)
      low-closed-expression
      (assoc :lowClosedExpression low-closed-expression)
      high-closed-expression
      (assoc :highClosedExpression high-closed-expression))))


;; 19.15. Intersect
(derive :elm.deps.type/intersect :elm.deps.type/nary-expression)


;; 19.31. Union
(derive :elm.deps.type/union :elm.deps.type/nary-expression)



;; 20. List Operators

;; 20.8. Exists
(derive :elm.deps.type/exists :elm.deps.type/unary-expression)


;; 20.10. First
(defmethod infer-deps :elm.deps.type/first
  [expression]
  (update expression :source infer-deps))


;; 20.25. SingletonFrom
(derive :elm.deps.type/singleton-from :elm.deps.type/unary-expression)


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


;; 22.22. ToDate
(derive :elm.deps.type/to-date :elm.deps.type/unary-expression)


;; 22.23. ToDateTime
(derive :elm.deps.type/to-date-time :elm.deps.type/unary-expression)


;; 22.26. ToList
(derive :elm.deps.type/to-list :elm.deps.type/unary-expression)


;; 22.28. ToQuantity
(derive :elm.deps.type/to-quantity :elm.deps.type/unary-expression)



;; 23. Clinical Operators

;; 23.4. CalculateAgeAt
(derive :elm.deps.type/calculate-age-at :elm.deps.type/nary-expression)
