(ns blaze.elm.normalizer
  (:require
   [blaze.elm.spec]
   [blaze.elm.util :as elm-util]))

(defmulti normalize
  {:arglists '([expression])}
  (fn [{:keys [type]}]
    (assert type)
    (keyword "elm.normalizer.type" (elm-util/pascal->kebab type))))

(defn- normalize-expression [x]
  (update x :expression normalize))

(defn- update-expression-defs [expression-defs]
  (mapv normalize-expression expression-defs))

(defn- normalize-parameter-def [x]
  (cond-> x
    (:default x) (update :default normalize)))

(defn- normalize-sort-by-item [item]
  (if (= "ByExpression" (:type item))
    (update item :expression normalize)
    item))

(defn normalize-library [library]
  (cond-> library
    (get-in library [:statements :def])
    (update-in [:statements :def] update-expression-defs)
    (get-in library [:parameters :def])
    (update-in [:parameters :def] (partial mapv normalize-parameter-def))))

(defmethod normalize :default
  [expression]
  expression)

(defn- un-pred [name operand]
  {:type name
   :operand operand
   :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"})

(defn- bin-pred [name operand-1 operand-2]
  {:type name
   :operand [operand-1 operand-2]
   :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"})

;; 2. Structured Values

;; 2.1. Tuple
(defmethod normalize :elm.normalizer.type/tuple
  [expression]
  (update expression :element (partial mapv #(update % :value normalize))))

;; 2.2. Instance
(defmethod normalize :elm.normalizer.type/instance
  [expression]
  (update expression :element (partial mapv #(update % :value normalize))))

;; 2.3. Property
(defmethod normalize :elm.normalizer.type/property
  [{:keys [source] :as expression}]
  (let [source (some-> source normalize)]
    (cond-> expression
      source
      (assoc :source source))))

;; 8. Expressions

;; 8.3. UnaryExpression
(defmethod normalize :elm.normalizer.type/unary-expression
  [{:keys [operand] :as expression}]
  (assoc expression :operand (normalize operand)))

;; 8.4. BinaryExpression
;; 8.5. TernaryExpression
;; 8.6. NaryExpression
(defmethod normalize :elm.normalizer.type/multiary-expression
  [{:keys [operand] :as expression}]
  (assoc expression :operand (mapv normalize operand)))

;; 9. Reusing Logic

;; 9.4. FunctionRef
(defmethod normalize :elm.normalizer.type/function-ref
  [expression]
  (update expression :operand (partial mapv normalize)))

;; 10. Queries

;; 10.1. Query
(defmethod normalize :elm.normalizer.type/query
  [{:keys [source relationship where return] let' :let :as expression}]
  (cond-> (assoc expression :source (mapv normalize-expression source))
    let'
    (assoc :let (mapv normalize-expression let'))

    relationship
    (assoc :relationship (mapv normalize-expression relationship))

    where
    (assoc :where (normalize where))

    return
    (assoc :return (update return :expression normalize))))

;; 11. External Data

;; 11.1. Retrieve
(defmethod normalize :elm.normalizer.type/retrieve
  [{:keys [codes dateRange context] :as expression}]
  (cond-> expression
    codes (update :codes normalize)
    dateRange (update :dateRange normalize)
    context (update :context normalize)))

;; 12. Comparison Operators

;; 12.1. Equal
(derive :elm.normalizer.type/equal :elm.normalizer.type/multiary-expression)

;; 12.2. Equivalent
(derive :elm.normalizer.type/equivalent :elm.normalizer.type/multiary-expression)

;; 12.3. Greater
(derive :elm.normalizer.type/greater :elm.normalizer.type/multiary-expression)

;; 12.4. GreaterOrEqual
(derive :elm.normalizer.type/greater-or-equal :elm.normalizer.type/multiary-expression)

;; 12.5. Less
(derive :elm.normalizer.type/less :elm.normalizer.type/multiary-expression)

;; 12.6. LessOrEqual
(derive :elm.normalizer.type/less-or-equal :elm.normalizer.type/multiary-expression)

;; 12.7. NotEqual
(defmethod normalize :elm.normalizer.type/not-equal
  [{[operand-1 operand-2] :operand}]
  (let [operand-1 (normalize operand-1)
        operand-2 (normalize operand-2)]
    (un-pred "Not" (bin-pred "Equal" operand-1 operand-2))))

;; 13. Logical Operators

;; 13.1 And
(derive :elm.normalizer.type/and :elm.normalizer.type/multiary-expression)

;; 13.2 Implies
(defmethod normalize :elm.normalizer.type/implies
  [{[operand-1 operand-2] :operand}]
  (let [operand-1 (normalize operand-1)
        operand-2 (normalize operand-2)]
    (bin-pred "Or" (un-pred "Not" operand-1) operand-2)))

;; 13.3. Not
(derive :elm.normalizer.type/not :elm.normalizer.type/unary-expression)

;; 13.4 Or
(derive :elm.normalizer.type/or :elm.normalizer.type/multiary-expression)

;; 13.5 Xor
(derive :elm.normalizer.type/xor :elm.normalizer.type/multiary-expression)

;; 14. Nullological Operators

;; 14.2. Coalesce
(derive :elm.normalizer.type/coalesce :elm.normalizer.type/multiary-expression)

;; 14.3. IsFalse
(derive :elm.normalizer.type/is-false :elm.normalizer.type/unary-expression)

;; 14.4. IsNull
(derive :elm.normalizer.type/is-null :elm.normalizer.type/unary-expression)

;; 14.3. IsTrue
(derive :elm.normalizer.type/is-true :elm.normalizer.type/unary-expression)

;; 15. Conditional Operators

;; 15.1. Case
(defn- normalize-case-item [item]
  (-> (update item :when normalize)
      (update :then normalize)))

(defmethod normalize :elm.normalizer.type/case
  [{:keys [comparand] :as expression}]
  (cond->
   (-> (update expression :caseItem (partial mapv normalize-case-item))
       (update :else normalize))
    comparand
    (assoc :comparand (normalize comparand))))

;; 15.2. If
(defmethod normalize :elm.normalizer.type/if
  [expression]
  (-> (update expression :condition normalize)
      (update :then normalize)
      (update :else normalize)))

;; 16. Arithmetic Operators

;; 16.1. Abs
(derive :elm.normalizer.type/abs :elm.normalizer.type/unary-expression)

;; 16.2. Add
(derive :elm.normalizer.type/add :elm.normalizer.type/multiary-expression)

;; 16.3. Ceiling
(derive :elm.normalizer.type/ceiling :elm.normalizer.type/unary-expression)

;; 16.4. Divide
(derive :elm.normalizer.type/divide :elm.normalizer.type/multiary-expression)

;; 16.5. Exp
(derive :elm.normalizer.type/exp :elm.normalizer.type/unary-expression)

;; 16.6. Floor
(derive :elm.normalizer.type/floor :elm.normalizer.type/unary-expression)

;; 16.7. HighBoundary
(derive :elm.normalizer.type/high-boundary :elm.normalizer.type/multiary-expression)

;; 16.8. Log
(derive :elm.normalizer.type/log :elm.normalizer.type/multiary-expression)

;; 16.9. LowBoundary
(derive :elm.normalizer.type/low-boundary :elm.normalizer.type/multiary-expression)

;; 16.10. Ln
(derive :elm.normalizer.type/ln :elm.normalizer.type/unary-expression)

;; 16.13. Modulo
(derive :elm.normalizer.type/modulo :elm.normalizer.type/multiary-expression)

;; 16.14. Multiply
(derive :elm.normalizer.type/multiply :elm.normalizer.type/multiary-expression)

;; 16.15. Negate
(derive :elm.normalizer.type/negate :elm.normalizer.type/unary-expression)

;; 16.16. Power
(derive :elm.normalizer.type/power :elm.normalizer.type/multiary-expression)

;; 16.17. Precision
(derive :elm.normalizer.type/precision :elm.normalizer.type/unary-expression)

;; 16.18. Predecessor
(derive :elm.normalizer.type/predecessor :elm.normalizer.type/unary-expression)

;; 16.19. Round
(defmethod normalize :elm.normalizer.type/round
  [{operand :operand :keys [precision] :as expression}]
  (cond-> (assoc expression :operand (normalize operand))
    precision
    (assoc :precision (normalize precision))))

;; 16.20. Subtract
(derive :elm.normalizer.type/subtract :elm.normalizer.type/multiary-expression)

;; 16.21. Successor
(derive :elm.normalizer.type/successor :elm.normalizer.type/unary-expression)

;; 16.22. Truncate
(derive :elm.normalizer.type/truncate :elm.normalizer.type/unary-expression)

;; 16.23. TruncatedDivide
(derive :elm.normalizer.type/truncated-divide :elm.normalizer.type/multiary-expression)

;; 17. String Operators

;; 17.1. Combine
(defmethod normalize :elm.normalizer.type/combine
  [{:keys [source separator] :as expression}]
  (cond-> (assoc expression :source (normalize source))
    separator
    (assoc :separator (normalize separator))))

;; 17.2. Concatenate
(derive :elm.normalizer.type/concatenate :elm.normalizer.type/multiary-expression)

;; 17.3. EndsWith
(derive :elm.normalizer.type/ends-with :elm.normalizer.type/multiary-expression)

;; 17.6. Indexer
(derive :elm.normalizer.type/indexer :elm.normalizer.type/multiary-expression)

;; 17.7. LastPositionOf
(defmethod normalize :elm.normalizer.type/last-position-of
  [expression]
  (-> (update expression :pattern normalize)
      (update :string normalize)))

;; 17.8. Length
(derive :elm.normalizer.type/length :elm.normalizer.type/unary-expression)

;; 17.9. Lower
(derive :elm.normalizer.type/lower :elm.normalizer.type/unary-expression)

;; 17.10. Matches
(derive :elm.normalizer.type/matches :elm.normalizer.type/multiary-expression)

;; 17.12. PositionOf
(defmethod normalize :elm.normalizer.type/position-of
  [expression]
  (-> (update expression :pattern normalize)
      (update :string normalize)))

;; 17.13. ReplaceMatches
(derive :elm.normalizer.type/replace-matches :elm.normalizer.type/multiary-expression)

;; 17.14. Split
(defmethod normalize :elm.normalizer.type/split
  [expression]
  (-> (update expression :stringToSplit normalize)
      (update :separator normalize)))

;; 17.15. SplitOnMatches
(derive :elm.normalizer.type/split-on-matches :elm.normalizer.type/multiary-expression)

;; 17.16. StartsWith
(derive :elm.normalizer.type/starts-with :elm.normalizer.type/multiary-expression)

;; 17.17. Substring
(defmethod normalize :elm.normalizer.type/substring
  [{:keys [length] :as expression}]
  (cond-> (-> (update expression :stringToSub normalize)
              (update :startIndex normalize))
    length
    (assoc :length (normalize length))))

;; 17.18. Upper
(derive :elm.normalizer.type/upper :elm.normalizer.type/unary-expression)

;; 18. Date and Time Operators

;; 18.6. Date
(defmethod normalize :elm.normalizer.type/date
  [expression]
  (cond-> expression
    (:year expression) (update :year normalize)
    (:month expression) (update :month normalize)
    (:day expression) (update :day normalize)))

;; 18.7. DateFrom
(derive :elm.normalizer.type/date-from :elm.normalizer.type/unary-expression)

;; 18.8. DateTime
(defmethod normalize :elm.normalizer.type/date-time
  [expression]
  (cond-> expression
    (:year expression) (update :year normalize)
    (:month expression) (update :month normalize)
    (:day expression) (update :day normalize)
    (:hour expression) (update :hour normalize)
    (:minute expression) (update :minute normalize)
    (:second expression) (update :second normalize)
    (:millisecond expression) (update :millisecond normalize)
    (:timezoneOffset expression) (update :timezoneOffset normalize)))

;; 18.9. DateTimeComponentFrom
(derive :elm.normalizer.type/date-time-component-from :elm.normalizer.type/unary-expression)

;; 18.10. DifferenceBetween
(derive :elm.normalizer.type/difference-between :elm.normalizer.type/multiary-expression)

;; 18.11. DurationBetween
(derive :elm.normalizer.type/duration-between :elm.normalizer.type/multiary-expression)

;; 18.14. SameAs
(derive :elm.normalizer.type/same-as :elm.normalizer.type/multiary-expression)

;; 18.15. SameOrBefore
(derive :elm.normalizer.type/same-or-before :elm.normalizer.type/multiary-expression)

;; 18.16. SameOrAfter
(derive :elm.normalizer.type/same-or-after :elm.normalizer.type/multiary-expression)

;; 18.18. Time
(defmethod normalize :elm.normalizer.type/time
  [expression]
  (cond-> expression
    (:hour expression) (update :hour normalize)
    (:minute expression) (update :minute normalize)
    (:second expression) (update :second normalize)
    (:millisecond expression) (update :millisecond normalize)))

;; 18.19. TimeFrom
(derive :elm.normalizer.type/time-from :elm.normalizer.type/unary-expression)

;; 18.20. TimezoneOffsetFrom
(derive :elm.normalizer.type/timezone-offset-from :elm.normalizer.type/unary-expression)

;; 19. Interval Operators

;; 19.1. Interval
(defmethod normalize :elm.normalizer.type/interval
  [expression]
  (cond-> expression
    (:low expression) (update :low normalize)
    (:high expression) (update :high normalize)
    (:lowClosedExpression expression) (update :lowClosedExpression normalize)
    (:highClosedExpression expression) (update :highClosedExpression normalize)))

;; 19.2. After
(derive :elm.normalizer.type/after :elm.normalizer.type/multiary-expression)

;; 19.3. Before
(derive :elm.normalizer.type/before :elm.normalizer.type/multiary-expression)

;; 19.4. Collapse
(derive :elm.normalizer.type/collapse :elm.normalizer.type/multiary-expression)

;; 19.5. Contains
(derive :elm.normalizer.type/contains :elm.normalizer.type/multiary-expression)

;; 19.6. End
(derive :elm.normalizer.type/end :elm.normalizer.type/unary-expression)

;; 19.7. Ends
(derive :elm.normalizer.type/ends :elm.normalizer.type/multiary-expression)

;; 19.10. Except
(derive :elm.normalizer.type/except :elm.normalizer.type/multiary-expression)

;; 19.11. Expand
(derive :elm.normalizer.type/expand :elm.normalizer.type/multiary-expression)

;; 19.12. In
(defmethod normalize :elm.normalizer.type/in
  [{[operand-1 operand-2] :operand :keys [precision]}]
  (let [operand-1 (normalize operand-1)
        operand-2 (normalize operand-2)]
    (cond-> (bin-pred "Contains" operand-2 operand-1)
      precision
      (assoc :precision precision))))

;; 19.13. Includes
(derive :elm.normalizer.type/includes :elm.normalizer.type/multiary-expression)

;; 19.14. IncludedIn
(defmethod normalize :elm.normalizer.type/included-in
  [{[operand-1 operand-2] :operand :keys [precision]}]
  (let [operand-1 (normalize operand-1)
        operand-2 (normalize operand-2)]
    (cond-> (bin-pred "Includes" operand-2 operand-1)
      precision
      (assoc :precision precision))))

;; 19.15. Intersect
(derive :elm.normalizer.type/intersect :elm.normalizer.type/multiary-expression)

;; 19.16. Meets
(defmethod normalize :elm.normalizer.type/meets
  [{[operand-1 operand-2] :operand :keys [precision]}]
  (let [operand-1 (normalize operand-1)
        operand-2 (normalize operand-2)]
    (bin-pred
     "Or"
     (cond-> (bin-pred "MeetsBefore" operand-1 operand-2)
       precision
       (assoc :precision precision))
     (cond-> (bin-pred "MeetsAfter" operand-1 operand-2)
       precision
       (assoc :precision precision)))))

;; 19.17. MeetsBefore
(derive :elm.normalizer.type/meets-before :elm.normalizer.type/multiary-expression)

;; 19.18. MeetsAfter
(derive :elm.normalizer.type/meets-after :elm.normalizer.type/multiary-expression)

;; 19.20. Overlaps
(derive :elm.normalizer.type/overlaps :elm.normalizer.type/multiary-expression)

;; 19.21. OverlapsBefore
(defmethod normalize :elm.normalizer.type/overlaps-before
  [{[operand-1 operand-2] :operand :keys [precision]}]
  (let [operand-1 (normalize operand-1)
        operand-2 (normalize operand-2)]
    (cond->
     (bin-pred
      "ProperContains"
      operand-1
      (cond->
       {:type "Start"
        :operand operand-2}
        (:resultTypeName operand-2)
        (assoc :resultTypeName (:resultTypeName operand-2))))
      precision
      (assoc :precision precision))))

;; 19.22. OverlapsAfter
(defmethod normalize :elm.normalizer.type/overlaps-after
  [{[operand-1 operand-2] :operand :keys [precision]}]
  (let [operand-1 (normalize operand-1)
        operand-2 (normalize operand-2)]
    (cond->
     (bin-pred
      "ProperContains"
      operand-1
      (cond->
       {:type "End"
        :operand operand-2}
        (:resultTypeName operand-2)
        (assoc :resultTypeName (:resultTypeName operand-2))))
      precision
      (assoc :precision precision))))

;; 19.23. PointFrom
(derive :elm.normalizer.type/point-from :elm.normalizer.type/unary-expression)

;; 19.24. ProperContains
(derive :elm.normalizer.type/proper-contains :elm.normalizer.type/multiary-expression)

;; 19.25. ProperIn
(defmethod normalize :elm.normalizer.type/proper-in
  [{[operand-1 operand-2] :operand :keys [precision]}]
  (let [operand-1 (normalize operand-1)
        operand-2 (normalize operand-2)]
    (cond-> (bin-pred "ProperContains" operand-2 operand-1)
      precision
      (assoc :precision precision))))

;; 19.26. ProperIncludes
(derive :elm.normalizer.type/proper-includes :elm.normalizer.type/multiary-expression)

;; 19.27. ProperIncludedIn
(defmethod normalize :elm.normalizer.type/proper-included-in
  [{[operand-1 operand-2] :operand :keys [precision]}]
  (let [operand-1 (normalize operand-1)
        operand-2 (normalize operand-2)]
    (cond-> (bin-pred "ProperIncludes" operand-2 operand-1)
      precision
      (assoc :precision precision))))

;; 19.28. Size
(derive :elm.normalizer.type/size :elm.normalizer.type/unary-expression)

;; 19.29. Start
(derive :elm.normalizer.type/start :elm.normalizer.type/unary-expression)

;; 19.30. Starts
(derive :elm.normalizer.type/starts :elm.normalizer.type/multiary-expression)

;; 19.31. Union
(derive :elm.normalizer.type/union :elm.normalizer.type/multiary-expression)

;; 19.32. Width
(derive :elm.normalizer.type/width :elm.normalizer.type/unary-expression)

;; 20. List Operators

;; 20.1. List
(defmethod normalize :elm.normalizer.type/list
  [expression]
  (update expression :element (partial mapv normalize)))

;; 20.4. Distinct
(derive :elm.normalizer.type/distinct :elm.normalizer.type/unary-expression)

;; 20.8. Exists
(derive :elm.normalizer.type/exists :elm.normalizer.type/unary-expression)

;; 20.9. Filter
(defmethod normalize :elm.normalizer.type/filter
  [expression]
  (-> (update expression :source normalize)
      (update :condition normalize)))

;; 20.10. First
(defmethod normalize :elm.normalizer.type/first
  [expression]
  (cond-> (update expression :source normalize)
    (:orderBy expression) (update :orderBy (partial mapv normalize-sort-by-item))))

;; 20.11. Flatten
(derive :elm.normalizer.type/flatten :elm.normalizer.type/unary-expression)

;; 20.12. ForEach
(defmethod normalize :elm.normalizer.type/for-each
  [expression]
  (-> (update expression :source normalize)
      (update :element normalize)))

;; 20.16. IndexOf
(defmethod normalize :elm.normalizer.type/index-of
  [expression]
  (-> (update expression :source normalize)
      (update :element normalize)))

;; 20.18. Last
(defmethod normalize :elm.normalizer.type/last
  [expression]
  (cond-> (update expression :source normalize)
    (:orderBy expression) (update :orderBy (partial mapv normalize-sort-by-item))))

;; 20.24. Repeat
(defmethod normalize :elm.normalizer.type/repeat
  [expression]
  (-> (update expression :source normalize)
      (update :element normalize)))

;; 20.25. SingletonFrom
(derive :elm.normalizer.type/singleton-from :elm.normalizer.type/unary-expression)

;; 20.26. Slice
(defmethod normalize :elm.normalizer.type/slice
  [expression]
  (cond-> (update expression :source normalize)
    (:startIndex expression) (update :startIndex normalize)
    (:endIndex expression) (update :endIndex normalize)))

;; 20.27. Sort
(defmethod normalize :elm.normalizer.type/sort
  [expression]
  (-> (update expression :source normalize)
      (update :by (partial mapv normalize-sort-by-item))))

;; 20.28. Times
(derive :elm.normalizer.type/times :elm.normalizer.type/multiary-expression)

;; 21. Aggregate Operators

(defn- normalize-aggregate [expression]
  (update expression :source normalize))

;; 21.1. AllTrue
(defmethod normalize :elm.normalizer.type/all-true [expression] (normalize-aggregate expression))

;; 21.2. AnyTrue
(defmethod normalize :elm.normalizer.type/any-true [expression] (normalize-aggregate expression))

;; 21.3. Avg
(defmethod normalize :elm.normalizer.type/avg [expression] (normalize-aggregate expression))

;; 21.4. Count
(defmethod normalize :elm.normalizer.type/count [expression] (normalize-aggregate expression))

;; 21.5. GeometricMean
(defmethod normalize :elm.normalizer.type/geometric-mean [expression] (normalize-aggregate expression))

;; 21.6. Product
(defmethod normalize :elm.normalizer.type/product [expression] (normalize-aggregate expression))

;; 21.7. Max
(defmethod normalize :elm.normalizer.type/max [expression] (normalize-aggregate expression))

;; 21.8. Median
(defmethod normalize :elm.normalizer.type/median [expression] (normalize-aggregate expression))

;; 21.9. Min
(defmethod normalize :elm.normalizer.type/min [expression] (normalize-aggregate expression))

;; 21.10. Mode
(defmethod normalize :elm.normalizer.type/mode [expression] (normalize-aggregate expression))

;; 21.11. PopulationVariance
(defmethod normalize :elm.normalizer.type/population-variance [expression] (normalize-aggregate expression))

;; 21.12. PopulationStdDev
(defmethod normalize :elm.normalizer.type/population-std-dev [expression] (normalize-aggregate expression))

;; 21.13. Sum
(defmethod normalize :elm.normalizer.type/sum [expression] (normalize-aggregate expression))

;; 21.14. StdDev
(defmethod normalize :elm.normalizer.type/std-dev [expression] (normalize-aggregate expression))

;; 21.15. Variance
(defmethod normalize :elm.normalizer.type/variance [expression] (normalize-aggregate expression))

;; 22. Type Operators

;; 22.1. As
(derive :elm.normalizer.type/as :elm.normalizer.type/unary-expression)

;; 22.3. CanConvertQuantity
(derive :elm.normalizer.type/can-convert-quantity :elm.normalizer.type/multiary-expression)

;; 22.4. Children
(defmethod normalize :elm.normalizer.type/children
  [expression]
  (update expression :source normalize))

;; 22.6. ConvertQuantity
(derive :elm.normalizer.type/convert-quantity :elm.normalizer.type/multiary-expression)

;; 22.7. ConvertsToBoolean
(derive :elm.normalizer.type/converts-to-boolean :elm.normalizer.type/unary-expression)

;; 22.8. ConvertsToDate
(derive :elm.normalizer.type/converts-to-date :elm.normalizer.type/unary-expression)

;; 22.9. ConvertsToDateTime
(derive :elm.normalizer.type/converts-to-date-time :elm.normalizer.type/unary-expression)

;; 22.10. ConvertsToDecimal
(derive :elm.normalizer.type/converts-to-decimal :elm.normalizer.type/unary-expression)

;; 22.11. ConvertsToLong
(derive :elm.normalizer.type/converts-to-long :elm.normalizer.type/unary-expression)

;; 22.12. ConvertsToInteger
(derive :elm.normalizer.type/converts-to-integer :elm.normalizer.type/unary-expression)

;; 22.13. ConvertsToQuantity
(derive :elm.normalizer.type/converts-to-quantity :elm.normalizer.type/unary-expression)

;; 22.14. ConvertsToRatio
(derive :elm.normalizer.type/converts-to-ratio :elm.normalizer.type/unary-expression)

;; 22.15. ConvertsToString
(derive :elm.normalizer.type/converts-to-string :elm.normalizer.type/unary-expression)

;; 22.16. ConvertsToTime
(derive :elm.normalizer.type/converts-to-time :elm.normalizer.type/unary-expression)

;; 22.17. Descendents
(defmethod normalize :elm.normalizer.type/descendents
  [expression]
  (update expression :source normalize))

;; 22.18. Is
(derive :elm.normalizer.type/is :elm.normalizer.type/unary-expression)

;; 22.19. ToBoolean
(derive :elm.normalizer.type/to-boolean :elm.normalizer.type/unary-expression)

;; 22.20. ToChars
(derive :elm.normalizer.type/to-chars :elm.normalizer.type/unary-expression)

;; 22.21. ToConcept
(derive :elm.normalizer.type/to-concept :elm.normalizer.type/unary-expression)

;; 22.22. ToDate
(derive :elm.normalizer.type/to-date :elm.normalizer.type/unary-expression)

;; 22.23. ToDateTime
(derive :elm.normalizer.type/to-date-time :elm.normalizer.type/unary-expression)

;; 22.24. ToDecimal
(derive :elm.normalizer.type/to-decimal :elm.normalizer.type/unary-expression)

;; 22.25. ToInteger
(derive :elm.normalizer.type/to-integer :elm.normalizer.type/unary-expression)

;; 22.26. ToList
(derive :elm.normalizer.type/to-list :elm.normalizer.type/unary-expression)

;; 22.27. ToLong
(derive :elm.normalizer.type/to-long :elm.normalizer.type/unary-expression)

;; 22.28. ToQuantity
(derive :elm.normalizer.type/to-quantity :elm.normalizer.type/unary-expression)

;; 22.29. ToRatio
(derive :elm.normalizer.type/to-ratio :elm.normalizer.type/unary-expression)

;; 22.30. ToString
(derive :elm.normalizer.type/to-string :elm.normalizer.type/unary-expression)

;; 22.31. ToTime
(derive :elm.normalizer.type/to-time :elm.normalizer.type/unary-expression)

;; 23. Clinical Operators

;; 23.3. CalculateAge
(defmethod normalize :elm.normalizer.type/calculate-age
  [{birth-date :operand :keys [precision]}]
  (let [birth-date (normalize birth-date)]
    (cond->
     {:type "CalculateAgeAt"
      :operand [birth-date {:type "Today"}]
      :resultTypeName "{urn:hl7-org:elm-types:r1}Integer"}
      precision
      (assoc :precision precision))))

;; 23.4. CalculateAgeAt
(derive :elm.normalizer.type/calculate-age-at :elm.normalizer.type/multiary-expression)

;; 23.7. InCodeSystem
(defmethod normalize :elm.normalizer.type/in-code-system
  [{:keys [codesystemExpression] :as expression}]
  (cond-> (update expression :code normalize)
    codesystemExpression (assoc :codesystemExpression (normalize codesystemExpression))))

;; 23.8. InValueSet
(defmethod normalize :elm.normalizer.type/in-value-set
  [{:keys [valuesetExpression] :as expression}]
  (cond-> (update expression :code normalize)
    valuesetExpression (assoc :valuesetExpression (normalize valuesetExpression))))

;; 24. Errors and Messages

;; 24.1. Message
(defmethod normalize :elm.normalizer.type/message
  [{:keys [condition code severity message] :as expression}]
  (cond-> (update expression :source normalize)
    condition (update :condition normalize)
    code (update :code normalize)
    severity (update :severity normalize)
    message (update :message normalize)))
