(ns blaze.cql-test
  "https://cql.hl7.org/2019May/tests.html"
  (:require
    [clojure.data.xml :as xml]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [cognitect.anomalies :as anom]
    [blaze.cql-translator :refer [translate]]
    [blaze.elm.compiler :refer [compile]]
    [blaze.elm.compiler.protocols :refer [-eval]]
    [blaze.elm.type-infer :as type-infer]
    [blaze.elm.deps-infer :as deps-infer]
    [blaze.elm.equiv-relationships :as equiv-relationships]
    [blaze.elm.normalizer :as normalizer])
  (:import
    [java.time OffsetDateTime])
  (:refer-clojure :exclude [compile eval]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (st/instrument
    `compile
    {:spec
     {`compile
      (s/fspec
        :args (s/cat :context any? :expression :elm/expression))}})
  (f)
  (st/unstrument))


(use-fixtures :each fixture)


(defn tests [xml exclusions]
  (for [group (:content xml)]
    {:name (-> group :attrs :name)
     :tests
     (for [test (:content group)
           :let [name (-> test :attrs :name)
                 expression (-> test :content first)
                 invalid? (-> expression :attrs :invalid)
                 output (-> test :content second :content first)]
           :when (not (contains? exclusions name))
           :let [expression-content (-> expression :content first)]]
       {:name name
        :expression expression-content
        :invalid? invalid?
        :output output})}))

(defn to-source-elm [cql]
  (translate (str "define x: " cql)))

(defn to-elm [cql]
  (let [elm (to-source-elm cql)]
    (if (::anom/category elm)
      (throw (ex-info "CQL-to-ELM translation error" elm))
      (-> elm
          normalizer/normalize-library
          equiv-relationships/find-equiv-rels-library
          deps-infer/infer-library-deps
          type-infer/infer-library-types
          :statements :def first :expression))))

(defn eval-elm [now elm]
  (-eval (compile {} elm) {:now now} nil nil))

(defn eval [now cql]
  (eval-elm now (to-elm cql)))

(defn gen-tests [name file exclusions]
  `(deftest ~(symbol name)
     ~@(for [{:keys [name tests]} (tests (xml/parse-str (slurp file)) exclusions)]
         `(testing ~name
            ~@(for [{:keys [name expression invalid? output]} tests]
                `(testing ~name
                   (let [~'now (OffsetDateTime/now)]
                     ~(if invalid?
                        `(is (~'thrown? Exception (eval ~'now ~expression)))
                        `(is (= (eval ~'now ~output) (eval ~'now ~expression)))))))))))


(defmacro deftests [name file exclusions]
  (gen-tests name file exclusions))


;; 1. Types
(deftests "types" "cql-test/CqlTypesTest.xml"
          #{"QuantityTest"                                  ; unit `lbs` unknown
            "QuantityTest2"                                 ; unit `eskimo kisses` unknown
            "DateTimeUncertain"                             ; TODO: implement
            })


;; 2. Logical Operators
(deftests "logical-operators" "cql-test/CqlLogicalOperatorsTest.xml"
          #{"TrueImpliesTrue"                               ; TODO: CQL-To-ELM error
            "TrueImpliesFalse"                              ; TODO: CQL-To-ELM error
            "TrueImpliesNull"                               ; TODO: CQL-To-ELM error
            "FalseImpliesTrue"                              ; TODO: CQL-To-ELM error
            "FalseImpliesFalse"                             ; TODO: CQL-To-ELM error
            "FalseImpliesNull"                              ; TODO: CQL-To-ELM error
            "NullImpliesTrue"                               ; TODO: CQL-To-ELM error
            "NullImpliesFalse"                              ; TODO: CQL-To-ELM error
            "NullImpliesNull"                               ; TODO: CQL-To-ELM error
            })


;; 3. Type Operators
(deftests "type-operators" "cql-test/CqlTypeOperatorsTest.xml"
          #{"IntegerToString"                               ; TODO: implement
            "StringToIntegerError"                          ; TODO: implement
            "StringToDateTime"                              ; TODO: implement
            "StringToTime"                                  ; TODO: implement
            "IntegerIsInteger"                              ; TODO: implement
            "StringIsInteger"                               ; TODO: implement
            "StringNoToBoolean"                             ; TODO: implement
            "CodeToConcept1"                                ; TODO: implement
            "ToDateTime1"                                   ; TODO: implement
            "ToDateTime2"                                   ; TODO: implement
            "ToDateTime3"                                   ; TODO: implement
            "ToDateTime4"                                   ; TODO: implement
            "ToDateTime5"                                   ; TODO: implement
            "ToDateTime6"                                   ; TODO: implement
            "String5D5CMToQuantity"                         ; TODO: implement
            "IntegerNeg5ToString"                           ; TODO: implement
            "Decimal18D55ToString"                          ; TODO: implement
            "Quantity5D5CMToString"                         ; TODO: implement
            "BooleanTrueToString"                           ; TODO: implement
            "ToTime1"                                       ; TODO: implement
            "ToTime2"                                       ; TODO: implement
            "ToTime3"                                       ; TODO: implement
            "ToTime4"                                       ; TODO: implement
            })


;; 4. Nullological Operators
(deftests "nullological-operators" "cql-test/CqlNullologicalOperatorsTest.xml" #{})


;; 5. Comparison Operators
(deftests "comparison-operators" "cql-test/CqlComparisonOperatorsTest.xml"
          #{"SimpleNotEqNullNull"                           ; TODO: CQL-To-ELM error
            "EquivNullNull"                                 ; TODO: CQL-To-ELM error
            "SimpleEqNullNull"                              ; TODO: CQL-To-ELM error
            })


;; 6. Arithmetic Operators
(deftests "arithmetic-operators" "cql-test/CqlArithmeticFunctionsTest.xml"
          #{"DecimalMinValue" "DecimalMaxValue"
            "Ln0" "LnNeg0"
            "Log1Base1"
            "RoundNeg0D5" "RoundNeg1D5"
            "IntegerMinValue"                               ; CQL-To-ELM negates the pos integer which is over Integer/MAX_Value than
            })


;; 7. String Operators
(deftests "string-operators" "cql-test/CqlStringOperatorsTest.xml"
          #{"CombineEmptyList"                              ; If either argument is null, or the source list is empty, the result is null.
            "QuantityToString"                              ; the spec says 125 'cm'
            "DateTimeToString3"                             ; TODO: should contain a timezone offset
            })


;; 8. Date and Time Operators
(deftests "date-time-operators" "cql-test/CqlDateTimeOperatorsTest.xml"
          #{"DateTimeComponentFromTimezone"                 ; was renamed to TimezoneOffsetFrom
            "DateTimeAddInvalidYears"                       ; should be nil for 1.4
            "DateTimeAdd2YearsByDays"                       ; should that be possible?
            "DateTimeAdd2YearsByDaysRem5Days"               ; should that be possible?
            "DateTimeDifferenceHour"                        ; TODO: I don't get difference
            "DateTimeDifferenceMinute"                      ; TODO: I don't get difference
            "DateTimeDifferenceSecond"                      ; TODO: I don't get difference
            "DateTimeDifferenceMillisecond"                 ; TODO: I don't get difference
            "DateTimeDifferenceWeeks3"                      ; TODO: I don't get difference
            "DateTimeDifferenceUncertain"                   ; TODO: I don't get difference
            "DateTimeA"                                     ; don't get this time output without timezone
            "DateTimeAA"                                    ; don't get this time output without timezone
            "DateTimeB"                                     ; don't get this time output without timezone
            "DateTimeBB"                                    ; don't get this time output without timezone
            "DateTimeC"                                     ; don't get this time output without timezone
            "DateTimeCC"                                    ; don't get this time output without timezone
            "DateTimeD"                                     ; don't get this time output without timezone
            "DateTimeDD"                                    ; don't get this time output without timezone
            "DateTimeE"                                     ; don't get this time output without timezone
            "DateTimeEE"                                    ; don't get this time output without timezone
            "DateTimeF"                                     ; don't get this time output without timezone
            "DateTimeFF"                                    ; don't get this time output without timezone
            "DifferenceInHoursA"                            ; don't get this time output without timezone
            "DifferenceInMinutesA"                          ; don't get this time output without timezone
            "DifferenceInDaysA"                             ; don't get this time output without timezone
            "DifferenceInHoursAA"                           ; don't get this time output without timezone
            "DifferenceInMinutesAA"                         ; don't get this time output without timezone
            "DifferenceInDaysAA"                            ; don't get this time output without timezone
            "DateTimeSubtract2YearsAsMonthsRem1"            ; I don't get it
            "DateTimeDurationBetweenUncertainInterval"      ; TODO: implement uncertainty
            "DateTimeDurationBetweenUncertainInterval2"     ; TODO: implement uncertainty
            "DateTimeDurationBetweenUncertainAdd"           ; TODO: implement uncertainty
            "DateTimeDurationBetweenUncertainSubtract"      ; TODO: implement uncertainty
            "DateTimeDurationBetweenUncertainMultiply"      ; TODO: implement uncertainty
            "DateTimeDurationBetweenUncertainDiv"           ; TODO: implement uncertainty
            "DateTimeDurationBetweenMonthUncertain"         ; TODO: implement uncertainty
            "DateTimeDurationBetweenMonthUncertain3"        ; TODO: implement uncertainty
            "DateTimeDurationBetweenMonthUncertain4"        ; TODO: implement uncertainty
            "DateTimeDurationBetweenMonthUncertain5"        ; TODO: implement uncertainty
            "DateTimeDurationBetweenMonthUncertain6"        ; TODO: implement uncertainty
            "DateTimeDurationBetweenMonthUncertain7"        ; TODO: implement uncertainty

            "TimeDurationBetweenHourDiffPrecision"          ; new in v1.4.6
            })


;; 9. Interval Operators
(deftests "interval-operators" "cql-test/CqlIntervalOperatorsTest.xml"
          #{"TestAfterNull"                                 ; TODO: CQL-To-ELM error
            "TestBeforeNull"                                ; TODO: CQL-To-ELM error
            "TestPointFromNull"                             ; this is an infinity interval of type any?
            "TestEndsNull"                                  ; CQL-To-ELM generates strange interval with property expression
            "TestInNull"                                    ; CQL-To-ELM generates strange interval with property expression
            "TestProperlyIncludedInNull"                    ; CQL-To-ELM generates strange interval with property expression
            "TestEqualNull"                                 ; CQL-To-ELM generates strange interval with property expression
            "TestOnOrAfterNull"                             ; CQL-To-ELM generates invalid PointFrom expression
            "TestOnOrBeforeNull"                            ; CQL-To-ELM generates invalid PointFrom expression
            "TestOverlapsNull"                              ; type Any
            "TestOverlapsBeforeNull"                        ; type Any
            "TestOverlapsAfterNull"                         ; type Any
            "NullInterval"                                  ; type Any
            "TestExceptNull"                                ; type Any
            "TestUnionNull"                                 ; type Any
            "TestStartsNull"                                ; type Any
            "TestProperlyIncludesNull"                      ; type Any
            "DateTimeWidth"                                 ; Width isn't defined for Date. DateTime or Time types
            "TimeWidth"                                     ; Width isn't defined for Date. DateTime or Time types
            "TestCollapseNull"                              ; I don't see why this should result in null
            "TestNullElement1"                              ; Contains should return null on either null argument
            "DateTimeIncludedInNull"                        ; as no precision is given, it should return true
            "DateTimeIncludedInPrecisionNull"               ; TODO: resolve, worked before
            })


;; 10. List Operators
(deftests "list-operators" "cql-test/CqlListOperatorsTest.xml"
          #{"quantityList"                                  ; no unit `lbs`
            "ExceptEmptyListAndEmptyList"                   ; don't have a Except function
            "simpleSortAsc"                                 ; queries return distinct elements
            "simpleSortDesc"                                ; queries return distinct elements
            "SortDatesAsc"                                  ; the order of duplicates like @2012-10-05 and @2012-10-05T10:00 is unspecified
            "SortDatesDesc"                                 ; the order of duplicates like @2012-10-05 and @2012-10-05T10:00 is unspecified
            "DistinctNullNullNull"                          ; should preserve multiple null's
            "DistinctANullANull"                            ; should preserve multiple null's

            "ExceptNullRight"                               ; I don't see null as the empty list
            "In1Null"                                       ; I don't see null as the empty list
            "ContainsNullLeft"                              ; I don't see null as the empty list
            "IncludesNullLeft"                              ; I don't see null as the empty list
            "IncludedInNullRight"                           ; I don't see null as the empty list

            "IncludesListNullAndListNull"                   ; null isn't equal to null
            "IncludedInListNullAndListNull"                 ; null isn't equal to null

            "IndexOfNullIn1Null"                            ; second argument is null
            "IndexOfEmptyNull"                              ; second argument is null
            "ProperContainsNullRightFalse"                  ; second argument is null
            "ProperContainsNullRightTrue"                   ; second argument is null
            "ProperlyIncludedInNulRight"                    ; second argument is null
            "ProperInNullRightFalse"                        ; first argument is null
            "ProperInNullRightTrue"                         ; first argument is null
            "ProperlyIncludesNullLeft"                      ; first argument is null

            "EquivalentDateTimeNull"                        ; I don't get this test
            "EquivalentTimeNull"                            ; I don't get this test

            "ProperContainsTimeNull"                        ; why should null be returned here?
            "ProperInTimeNull"                              ; why should null be returned here?

            "Union123And2"                                  ; union has set semantics

            "EquivalentABCAnd123"                           ; new in v1.4.6
            "NotEqual123AndString123"                       ; new in v1.4.6
            "NotEqual123AndABC"                             ; new in v1.4.6
            "NotEqualABCAnd123"                             ; new in v1.4.6
            "Equivalent123AndString123"                     ; new in v1.4.6
            "Equivalent123AndABC"                           ; new in v1.4.6
            })


;; 11. Aggregate Functions
(deftests "aggregate-functions" "cql-test/CqlAggregateFunctionsTest.xml" #{})


(deftests "conditional-operators" "cql-test/CqlConditionalOperatorsTest.xml" #{})


(deftests "value-literals-and-selectors" "cql-test/ValueLiteralsAndSelectors.xml"
          #{"IntegerNeg2Pow31IntegerMinValue"               ; CQL-To-ELM negates the pos integer which is over Integer/MAX_Value than
            "DecimalTenthStep"                              ; we round here
            "DecimalPosTenthStep"                           ; we round here
            "DecimalNegTenthStep"                           ; we round here
            "Decimal10Pow28ToZeroOneStepDecimalMaxValue"    ; don't get it
            "DecimalPos10Pow28ToZeroOneStepDecimalMaxValue" ; don't get it
            "DecimalNeg10Pow28ToZeroOneStepDecimalMinValue" ; don't get it
            "IntegerMinValue"                               ; CQL-To-ELM negates the pos integer which is over Integer/MAX_Value than
            })
