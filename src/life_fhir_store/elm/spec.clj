(ns life-fhir-store.elm.spec
  (:require
    [camel-snake-kebab.core :refer [->kebab-case-string]]
    [clojure.spec.alpha :as s]))


(s/def :elm/alias
  string?)


(s/def :elm/accessLevel
  string?)


(s/def :elm/context
  #{"Patient" "Population"})


(s/def :elm/dataType
  string?)


(s/def :elm/libraryName
  string?)


(s/def :elm/name
  string?)


(s/def :elm/path
  string?)


(s/def :elm/scope
  string?)


(s/def :elm/type
  string?)


(s/def :elm/value
  string?)


(s/def :elm/valueType
  string?)


(s/def :elm/signature
  (s/coll-of :elm/type-specifier))


(defn expression-dispatch [{:keys [type] :as expression}]
  (assert type (str "Missing type in expression `" (prn-str expression) "`."))
  (keyword "elm.spec.type" (->kebab-case-string type)))


(defmulti expression expression-dispatch)

(s/def :elm/expression
  (s/multi-spec expression :type))



;; 1. Simple Values

;; 1.1. Literal
(defmethod expression :elm.spec.type/literal [_]
  (s/keys :req-un [:elm/valueType] :opt-un [:elm/value]))



;; 2. Structured Values

;; 2.3. Property
(s/def :elm.property/source
  :elm/expression)


(defmethod expression :elm.spec.type/property [_]
  (s/keys :req-un [:elm/path] :opt-un [:elm.property/source :elm/scope]))



;; 3. Clinical Values

;; 3.3. CodeRef
(defmethod expression :elm.spec.type/code-ref [_]
  (s/keys :opt-un [:elm/name :elm/libraryName]))


;; 3.5. CodeSystemRef
(defmethod expression :elm.spec.type/code-system-ref [_]
  (s/keys :opt-un [:elm/name :elm/libraryName]))


;; 3.9. Quantity
(s/def :elm.quantity/value
  number?)


(s/def :elm.quantity/unit
  string?)


(defmethod expression :elm.spec.type/quantity [_]
  (s/keys :opt-un [:elm.quantity/value :elm.quantity/unit]))


;; 4. Type Specifiers

;; 4.1. TypeSpecifier
(defmulti type-specifier :type)


(s/def :elm/type-specifier
  (s/multi-spec type-specifier :type))


(s/def :elm/elementType
  :elm/type-specifier)


;; 4.2. NamedTypeSpecifier
(s/def :elm/named-type-specifier
  (s/keys :req-un [:elm/name]))


(defmethod type-specifier :elm.spec.type/named-type-specifier [_]
  :elm/named-type-specifier)


;; 4.4. ListTypeSpecifier
(s/def :elm/list-type-specifier
  (s/keys :req-un [:elm/elementType]))


(defmethod type-specifier :elm.spec.type/list-type-specifier [_]
  :elm/list-type-specifier)



;; 5. Libraries

;; 5.1. Library
(s/def :elm.library.statements/def
  (s/coll-of :elm/expression-def))


(s/def :elm.library/statements
  (s/keys :req-un [:elm.library.statements/def]))


(s/def :elm/library
  (s/keys :req-un [:elm/identifier :elm/schemaIdentifier :elm.library/statements]))



;; 8. Expressions

;; 8.2. OperatorExpression
(defmethod expression :elm.spec.type/operator-expression [_]
  (s/keys :opt-un [:elm/signature]))


;; 8.3. UnaryExpression
(s/def :elm.unary-expression/operand
  :elm/expression)


(defmethod expression :elm.spec.type/unary-expression [_]
  (s/keys :req-un [:elm.unary-expression/operand]))


;; 8.4. BinaryExpression
(s/def :elm.binary-expression/operand
  (s/tuple :elm/expression :elm/expression))


(defmethod expression :elm.spec.type/binary-expression [_]
  (s/keys :req-un [:elm.binary-expression/operand]))


;; 8.5. TernaryExpression
(s/def :elm.ternary-expression/operand
  (s/tuple :elm/expression :elm/expression :elm/expression))


(defmethod expression :elm.spec.type/ternary-expression [_]
  (s/keys :req-un [:elm.ternary-expression/operand]))


;; 8.6. NaryExpression
(s/def :elm.nary-expression/operand
  (s/coll-of :elm/expression))


(defmethod expression :elm.spec.type/nary-expression [_]
  (s/keys :req-un [:elm.nary-expression/operand]))


;; 8.7. AggregateExpression
(s/def :elm.aggregate-expression/source
  :elm/expression)


(defmethod expression :elm.spec.type/aggregate-expression [_]
  (s/keys :req-un [:elm.aggregate-expression/source]
          :opt-un [:elm/signature :elm/path]))



;; 9. Reusing Logic

;; 9.1. ExpressionDef
(s/def :elm/expression-def
  (s/keys :opt-un [:elm/expression :elm/name :elm/context :elm/accessLevel]))


;; 9.2. ExpressionRef
(defmethod expression :elm.spec.type/expression-ref [_]
  (s/keys :opt-un [:elm/name :elm/libraryName]))


;; 9.4. FunctionRef
(defmethod expression :elm.spec.type/function-ref [_]
  (s/keys :opt-un [:elm/name :elm/libraryName :elm.nary-expression/operand]))



;; 10. Queries
(s/def :elm.query/source
  (s/coll-of :elm/aliased-query-source :min-count 1))


(s/def :elm.query/relationship
  (s/coll-of
    (s/or :with :elm.query/with
          :with-equiv :elm.query.life/with-equiv
          :without :elm.query/without)))


(s/def :elm.query/suchThat
  :elm/expression)


(s/def :elm.query.life/equivOperand
  (s/tuple :elm/expression :elm/expression))


(s/def :elm.query/where
  :elm/expression)


;; 10.1. Query
(defmethod expression :elm.spec.type/query [_]
  (s/keys :req-un [:elm.query/source]
          :opt-un [:elm.query/relationship :elm.query/where]))


;; 10.2. AliasedQuerySource
(s/def :elm/aliased-query-source
  (s/keys :req-un [:elm/expression :elm/alias]))


;; 10.3. AliasRef
(defmethod expression :elm.spec.type/alias-ref [_]
  (s/keys :opt-un [:elm/name]))


;; 10.12. With
(s/def :elm.query/with
  (s/keys :req-un [:elm/expression :elm/alias :elm.query/suchThat]))


(s/def :elm.query.life/with-equiv
  (s/keys :req-un [:elm/expression :elm/alias :elm.query.life/equivOperand]
          :opt-un [:elm.query/suchThat]))


;; 10.13. Without
(s/def :elm.query/without
  (s/keys :req-un [:elm/expression :elm/alias :elm.query/suchThat]))



;; 11. External Data

;; 11.1. Retrieve
(defmethod expression :elm.spec.type/retrieve [_]
  (s/keys :req-un [:elm/dataType]
          :opt-un [:elm/scope]))



;; 12. Comparison Operators

;; 12.1. Equal
(derive :elm.spec.type/equal :elm.spec.type/binary-expression)


;; 12.2. Equivalent
(derive :elm.spec.type/equivalent :elm.spec.type/binary-expression)


;; 12.3. Greater
(derive :elm.spec.type/greater :elm.spec.type/binary-expression)


;; 12.4. GreaterOrEqual
(derive :elm.spec.type/greater-or-equal :elm.spec.type/binary-expression)


;; 12.5. Less
(derive :elm.spec.type/less :elm.spec.type/binary-expression)


;; 12.6. LessOrEqual
(derive :elm.spec.type/less-or-equal :elm.spec.type/binary-expression)


;; 12.7. NotEqual
(derive :elm.spec.type/not-equal :elm.spec.type/binary-expression)



;; 13. Logical Operators

;; 13.1. And
(derive :elm.spec.type/and :elm.spec.type/binary-expression)


;; 13.2. Implies
(derive :elm.spec.type/implies :elm.spec.type/binary-expression)


;; 13.3. Not
(derive :elm.spec.type/not :elm.spec.type/unary-expression)


;; 13.4. Or
(derive :elm.spec.type/or :elm.spec.type/binary-expression)


;; 13.4. Xor
(derive :elm.spec.type/xor :elm.spec.type/binary-expression)



;; 14. Nullological Operators

;; 14.1. Null
(defmethod expression :elm.spec.type/null [_]
  (s/keys :opt-un [:elm/valueType]))


;; 14.2. Coalesce
(derive :elm.spec.type/coalesce :elm.spec.type/nary-expression)


;; 14.3. IsFalse
(derive :elm.spec.type/is-false :elm.spec.type/unary-expression)


;; 14.4. IsNull
(derive :elm.spec.type/is-null :elm.spec.type/unary-expression)


;; 14.5. IsTrue
(derive :elm.spec.type/is-true :elm.spec.type/unary-expression)



;; 16. Arithmetic Operators

;; 16.1. Abs
(derive :elm.spec.type/abs :elm.spec.type/unary-expression)



;; 18. Date and Time Operators
(s/def :elm.date/precision
  #{"Year" "Month" "Week" "Day"
    "Hour" "Minute" "Second" "Millisecond"})


;; 18.6. Date
(s/def :elm/year
  :elm/expression)


(s/def :elm/month
  :elm/expression)


(s/def :elm/day
  :elm/expression)


(defmethod expression :elm.spec.type/date [_]
  (s/keys :req-un [:elm/year] :opt-un [:elm/month :elm/day]))


;; 18.7. DateFrom
(derive :elm.spec.type/date-from :elm.spec.type/unary-expression)


;; 18.8. DateTime
(s/def :elm/hour
  :elm/expression)


(s/def :elm/minute
  :elm/expression)


(s/def :elm/second
  :elm/expression)


(s/def :elm/millisecond
  :elm/expression)


(s/def :elm/timezone-offset
  :elm/expression)


(defmethod expression :elm.spec.type/date-time [_]
  (s/keys :req-un [:elm/year]
          :opt-un [:elm/month :elm/day :elm/hour :elm/minute :elm/second
                   :elm/millisecond :elm/timezone-offset]))


;; 18.9. DateTimeComponentFrom


;; 18.10. DifferenceBetween
(defmethod expression :elm.spec.type/difference-between [_]
  (s/keys :req-un [:elm.binary-expression/operand]
          :opt-un [:elm.date/precision]))


;; 18.11. DurationBetween
(defmethod expression :elm.spec.type/duration-between [_]
  (s/keys :req-un [:elm.binary-expression/operand]
          :opt-un [:elm.date/precision]))


;; 18.12. Not Equal


;; 18.13. Now
(derive :elm.spec.type/now :elm.spec.type/operator-expression)


;; 18.14. SameAs
(defmethod expression :elm.spec.type/same-as [_]
  (s/keys :req-un [:elm.binary-expression/operand]
          :opt-un [:elm.date/precision]))


;; 18.15. SameOrBefore
(defmethod expression :elm.spec.type/same-or-before [_]
  (s/keys :req-un [:elm.binary-expression/operand]
          :opt-un [:elm.date/precision]))


;; 18.22. Today
(derive :elm.spec.type/today :elm.spec.type/operator-expression)



;; 19. Interval Operators

;; 19.15. Intersect
(derive :elm.spec.type/intersect :elm.spec.type/nary-expression)


;; 19.30. Union
(derive :elm.spec.type/union :elm.spec.type/nary-expression)



;; 20. List Operators

;; 20.1. List
(s/def :elm.list/typeSpecifier
  :elm/type-specifier)


(s/def :elm.list/element
  (s/coll-of :elm/expression))


(defmethod expression :elm.spec.type/list [_]
  (s/keys :opt-un [:elm.list/typeSpecifier :elm.list/element]))


;; 20.25. SingletonFrom
(derive :elm.spec.type/singleton-from :elm.spec.type/unary-expression)



;; 21. Aggregate Operators

;; 21.1. AllTrue
(derive :elm.spec.type/all-true :elm.spec.type/aggregate-expression)


;; 21.2. AnyTrue
(derive :elm.spec.type/any-true :elm.spec.type/aggregate-expression)


;; 21.3. Avg
(derive :elm.spec.type/avg :elm.spec.type/aggregate-expression)


;; 21.4. Count
(derive :elm.spec.type/count :elm.spec.type/aggregate-expression)


;; 21.5. GeometricMean
(derive :elm.spec.type/geometric-mean :elm.spec.type/aggregate-expression)


;; 21.6. Product
(derive :elm.spec.type/product :elm.spec.type/aggregate-expression)


;; 21.7. Max
(derive :elm.spec.type/max :elm.spec.type/aggregate-expression)


;; 21.8. Median
(derive :elm.spec.type/median :elm.spec.type/aggregate-expression)


;; 21.9. Min
(derive :elm.spec.type/min :elm.spec.type/aggregate-expression)


;; 21.10. Mode
(derive :elm.spec.type/mode :elm.spec.type/aggregate-expression)


;; 21.11. PopulationVariance
(derive :elm.spec.type/population-variance :elm.spec.type/aggregate-expression)


;; 21.12. PopulationStdDev
(derive :elm.spec.type/population-std-dev :elm.spec.type/aggregate-expression)


;; 21.13. Sum
(derive :elm.spec.type/sum :elm.spec.type/aggregate-expression)


;; 21.14. StdDev
(derive :elm.spec.type/std-dev :elm.spec.type/aggregate-expression)


;; 21.15. Variance
(derive :elm.spec.type/variance :elm.spec.type/aggregate-expression)



;; 22. Type Operators

;; 22.1. TODO As
(derive :elm.spec.type/as :elm.spec.type/unary-expression)


;; 22.2. TODO CanConvert
(derive :elm.spec.type/can-convert :elm.spec.type/unary-expression)


;; 22.4. TODO Convert
(derive :elm.spec.type/convert :elm.spec.type/unary-expression)


;; 22.20. ToDateTime
(derive :elm.spec.type/to-date-time :elm.spec.type/unary-expression)


;; 22.23. ToList
(derive :elm.spec.type/to-list :elm.spec.type/unary-expression)


;; 22.24. ToQuantity
(derive :elm.spec.type/to-quantity :elm.spec.type/unary-expression)


;; 22.25. ToRatio
(derive :elm.spec.type/to-ratio :elm.spec.type/unary-expression)


;; 22.26. ToString
(derive :elm.spec.type/to-string :elm.spec.type/unary-expression)


;; 22.27. ToTime
(derive :elm.spec.type/to-time :elm.spec.type/unary-expression)
