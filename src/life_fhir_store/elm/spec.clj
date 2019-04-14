(ns life-fhir-store.elm.spec
  (:require
    [camel-snake-kebab.core :refer [->kebab-case-string]]
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
    [clojure.string :as str]
    [life-fhir-store.elm.quantity :refer [print-unit]])
  (:import
    [java.time LocalDate]
    [javax.measure.spi SystemOfUnits]
    [systems.uom.ucum UCUM]
    [java.math RoundingMode]))


(def temporal-keywords
  "CQL temporal keywords as units for temporal quantities."
  (->> ["year" "month" "week" "day" "hour" "minute" "second" "millisecond"]
       (into #{} (mapcat #(vector % (str % "s"))))))


(def ^SystemOfUnits ucum-service
  (UCUM/getInstance))


(def defined-units
  "All defined units from ucum-service."
  (into #{} (comp (map print-unit)
                  (remove str/blank?)
                  ;; TODO: UCUM can't parse powers of ten like 10^6
                  (remove #(.contains ^String % "^"))
                  (remove #(.contains ^String % "()"))
                  (remove #(.contains ^String % "(W)"))
                  (remove #(.contains ^String % "tec.uom.se")))
        (.getUnits ucum-service)))


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


(defn expression-dispatch [{:keys [type]}]
  (when type
    (keyword "elm.spec.type" (->kebab-case-string type))))


(defmulti expression expression-dispatch)

(s/def :elm/expression
  (s/multi-spec expression :type))



;; 1. Simple Values

;; 1.1. Literal
(defmethod expression :elm.spec.type/literal [_]
  (s/keys :req-un [:elm/valueType] :opt-un [:elm/value]))


(s/def :elm.literal/type
  #{"Literal"})


(s/def :elm.integer/valueType
  #{"{urn:hl7-org:elm-types:r1}Integer"})


(s/def :elm.integer/value
  (s/with-gen string? #(gen/fmap str (gen/int))))


(s/def :elm/integer
  (s/keys :req-un [:elm.literal/type :elm.integer/valueType :elm.integer/value]))


(s/def :elm.decimal/valueType
  #{"{urn:hl7-org:elm-types:r1}Decimal"})

(def decimal-value
  (->> (gen/large-integer)
       (gen/fmap #(BigDecimal/valueOf ^long % 8))))


(s/def :elm.decimal/value
  (s/with-gen string? #(gen/fmap str decimal-value)))


(s/def :elm/decimal
  (s/keys :req-un [:elm.literal/type :elm.decimal/valueType :elm.decimal/value]))


(defn non-zero-decimal-value []
  (gen/fmap str (gen/such-that (complement zero?) decimal-value)))


(s/def :elm/non-zero-decimal
  (s/with-gen :elm/decimal #(s/gen :elm/decimal {:elm.decimal/value non-zero-decimal-value})))


;; 2. Structured Values

;; 2.1. Tuple
(s/def :elm.tuple/name
  string?)


(s/def :elm.tuple/value
  :elm/expression)


(s/def :elm/tuple-element
  (s/keys :req-un [:elm.tuple/name :elm.tuple/value]))


(s/def :elm.tuple/element
  (s/coll-of :elm/tuple-element))


(defmethod expression :elm.spec.type/tuple [_]
  (s/keys :opt-un [:elm.tuple/element]))


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
(s/def :elm.quantity/type
  #{"Quantity"})


(s/def :elm.quantity/value
  (s/with-gen number? (constantly decimal-value)))


(s/def :elm.quantity/unit
  (s/with-gen string? #(s/gen (set/union temporal-keywords defined-units))))


(s/def :elm/quantity
  (s/keys :req-un [:elm.quantity/type :elm.quantity/value]
          :opt-un [:elm.quantity/unit]))


(defmethod expression :elm.spec.type/quantity [_]
  :elm/quantity)


(s/def :elm.quantity.temporal-keyword/unit
  temporal-keywords)


(s/def :elm/period
  (s/keys :req-un [:elm.quantity/type :elm.quantity/value
                   :elm.quantity.temporal-keyword/unit]))


(defn years-unit-gen []
  (s/gen #{"year" "years"}))


(s/def :elm/years
  (s/with-gen
    :elm/period
    #(s/gen :elm/period {:elm.quantity.temporal-keyword/unit years-unit-gen})))


(defn pos-decimal-gen []
  (gen/fmap #(BigDecimal/valueOf ^double %)
            (gen/double* {:infinite? false :NaN? false :min 1 :max 10000})))


(s/def :elm/pos-years
  (s/with-gen
    :elm/period
    #(s/gen :elm/period {:elm.quantity/value pos-decimal-gen
                         :elm.quantity.temporal-keyword/unit years-unit-gen})))


(defn months-unit-gen []
  (s/gen #{"month" "months"}))


(s/def :elm/pos-months
  (s/with-gen
    :elm/period
    #(s/gen :elm/period {:elm.quantity/value pos-decimal-gen
                         :elm.quantity.temporal-keyword/unit months-unit-gen})))


(defn days-unit-gen []
  (s/gen #{"day" "days"}))


(s/def :elm/pos-days
  (s/with-gen
    :elm/period
    #(s/gen :elm/period {:elm.quantity/value pos-decimal-gen
                         :elm.quantity.temporal-keyword/unit days-unit-gen})))


(defn hours-unit-gen []
  (s/gen #{"hour" "hours"}))


(s/def :elm/pos-hours
  (s/with-gen
    :elm/period
    #(s/gen :elm/period {:elm.quantity/value pos-decimal-gen
                         :elm.quantity.temporal-keyword/unit hours-unit-gen})))



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



;; 15. Conditional Operators

(s/def :elm/when
  :elm/expression)


(s/def :elm/then
  :elm/expression)


(s/def :elm/else
  :elm/expression)


;; 15.1. Case
(s/def :elm.case/comparand
  :elm/expression)


(s/def :elm.case/item
  (s/keys :req-un [:elm/when :elm/then]))


(s/def :elm/caseItem
  (s/coll-of :elm.case/item :min-count 1))


(defmethod expression :elm.spec.type/case [_]
  (s/keys :req-un [:elm/caseItem :elm/else] :op-un [:elm.case/comparand]))


;; 15.2. If
(s/def :elm.if/condition
  :elm/expression)


(defmethod expression :elm.spec.type/if [_]
  (s/keys :req-un [:elm.if/condition :elm/then :elm/else]))



;; 16. Arithmetic Operators

;; 16.1. Abs
(derive :elm.spec.type/abs :elm.spec.type/unary-expression)


;; 16.2. Add
(derive :elm.spec.type/add :elm.spec.type/binary-expression)


;; 16.3. Ceiling
(derive :elm.spec.type/ceiling :elm.spec.type/unary-expression)


;; 16.4. Divide
(derive :elm.spec.type/divide :elm.spec.type/binary-expression)


;; 16.5. Exp
(derive :elm.spec.type/exp :elm.spec.type/unary-expression)


;; 16.6. Floor
(derive :elm.spec.type/floor :elm.spec.type/unary-expression)


;; 16.7. Log
(derive :elm.spec.type/log :elm.spec.type/binary-expression)


;; 16.8. Ln
(derive :elm.spec.type/ln :elm.spec.type/unary-expression)


;; 16.9. MaxValue
(defmethod expression :elm.spec.type/max-value [_]
  (s/keys :req-un [:elm/valueType]))


;; 16.10. MinValue
(defmethod expression :elm.spec.type/min-value [_]
  (s/keys :req-un [:elm/valueType]))


;; 16.11. Modulo
(derive :elm.spec.type/modulo :elm.spec.type/binary-expression)


;; 16.12. Multiply
(derive :elm.spec.type/multiply :elm.spec.type/binary-expression)


;; 16.13. Negate
(derive :elm.spec.type/negate :elm.spec.type/unary-expression)


;; 16.14. Power
(derive :elm.spec.type/power :elm.spec.type/binary-expression)


;; 16.15. Predecessor
(derive :elm.spec.type/predecessor :elm.spec.type/unary-expression)


;; 16.16. Round
(s/def :elm.round/precision
  :elm/expression)


(defmethod expression :elm.spec.type/round [_]
  (s/keys :req-un [:elm.unary-expression/operand]
          :opt-un [:elm.round/precision]))


;; 16.17. Subtract
(derive :elm.spec.type/subtract :elm.spec.type/binary-expression)


;; 16.18. Successor
(derive :elm.spec.type/successor :elm.spec.type/unary-expression)


;; 16.19. Truncate
(derive :elm.spec.type/truncate :elm.spec.type/unary-expression)


;; 16.20. TruncatedDivide
(derive :elm.spec.type/truncated-divide :elm.spec.type/binary-expression)



;; 18. Date and Time Operators
(s/def :elm.date/precision
  #{"Year" "Month" "Week" "Day"
    "Hour" "Minute" "Second" "Millisecond"})


;; 18.6. Date
(defn year-value-gen []
  (gen/fmap str (s/gen (s/int-in 1 10000))))


(s/def :elm.date/year
  (s/with-gen :elm/expression #(s/gen :elm/integer {:elm.integer/value year-value-gen})))


(defn month-value-gen []
  (gen/fmap str (s/gen (s/int-in 1 13))))


(s/def :elm.date/month
  (s/with-gen :elm/expression #(s/gen :elm/integer {:elm.integer/value month-value-gen})))


(defn day-value-gen []
  (gen/fmap str (s/gen (s/int-in 1 32))))


(s/def :elm.date/day
  (s/with-gen :elm/expression #(s/gen :elm/integer {:elm.integer/value day-value-gen})))


(s/def :elm.date/type
  #{"Date"})


(s/def :elm/year
  (s/keys :req-un [:elm.date/type :elm.date/year]))


(s/def :elm/literal-year
  :elm/year)


(s/def :elm/year-month
  (s/keys :req-un [:elm.date/type :elm.date/year :elm.date/month]))


(s/def :elm/literal-year-month
  :elm/year-month)

(s/def :elm/date
  (s/keys
    :req-un
    [:elm.date/type
     (or :elm.date/year
         (and :elm.date/year :elm.date/month)
         (and :elm.date/year :elm.date/month :elm.date/day))]))


(s/def :elm/literal-date
  (s/with-gen
    :elm/date
    #(s/gen
       (s/and
         :elm/date
         (fn [{{year :value} :year {month :value} :month {day :value} :day}]
           (or
             (nil? day)
             (try
               (LocalDate/of
                 (Long/parseLong year)
                 (Long/parseLong month)
                 (Long/parseLong day))
               (catch Exception _))))))))


(defmethod expression :elm.spec.type/date [_]
  (s/or :year :elm/year :year-month :elm/year-month :date :elm/date))


;; 18.7. DateFrom
(derive :elm.spec.type/date-from :elm.spec.type/unary-expression)


;; 18.8. DateTime
(defn hour-value-gen []
  (gen/fmap str (s/gen (s/int-in 0 24))))


(s/def :elm/hour
  (s/with-gen :elm/expression #(s/gen :elm/integer {:elm.integer/value hour-value-gen})))


(defn minute-value-gen []
  (gen/fmap str (s/gen (s/int-in 0 60))))


(s/def :elm/minute
  (s/with-gen :elm/expression #(s/gen :elm/integer {:elm.integer/value minute-value-gen})))


(defn second-value-gen []
  (gen/fmap str (s/gen (s/int-in 0 60))))


(s/def :elm/second
  (s/with-gen :elm/expression #(s/gen :elm/integer {:elm.integer/value second-value-gen})))


(defn timezone-offset-gen []
  (->> (gen/double* {:infinite? false :NaN? false :min -18 :max 18})
       (gen/fmap #(BigDecimal/valueOf ^double %))
       (gen/fmap str)))


(s/def :elm/timezone-offset
  (s/with-gen :elm/expression #(s/gen :elm/decimal {:elm.decimal/value timezone-offset-gen})))


(s/def :elm.date-time/type
  #{"DateTime"})


(s/def :elm/date-time
  (s/keys
    :req-un
    [:elm.date-time/type
     (or :elm.date/year
         (and :elm.date/year :elm.date/month)
         (and :elm.date/year :elm.date/month :elm.date/day)
         (and :elm.date/year :elm.date/month :elm.date/day :elm/hour)
         (and :elm.date/year :elm.date/month :elm.date/day :elm/hour
              :elm/timezone-offset)
         (and :elm.date/year :elm.date/month :elm.date/day :elm/hour
              :elm/minute)
         (and :elm.date/year :elm.date/month :elm.date/day :elm/hour
              :elm/minute :elm/timezone-offset)
         (and :elm.date/year :elm.date/month :elm.date/day :elm/hour
              :elm/minute :elm/second)
         (and :elm.date/year :elm.date/month :elm.date/day :elm/hour
              :elm/minute :elm/second :elm/timezone-offset))]))


(s/def :elm/literal-date-time
  (s/with-gen
    :elm/date-time
    #(s/gen
       (s/and
         :elm/date-time
         (fn [{{year :value} :year {month :value} :month {day :value} :day}]
           (or
             (nil? day)
             (try
               (LocalDate/of
                 (Long/parseLong year)
                 (Long/parseLong month)
                 (Long/parseLong day))
               (catch Exception _))))))))


(defmethod expression :elm.spec.type/date-time [_]
  :elm/date-time)

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


;; 18.18. Time
(s/def :elm.time/type
  #{"Time"})


(s/def :elm/time
  (s/keys
    :req-un
    [:elm.time/type
     (or :elm/hour
         (and :elm/hour :elm/minute)
         (and :elm/hour :elm/minute :elm/second))]))


(s/def :elm/literal-time
  :elm/time)


(defmethod expression :elm.spec.type/time [_]
  :elm/time)


;; 18.22. Today
(derive :elm.spec.type/today :elm.spec.type/operator-expression)



;; 19. Interval Operators

;; 19.1. Interval
(s/def :elm.interval/low
  :elm/expression)


(s/def :elm.interval/lowClosedExpression
  :elm/expression)


(s/def :elm.interval/high
  :elm/expression)


(s/def :elm.interval/highClosedExpression
  :elm/expression)


(s/def :elm.interval/lowClosed
  boolean?)


(s/def :elm.interval/highClosed
  boolean?)


(defmethod expression :elm.spec.type/interval [_]
  (s/keys
    :opt-un
    [:elm.interval/low
     :elm.interval/lowClosedExpression
     :elm.interval/high
     :elm.interval/highClosedExpression
     :elm.interval/lowClosed
     :elm.interval/highClosed]))

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


;; 22.16. ToBoolean
(derive :elm.spec.type/to-boolean :elm.spec.type/unary-expression)


;; 22.17. ToChars
(derive :elm.spec.type/to-chars :elm.spec.type/unary-expression)


;; 22.18. ToConcept
(derive :elm.spec.type/to-concept :elm.spec.type/unary-expression)


;; 22.19. ToDate
(derive :elm.spec.type/to-date :elm.spec.type/unary-expression)


;; 22.20. ToDateTime
(derive :elm.spec.type/to-date-time :elm.spec.type/unary-expression)


;; 22.21. ToDecimal
(derive :elm.spec.type/to-decimal :elm.spec.type/unary-expression)


;; 22.21. ToInteger
(derive :elm.spec.type/to-integer :elm.spec.type/unary-expression)


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



;; 23. Clinical Operators

;; 23.4. CalculateAgeAt
(defmethod expression :elm.spec.type/calculate-age-at [_]
  (s/keys :req-un [:elm.binary-expression/operand]
          :opt-un [:elm.date/precision]))
