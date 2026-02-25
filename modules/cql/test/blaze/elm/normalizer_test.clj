(ns blaze.elm.normalizer-test
  "Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.elm.literal :as elm]
   [blaze.elm.literal-spec]
   [blaze.elm.normalizer :refer [normalize]]
   [blaze.elm.normalizer-spec]
   [blaze.elm.util-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def expression-1
  {:type "Implies" :operand [#elm/string "A" #elm/string "B"]})

(def expression-2
  {:type "Xor" :operand [#elm/string "A" #elm/string "B"]})

(def expression-3
  {:type "In" :operand [#elm/string "A" #elm/string "B"]})

(def expression-4
  {:type "IncludedIn" :operand [#elm/string "A" #elm/string "B"]})

(def expression-5
  {:type "Meets" :operand [#elm/string "A" #elm/string "B"]})

;; 2. Structured Values

;; 2.1. Tuple
(deftest normalize-tuple-test
  (testing "Normalizes all elements of a Tuple expression"
    (given (normalize {:type "Tuple"
                       :element [{:name "a" :value expression-1}
                                 {:name "b" :value expression-2}]})
      [:element 0 :name] := "a"
      [:element 0 :value] := (normalize expression-1)
      [:element 1 :name] := "b"
      [:element 1 :value] := (normalize expression-2))))

;; 2.2. Instance
(deftest normalize-instance-test
  (testing "Normalizes all elements of an Instance expression"
    (given (normalize {:type "Instance"
                       :classType "{http://hl7.org/fhir}CodeableConcept"
                       :element [{:name "coding" :value expression-1}]})
      :type := "Instance"
      :classType := "{http://hl7.org/fhir}CodeableConcept"
      [:element 0 :name] := "coding"
      [:element 0 :value] := (normalize expression-1))))

;; 2.3. Property
(deftest normalize-property-test
  (testing "Normalizes the source of a Property expression"
    (given (normalize {:type "Property" :source expression-1})
      :source := (normalize expression-1)))

  (testing "Normalizes a Property expression without source"
    (given (normalize {:type "Property"})
      :type := "Property")))

;; 3. Clinical Values

;; 9. Reusing Logic

;; 9.4. FunctionRef
(deftest normalize-function-ref-test
  (testing "Normalizes all operands of a FunctionRef expression"
    (given (normalize {:type "FunctionRef" :name "a" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 10. Queries

;; 10.1. Query
(deftest normalize-query-test
  (testing "Normalizes the expression of a AliasedQuerySource in a Query expression"
    (given (normalize {:type "Query" :source [{:type "AliasedQuerySource" :expression expression-1}]})
      :source := [{:type "AliasedQuerySource" :expression (normalize expression-1)}]))

  (testing "Normalizes the expression of a LetClause in a Query expression"
    (given (normalize {:type "Query" :let [{:type "LetClause" :expression expression-1}]})
      :let := [{:type "LetClause" :expression (normalize expression-1)}]))

  (testing "Normalizes the expression of a RelationshipClause in a Query expression"
    (given (normalize {:type "Query" :relationship [{:type "With" :expression expression-1 :suchThat expression-2}]})
      :relationship := [{:type "With" :expression (normalize expression-1) :suchThat (normalize expression-2)}]))

  (testing "Normalizes the where expression of a Query expression"
    (given (normalize {:type "Query" :where expression-1})
      :where := (normalize expression-1)))

  (testing "Normalizes a Query expression without optional clauses"
    (given (normalize {:type "Query" :source [{:type "AliasedQuerySource" :expression expression-1}]})
      :type := "Query"
      :source := [{:type "AliasedQuerySource" :expression (normalize expression-1)}]
      :let := nil
      :relationship := nil
      :where := nil
      :return := nil))

  (testing "Normalizes the expression of the ReturnClause in a Query expression"
    (given (normalize {:type "Query" :return {:type "ReturnClause" :expression expression-1}})
      :return := {:type "ReturnClause" :expression (normalize expression-1)})))

;; 11. External Data

;; 11.1. Retrieve
(deftest normalize-retrieve-test
  (testing "Normalizes all operands of a Retrieve expression"
    (given (normalize {:type "Retrieve"
                       :codes expression-1
                       :dateRange expression-2
                       :context expression-3})
      :codes := (normalize expression-1)
      :dateRange := (normalize expression-2)
      :context := (normalize expression-3)))

  (testing "Normalizes a Retrieve expression without optional operands"
    (given (normalize {:type "Retrieve"})
      :type := "Retrieve"
      :codes := nil
      :dateRange := nil
      :context := nil)))

;; 12. Comparison Operators

;; 12.1. Equal
(deftest normalize-equal-test
  (testing "Normalizes both operands of an Equal expression"
    (given (normalize {:type "Equal" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 12.2. Equivalent
(deftest normalize-equivalent-test
  (testing "Normalizes both operands of an Equivalent expression"
    (given (normalize {:type "Equivalent" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 12.3. Greater
(deftest normalize-greater-test
  (testing "Normalizes both operands of a Greater expression"
    (given (normalize {:type "Greater" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 12.4. GreaterOrEqual
(deftest normalize-greater-or-equal-test
  (testing "Normalizes both operands of a GreaterOrEqual expression"
    (given (normalize {:type "GreaterOrEqual" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 12.5. Less
(deftest normalize-less-test
  (testing "Normalizes both operands of a Less expression"
    (given (normalize {:type "Less" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 12.6. LessOrEqual
(deftest normalize-less-or-equal-test
  (testing "Normalizes both operands of a LessOrEqual expression"
    (given (normalize {:type "LessOrEqual" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 12.7. NotEqual
(deftest normalize-not-equal-test
  (testing "A not-equal B normalizes to not (A equal B)"
    (given (normalize {:type "NotEqual" :operand [#elm/string "A" #elm/string "B"]})
      :type := "Not"
      [:operand :type] := "Equal"
      [:operand :operand] := [#elm/string "A" #elm/string "B"])))

;; 13. Logical Operators

;; 13.1. And
(deftest normalize-and-test
  (testing "Normalizes both operands of an And expression"
    (given (normalize {:type "And" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 13.2 Implies
(deftest normalize-implies-test
  (testing "A implies B normalizes to not A or B"
    (given (normalize {:type "Implies" :operand [#elm/string "A" #elm/string "B"]})
      :type := "Or"
      [:operand first :type] := "Not"
      [:operand first :operand] := #elm/string "A"
      [:operand second] := #elm/string "B")))

;; 13.3. Not
(deftest normalize-not-test
  (testing "Normalizes the operand of a Not expression"
    (given (normalize {:type "Not" :operand expression-1})
      :operand := (normalize expression-1))))

;; 13.4. Or
(deftest normalize-or-test
  (testing "Normalizes both operands of an Or expression"
    (given (normalize {:type "Or" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 13.5 Xor
(deftest normalize-xor-test
  (testing "Normalizes both operands of an Xor expression"
    (given (normalize {:type "Xor" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 14. Nullological Operators

;; 14.2. Coalesce
(deftest normalize-coalesce-test
  (testing "Normalizes all operands of an Coalesce expression"
    (given (normalize {:type "Coalesce" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 14.3. IsFalse
(deftest normalize-is-false-test
  (testing "Normalizes the operand of an IsFalse expression"
    (given (normalize {:type "IsFalse" :operand expression-1})
      :operand := (normalize expression-1))))

;; 14.4. IsNull
(deftest normalize-is-null-test
  (testing "Normalizes the operand of an IsNull expression"
    (given (normalize {:type "IsNull" :operand expression-1})
      :operand := (normalize expression-1))))

;; 14.5. IsTrue
(deftest normalize-is-true-test
  (testing "Normalizes the operand of an IsTrue expression"
    (given (normalize {:type "IsTrue" :operand expression-1})
      :operand := (normalize expression-1))))

;; 15. Conditional Operators

;; 15.1. Case
(deftest normalize-case-test
  (testing "Normalizes the case item and else without comparand"
    (given (normalize {:type "Case"
                       :caseItem
                       [{:when expression-1
                         :then expression-2}]
                       :else expression-3})
      :comparand := nil
      [:caseItem 0 :when] := (normalize expression-1)
      [:caseItem 0 :then] := (normalize expression-2)
      :else := (normalize expression-3)))

  (testing "Normalizes the case item, else and comparand"
    (given (normalize {:type "Case"
                       :comparand expression-4
                       :caseItem
                       [{:when expression-1
                         :then expression-2}]
                       :else expression-3})
      :comparand := (normalize expression-4)
      [:caseItem 0 :when] := (normalize expression-1)
      [:caseItem 0 :then] := (normalize expression-2)
      :else := (normalize expression-3)))

  (testing "Normalizes the optional comparand"
    (given (normalize {:type "Case"
                       :comparand expression-1
                       :caseItem
                       [{:when expression-2
                         :then expression-3}]
                       :else expression-4})
      :comparand := (normalize expression-1)
      [:caseItem 0 :when] := (normalize expression-2)
      [:caseItem 0 :then] := (normalize expression-3)
      :else := (normalize expression-4))))

;; 15.2. If
(deftest normalize-if-test
  (testing "Normalizes all arguments"
    (given (normalize {:type "If"
                       :condition expression-1
                       :then expression-2
                       :else expression-3})
      :condition := (normalize expression-1)
      :then := (normalize expression-2)
      :else := (normalize expression-3))))

;; 16. Arithmetic Operators

;; 16.1. Abs
(deftest normalize-abs-test
  (testing "Normalizes the operand of an Abs expression"
    (given (normalize {:type "Abs" :operand expression-1})
      :operand := (normalize expression-1))))

;; 16.2. Add
(deftest normalize-add-test
  (testing "Normalizes both operands of an Add expression"
    (given (normalize {:type "Add" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 16.3. Ceiling
(deftest normalize-ceiling-test
  (testing "Normalizes the operand of a Ceiling expression"
    (given (normalize {:type "Ceiling" :operand expression-1})
      :operand := (normalize expression-1))))

;; 16.4. Divide
(deftest normalize-divide-test
  (testing "Normalizes both operands of a Divide expression"
    (given (normalize {:type "Divide" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 16.5. Exp
(deftest normalize-exp-test
  (testing "Normalizes the operand of an Exp expression"
    (given (normalize {:type "Exp" :operand expression-1})
      :operand := (normalize expression-1))))

;; 16.6. Floor
(deftest normalize-floor-test
  (testing "Normalizes the operand of a Floor expression"
    (given (normalize {:type "Floor" :operand expression-1})
      :operand := (normalize expression-1))))

;; 16.7. HighBoundary
(deftest normalize-high-boundary-test
  (testing "Normalizes both operands of a HighBoundary expression"
    (given (normalize {:type "HighBoundary" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 16.8. Log
(deftest normalize-log-test
  (testing "Normalizes both operands of a Log expression"
    (given (normalize {:type "Log" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 16.9. LowBoundary
(deftest normalize-low-boundary-test
  (testing "Normalizes both operands of a LowBoundary expression"
    (given (normalize {:type "LowBoundary" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 16.10. Ln
(deftest normalize-ln-test
  (testing "Normalizes the operand of an Ln expression"
    (given (normalize {:type "Ln" :operand expression-1})
      :operand := (normalize expression-1))))

;; 16.11. MaxValue
(deftest normalize-max-value-test
  (given (normalize {:type "MaxValue" :valueType "{urn:hl7-org:elm-types:r1}Integer"})
    :type := "MaxValue"
    :valueType := "{urn:hl7-org:elm-types:r1}Integer"))

;; 16.12. MinValue
(deftest normalize-min-value-test
  (given (normalize {:type "MinValue" :valueType "{urn:hl7-org:elm-types:r1}Integer"})
    :type := "MinValue"
    :valueType := "{urn:hl7-org:elm-types:r1}Integer"))

;; 16.13. Modulo
(deftest normalize-modulo-test
  (testing "Normalizes both operands of a Modulo expression"
    (given (normalize {:type "Modulo" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 16.14. Multiply
(deftest normalize-multiply-test
  (testing "Normalizes both operands of a Multiply expression"
    (given (normalize {:type "Multiply" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 16.15. Negate
(deftest normalize-negate-test
  (testing "Normalizes the operand of a Negate expression"
    (given (normalize {:type "Negate" :operand expression-1})
      :operand := (normalize expression-1))))

;; 16.16. Power
(deftest normalize-power-test
  (testing "Normalizes both operands of a Power expression"
    (given (normalize {:type "Power" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 16.17. Precision
(deftest normalize-precision-test
  (testing "Normalizes the operand of a Precision expression"
    (given (normalize {:type "Precision" :operand expression-1})
      :operand := (normalize expression-1))))

;; 16.18. Predecessor
(deftest normalize-predecessor-test
  (testing "Normalizes the operand of a Predecessor expression"
    (given (normalize {:type "Predecessor" :operand expression-1})
      :operand := (normalize expression-1))))

;; 16.19. Round
(deftest normalize-round-test
  (testing "Normalizes the operand and precision of a Round expression"
    (given (normalize {:type "Round" :operand expression-1 :precision expression-2})
      :operand := (normalize expression-1)
      :precision := (normalize expression-2)))

  (testing "Normalizes a Round expression without precision"
    (given (normalize {:type "Round" :operand expression-1})
      :operand := (normalize expression-1)
      :precision := nil)))

;; 16.20. Subtract
(deftest normalize-subtract-test
  (testing "Normalizes both operands of a Subtract expression"
    (given (normalize {:type "Subtract" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 16.21. Successor
(deftest normalize-successor-test
  (testing "Normalizes the operand of a Successor expression"
    (given (normalize {:type "Successor" :operand expression-1})
      :operand := (normalize expression-1))))

;; 16.22. Truncate
(deftest normalize-truncate-test
  (testing "Normalizes the operand of a Truncate expression"
    (given (normalize {:type "Truncate" :operand expression-1})
      :operand := (normalize expression-1))))

;; 16.23. TruncatedDivide
(deftest normalize-truncated-divide-test
  (testing "Normalizes both operands of a TruncatedDivide expression"
    (given (normalize {:type "TruncatedDivide" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 17. String Operators

;; 17.1. Combine
(deftest normalize-combine-test
  (testing "Normalizes all operands of a Combine expression"
    (given (normalize {:type "Combine" :source expression-1 :separator expression-2})
      :source := (normalize expression-1)
      :separator := (normalize expression-2))))

;; 17.2. Concatenate
(deftest normalize-concatenate-test
  (testing "Normalizes all operands of a Concatenate expression"
    (given (normalize {:type "Concatenate" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 17.3. EndsWith
(deftest normalize-ends-with-test
  (testing "Normalizes both operands of an EndsWith expression"
    (given (normalize {:type "EndsWith" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 17.6. Indexer
(deftest normalize-indexer-test
  (testing "Normalizes both operands of an Indexer expression"
    (given (normalize {:type "Indexer" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 17.7. LastPositionOf
(deftest normalize-last-position-of-test
  (testing "Normalizes both operands of a LastPositionOf expression"
    (given (normalize {:type "LastPositionOf" :pattern expression-1 :string expression-2})
      :pattern := (normalize expression-1)
      :string := (normalize expression-2))))

;; 17.8. Length
(deftest normalize-length-test
  (testing "Normalizes the operand of a Length expression"
    (given (normalize {:type "Length" :operand expression-1})
      :operand := (normalize expression-1))))

;; 17.9. Lower
(deftest normalize-lower-test
  (testing "Normalizes the operand of a Lower expression"
    (given (normalize {:type "Lower" :operand expression-1})
      :operand := (normalize expression-1))))

;; 17.10. Matches
(deftest normalize-matches-test
  (testing "Normalizes both operands of a Matches expression"
    (given (normalize {:type "Matches" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 17.12. PositionOf
(deftest normalize-position-of-test
  (testing "Normalizes both operands of a PositionOf expression"
    (given (normalize {:type "PositionOf" :pattern expression-1 :string expression-2})
      :pattern := (normalize expression-1)
      :string := (normalize expression-2))))

;; 17.13. ReplaceMatches
(deftest normalize-replace-matches-test
  (testing "Normalizes all operands of a ReplaceMatches expression"
    (given (normalize {:type "ReplaceMatches" :operand [expression-1 expression-2 expression-3]})
      :operand := [(normalize expression-1) (normalize expression-2) (normalize expression-3)])))

;; 17.14. Split
(deftest normalize-split-test
  (testing "Normalizes both operands of a Split expression"
    (given (normalize {:type "Split" :stringToSplit expression-1 :separator expression-2})
      :stringToSplit := (normalize expression-1)
      :separator := (normalize expression-2))))

;; 17.15. SplitOnMatches
(deftest normalize-split-on-matches-test
  (testing "Normalizes both operands of a SplitOnMatches expression"
    (given (normalize {:type "SplitOnMatches" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 17.16. StartsWith
(deftest normalize-starts-with-test
  (testing "Normalizes both operands of a StartsWith expression"
    (given (normalize {:type "StartsWith" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 17.17. Substring
(deftest normalize-substring-test
  (testing "Normalizes all operands of a Substring expression"
    (given (normalize {:type "Substring"
                       :stringToSub expression-1
                       :startIndex expression-2
                       :length expression-3})
      :stringToSub := (normalize expression-1)
      :startIndex := (normalize expression-2)
      :length := (normalize expression-3))))

;; 17.18. Upper
(deftest normalize-upper-test
  (testing "Normalizes the operand of an Upper expression"
    (given (normalize {:type "Upper" :operand expression-1})
      :operand := (normalize expression-1))))

;; 18. Date and Time Operators

;; 18.6. Date
(deftest normalize-date-test
  (testing "Normalizes all components of a Date expression"
    (given (normalize {:type "Date" :year expression-1 :month expression-2 :day expression-3})
      :year := (normalize expression-1)
      :month := (normalize expression-2)
      :day := (normalize expression-3)))

  (testing "Normalizes a Date expression without month and day"
    (given (normalize {:type "Date" :year expression-1})
      :year := (normalize expression-1)
      :month := nil
      :day := nil)))

;; 18.7. DateFrom
(deftest normalize-date-from-test
  (given (normalize {:type "DateFrom" :operand expression-1})
    :operand := (normalize expression-1)))

;; 18.8. DateTime
(deftest normalize-date-time-test
  (testing "Normalizes all components of a DateTime expression"
    (given (normalize {:type "DateTime"
                       :year expression-1 :month expression-2 :day expression-3
                       :hour expression-4 :minute expression-1 :second expression-2
                       :millisecond expression-3 :timezoneOffset expression-4})
      :year := (normalize expression-1)
      :month := (normalize expression-2)
      :day := (normalize expression-3)
      :hour := (normalize expression-4)
      :minute := (normalize expression-1)
      :second := (normalize expression-2)
      :millisecond := (normalize expression-3)
      :timezoneOffset := (normalize expression-4)))

  (testing "Normalizes a DateTime expression with only year"
    (given (normalize {:type "DateTime" :year expression-1})
      :year := (normalize expression-1)
      :month := nil
      :day := nil
      :hour := nil
      :minute := nil
      :second := nil
      :millisecond := nil
      :timezoneOffset := nil)))

;; 18.9. DateTimeComponentFrom
(deftest normalize-date-time-component-from-test
  (given (normalize {:type "DateTimeComponentFrom" :operand expression-1 :precision "Year"})
    :operand := (normalize expression-1)))

;; 18.10. DifferenceBetween
(deftest normalize-difference-between-test
  (given (normalize {:type "DifferenceBetween" :operand [expression-1 expression-2] :precision "Year"})
    :operand := [(normalize expression-1) (normalize expression-2)]))

;; 18.11. DurationBetween
(deftest normalize-duration-between-test
  (given (normalize {:type "DurationBetween" :operand [expression-1 expression-2] :precision "Year"})
    :operand := [(normalize expression-1) (normalize expression-2)]))

;; 18.13. Now
(deftest normalize-now-test
  (given (normalize {:type "Now"})
    :type := "Now"))

;; 18.14. SameAs
(deftest normalize-same-as-test
  (given (normalize {:type "SameAs" :operand [expression-1 expression-2] :precision "Year"})
    :operand := [(normalize expression-1) (normalize expression-2)]))

;; 18.15. SameOrBefore
(deftest normalize-same-or-before-test
  (given (normalize {:type "SameOrBefore" :operand [expression-1 expression-2] :precision "Year"})
    :operand := [(normalize expression-1) (normalize expression-2)]))

;; 18.16. SameOrAfter
(deftest normalize-same-or-after-test
  (given (normalize {:type "SameOrAfter" :operand [expression-1 expression-2] :precision "Year"})
    :operand := [(normalize expression-1) (normalize expression-2)]))

;; 18.18. Time
(deftest normalize-time-test
  (testing "Normalizes all components of a Time expression"
    (given (normalize {:type "Time"
                       :hour expression-1 :minute expression-2 :second expression-3
                       :millisecond expression-4})
      :hour := (normalize expression-1)
      :minute := (normalize expression-2)
      :second := (normalize expression-3)
      :millisecond := (normalize expression-4)))

  (testing "Normalizes a Time expression with only hour"
    (given (normalize {:type "Time" :hour expression-1})
      :hour := (normalize expression-1)
      :minute := nil
      :second := nil
      :millisecond := nil)))

;; 18.21. TimeOfDay
(deftest normalize-time-of-day-test
  (given (normalize {:type "TimeOfDay"})
    :type := "TimeOfDay"))

;; 18.22. Today
(deftest normalize-today-test
  (given (normalize {:type "Today"})
    :type := "Today"))

;; 18.15. TimezoneOffsetFrom
(deftest normalize-timezone-offset-from-test
  (given (normalize {:type "TimezoneOffsetFrom" :operand expression-1})
    :operand := (normalize expression-1)))

;; 19. Interval Operators

;; 19.1. Interval
(deftest normalize-interval-test
  (testing "Normalizes all bounds and closed expressions of an Interval expression"
    (given (normalize {:type "Interval"
                       :low expression-1
                       :high expression-2
                       :lowClosedExpression expression-3
                       :highClosedExpression expression-4})
      :low := (normalize expression-1)
      :high := (normalize expression-2)
      :lowClosedExpression := (normalize expression-3)
      :highClosedExpression := (normalize expression-4)))

  (testing "Normalizes an Interval expression without bounds"
    (given (normalize {:type "Interval"})
      :low := nil
      :high := nil
      :lowClosedExpression := nil
      :highClosedExpression := nil)))

;; 19.2. After
(deftest normalize-after-test
  (testing "Normalizes both operands of an After expression"
    (given (normalize {:type "After" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 19.3. Before
(deftest normalize-before-test
  (testing "Normalizes both operands of a Before expression"
    (given (normalize {:type "Before" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 19.4. Collapse
(deftest normalize-collapse-test
  (testing "Normalizes both operands of a Collapse expression"
    (given (normalize {:type "Collapse" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 19.5. Contains
(deftest normalize-contains-test
  (testing "A contains B normalizes to A contains B"
    (given (normalize {:type "Contains" :operand [expression-1 expression-2]})
      :type := "Contains"
      :operand := [(normalize expression-1) (normalize expression-2)]))

  (testing "A contains B with precision normalizes to A contains B with precision"
    (given (normalize {:type "Contains" :operand [expression-1 expression-2] :precision "year"})
      :type := "Contains"
      :operand := [(normalize expression-1) (normalize expression-2)]
      :precision := "year")))

;; 19.6. End
(deftest normalize-end-test
  (testing "Normalizes the operand of an End expression"
    (given (normalize {:type "End" :operand expression-1})
      :operand := (normalize expression-1))))

;; 19.7. Ends
(deftest normalize-ends-test
  (testing "Normalizes both operands of an Ends expression"
    (given (normalize {:type "Ends" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 19.10. Except
(deftest normalize-except-test
  (testing "Normalizes both operands of an Except expression"
    (given (normalize {:type "Except" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 19.11. Expand
(deftest normalize-expand-test
  (testing "Normalizes both operands of an Expand expression"
    (given (normalize {:type "Expand" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 19.12. In
(deftest normalize-in-test
  (testing "A in B normalizes to B contains A"
    (given (normalize {:type "In" :operand [expression-1 expression-2]})
      :type := "Contains"
      :operand := [(normalize expression-2) (normalize expression-1)]))

  (testing "A in B with precision normalizes to B contains A with precision"
    (given (normalize {:type "In" :operand [expression-1 expression-2] :precision "year"})
      :type := "Contains"
      :operand := [(normalize expression-2) (normalize expression-1)]
      :precision := "year")))

;; 19.13. Includes
(deftest normalize-includes-test
  (testing "A includes B normalizes to A includes B"
    (given (normalize {:type "Includes" :operand [expression-1 expression-2]})
      :type := "Includes"
      :operand := [(normalize expression-1) (normalize expression-2)]))

  (testing "A includes B with precision normalizes to A includes B with precision"
    (given (normalize {:type "Includes" :operand [expression-1 expression-2] :precision "year"})
      :type := "Includes"
      :operand := [(normalize expression-1) (normalize expression-2)]
      :precision := "year")))

;; 19.14. IncludedIn
(deftest normalize-included-in-test
  (testing "A included in B normalizes to B includes A"
    (given (normalize {:type "IncludedIn" :operand [expression-1 expression-2]})
      :type := "Includes"
      :operand := [(normalize expression-2) (normalize expression-1)]))

  (testing "A included in B with precision normalizes to B includes A with precision"
    (given (normalize {:type "IncludedIn" :operand [expression-1 expression-2] :precision "year"})
      :type := "Includes"
      :operand := [(normalize expression-2) (normalize expression-1)]
      :precision := "year")))

;; 19.15. Intersect
(deftest normalize-intersect-test
  (testing "Normalizes both operands of an Intersect expression"
    (given (normalize {:type "Intersect" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 19.16. Meets
(deftest normalize-meets-test
  (testing "A meets B normalizes to (A meets-before B) or (A meets-after B)"
    (given (normalize {:type "Meets" :operand [expression-1 expression-2]})
      :type := "Or"
      [:operand 0 :type] := "MeetsBefore"
      [:operand 0 :operand] := [(normalize expression-1) (normalize expression-2)]
      [:operand 1 :type] := "MeetsAfter"
      [:operand 1 :operand] := [(normalize expression-1) (normalize expression-2)]))

  (testing "A meets B with precision normalizes to (A meets-before B) or (A meets-after B) with precision"
    (given (normalize {:type "Meets" :operand [expression-1 expression-2] :precision "year"})
      :type := "Or"
      [:operand 0 :type] := "MeetsBefore"
      [:operand 0 :operand] := [(normalize expression-1) (normalize expression-2)]
      [:operand 0 :precision] := "year"
      [:operand 1 :type] := "MeetsAfter"
      [:operand 1 :operand] := [(normalize expression-1) (normalize expression-2)]
      [:operand 1 :precision] := "year")))

;; 19.17. MeetsBefore
(deftest normalize-meets-before-test
  (testing "Normalizes both operands of a MeetsBefore expression"
    (given (normalize {:type "MeetsBefore" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 19.18. MeetsAfter
(deftest normalize-meets-after-test
  (testing "Normalizes both operands of a MeetsAfter expression"
    (given (normalize {:type "MeetsAfter" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 19.20. Overlaps
(deftest normalize-overlaps-test
  (testing "Normalizes both operands of an Overlaps expression"
    (given (normalize {:type "Overlaps" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 19.21. OverlapsBefore
(deftest normalize-overlaps-before-test
  (testing "A overlaps-before B normalizes to A proper-contains start of B"
    (given (normalize {:type "OverlapsBefore" :operand [expression-1 expression-2]})
      :type := "ProperContains"
      [:operand 0] := (normalize expression-1)
      [:operand 1 :type] := "Start"
      [:operand 1 :operand] := (normalize expression-2)))

  (testing "A overlaps-before B normalizes to A proper-contains start of B with resultTypeName"
    (given (normalize {:type "OverlapsBefore" :operand [expression-1 (assoc expression-2 :resultTypeName "{urn:hl7-org:elm-types:r1}DateTime")]})
      :type := "ProperContains"
      [:operand 0] := (normalize expression-1)
      [:operand 1 :type] := "Start"
      [:operand 1 :operand] := (normalize (assoc expression-2 :resultTypeName "{urn:hl7-org:elm-types:r1}DateTime"))
      [:operand 1 :resultTypeName] := "{urn:hl7-org:elm-types:r1}DateTime"))

  (testing "A overlaps-before B with precision normalizes to A proper-contains start of B with precision"
    (given (normalize {:type "OverlapsBefore" :operand [expression-1 expression-2] :precision "year"})
      :type := "ProperContains"
      [:operand 0] := (normalize expression-1)
      [:operand 1 :type] := "Start"
      [:operand 1 :operand] := (normalize expression-2)
      :precision := "year")))

;; 19.22. OverlapsAfter
(deftest normalize-overlaps-after-test
  (testing "A overlaps-after B normalizes to A proper-contains end of B"
    (given (normalize {:type "OverlapsAfter" :operand [expression-1 expression-2]})
      :type := "ProperContains"
      [:operand 0] := (normalize expression-1)
      [:operand 1 :type] := "End"
      [:operand 1 :operand] := (normalize expression-2)))

  (testing "A overlaps-after B normalizes to A proper-contains end of B with resultTypeName"
    (given (normalize {:type "OverlapsAfter" :operand [expression-1 (assoc expression-2 :resultTypeName "{urn:hl7-org:elm-types:r1}DateTime")]})
      :type := "ProperContains"
      [:operand 0] := (normalize expression-1)
      [:operand 1 :type] := "End"
      [:operand 1 :operand] := (normalize (assoc expression-2 :resultTypeName "{urn:hl7-org:elm-types:r1}DateTime"))
      [:operand 1 :resultTypeName] := "{urn:hl7-org:elm-types:r1}DateTime"))

  (testing "A overlaps-after B with precision normalizes to A proper-contains end of B with precision"
    (given (normalize {:type "OverlapsAfter" :operand [expression-1 expression-2] :precision "year"})
      :type := "ProperContains"
      [:operand 0] := (normalize expression-1)
      [:operand 1 :type] := "End"
      [:operand 1 :operand] := (normalize expression-2)
      :precision := "year")))

;; 19.23. PointFrom
(deftest normalize-point-from-test
  (testing "Normalizes the operand of a PointFrom expression"
    (given (normalize {:type "PointFrom" :operand expression-1})
      :operand := (normalize expression-1))))

;; 19.24. ProperContains
(deftest normalize-proper-contains-test
  (testing "A proper-contains B normalizes to A proper-contains B"
    (given (normalize {:type "ProperContains" :operand [expression-1 expression-2]})
      :type := "ProperContains"
      :operand := [(normalize expression-1) (normalize expression-2)]))

  (testing "A proper-contains B with precision normalizes to A proper-contains B with precision"
    (given (normalize {:type "ProperContains" :operand [expression-1 expression-2] :precision "year"})
      :type := "ProperContains"
      :operand := [(normalize expression-1) (normalize expression-2)]
      :precision := "year")))

;; 19.25. ProperIn
(deftest normalize-proper-in-test
  (testing "A proper-in B normalizes to B proper-contains A"
    (given (normalize {:type "ProperIn" :operand [expression-1 expression-2]})
      :type := "ProperContains"
      :operand := [(normalize expression-2) (normalize expression-1)]))

  (testing "A proper-in B with precision normalizes to B proper-contains A with precision"
    (given (normalize {:type "ProperIn" :operand [expression-1 expression-2] :precision "year"})
      :type := "ProperContains"
      :operand := [(normalize expression-2) (normalize expression-1)]
      :precision := "year")))

;; 19.26. ProperIncludes
(deftest normalize-proper-includes-test
  (testing "A proper-includes B normalizes to A proper-includes B"
    (given (normalize {:type "ProperIncludes" :operand [expression-1 expression-2]})
      :type := "ProperIncludes"
      :operand := [(normalize expression-1) (normalize expression-2)]))

  (testing "A proper-includes B with precision normalizes to A proper-includes B with precision"
    (given (normalize {:type "ProperIncludes" :operand [expression-1 expression-2] :precision "year"})
      :type := "ProperIncludes"
      :operand := [(normalize expression-1) (normalize expression-2)]
      :precision := "year")))

;; 19.27. ProperIncludedIn
(deftest normalize-proper-included-in-test
  (testing "A proper-included-in B normalizes to B proper-includes A"
    (given (normalize {:type "ProperIncludedIn" :operand [expression-1 expression-2]})
      :type := "ProperIncludes"
      :operand := [(normalize expression-2) (normalize expression-1)]))

  (testing "A proper-included-in B with precision normalizes to B proper-includes A with precision"
    (given (normalize {:type "ProperIncludedIn" :operand [expression-1 expression-2] :precision "year"})
      :type := "ProperIncludes"
      :operand := [(normalize expression-2) (normalize expression-1)]
      :precision := "year")))

;; 19.29. Size
(deftest normalize-size-test
  (testing "Normalizes the operand of a Size expression"
    (given (normalize {:type "Size" :operand expression-1})
      :operand := (normalize expression-1))))

;; 19.29. Start
(deftest normalize-start-test
  (testing "Normalizes the operand of a Start expression"
    (given (normalize {:type "Start" :operand expression-1})
      :operand := (normalize expression-1))))

;; 19.30. Starts
(deftest normalize-starts-test
  (testing "Normalizes both operands of a Starts expression"
    (given (normalize {:type "Starts" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 19.31. Union
(deftest normalize-union-test
  (testing "Normalizes both operands of a Union expression"
    (given (normalize {:type "Union" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 19.32. Width
(deftest normalize-width-test
  (testing "Normalizes the operand of a Width expression"
    (given (normalize {:type "Width" :operand expression-1})
      :operand := (normalize expression-1))))

;; 20. List Operators

;; 20.1. List
(deftest normalize-list-test
  (testing "Normalizes all elements of a List expression"
    (given (normalize {:type "List" :element [expression-1 expression-2]})
      :element := [(normalize expression-1) (normalize expression-2)])))

;; 20.4. Distinct
(deftest normalize-distinct-test
  (given (normalize (elm/distinct expression-1))
    :operand := (normalize expression-1)))

;; 20.8. Exists
(deftest normalize-exists-test
  (given (normalize (elm/exists expression-1))
    :operand := (normalize expression-1)))

;; 20.9. Filter
(deftest normalize-filter-test
  (testing "Normalizes all arguments of a Filter expression"
    (given (normalize {:type "Filter" :source expression-1 :condition expression-2})
      :source := (normalize expression-1)
      :condition := (normalize expression-2))))

;; 20.10. First
(deftest normalize-first-test
  (testing "Normalizes the source and orderBy"
    (given (normalize {:type "First"
                       :source expression-1
                       :orderBy
                       [{:type "ByExpression" :expression expression-2}
                        {:type "ByColumn" :path "a"}]})
      :source := (normalize expression-1)
      [:orderBy 0 :expression] := (normalize expression-2)
      [:orderBy 1 :type] := "ByColumn"
      [:orderBy 1 :path] := "a")))

;; 20.11. Flatten
(deftest normalize-flatten-test
  (given (normalize (elm/flatten expression-1))
    :operand := (normalize expression-1)))

;; 20.12. ForEach
(deftest normalize-for-each-test
  (testing "Normalizes all arguments of a ForEach expression"
    (given (normalize {:type "ForEach" :source expression-1 :element expression-2})
      :source := (normalize expression-1)
      :element := (normalize expression-2))))

;; 20.16. IndexOf
(deftest normalize-index-of-test
  (testing "Normalizes all arguments of an IndexOf expression"
    (given (normalize {:type "IndexOf" :source expression-1 :element expression-2})
      :source := (normalize expression-1)
      :element := (normalize expression-2))))

;; 20.18. Last
(deftest normalize-last-test
  (testing "Normalizes the source and orderBy"
    (given (normalize {:type "Last"
                       :source expression-1
                       :orderBy
                       [{:type "ByExpression" :expression expression-2}]})
      :source := (normalize expression-1)
      [:orderBy 0 :expression] := (normalize expression-2))))

;; 20.24. Repeat
(deftest normalize-repeat-test
  (testing "Normalizes all arguments of a Repeat expression"
    (given (normalize {:type "Repeat" :source expression-1 :element expression-2})
      :source := (normalize expression-1)
      :element := (normalize expression-2))))

;; 20.25. SingletonFrom
(deftest normalize-singleton-from-test
  (given (normalize (elm/singleton-from expression-1))
    :operand := (normalize expression-1)))

;; 20.26. Slice
(deftest normalize-slice-test
  (testing "Normalizes all arguments of a Slice expression"
    (given (normalize {:type "Slice"
                       :source expression-1
                       :startIndex expression-2
                       :endIndex expression-3})
      :source := (normalize expression-1)
      :startIndex := (normalize expression-2)
      :endIndex := (normalize expression-3)))

  (testing "Normalizes a Slice expression without optional arguments"
    (given (normalize {:type "Slice" :source expression-1})
      :source := (normalize expression-1)
      :startIndex := nil
      :endIndex := nil)))

;; 20.27. Sort
(deftest normalize-sort-test
  (testing "Normalizes all arguments of a Sort expression"
    (given (normalize {:type "Sort"
                       :source expression-1
                       :by
                       [{:type "ByExpression" :expression expression-2}]})
      :source := (normalize expression-1)
      [:by 0 :expression] := (normalize expression-2))))

;; 20.28. Times
(deftest normalize-times-test
  (testing "Normalizes both operands of a Times expression"
    (given (normalize {:type "Times" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 21. Aggregate Operators

;; 21.1. AllTrue
(deftest normalize-all-true-test
  (given (normalize {:type "AllTrue" :source expression-1})
    :source := (normalize expression-1)))

;; 21.2. AnyTrue
(deftest normalize-any-true-test
  (given (normalize {:type "AnyTrue" :source expression-1})
    :source := (normalize expression-1)))

;; 21.3. Avg
(deftest normalize-avg-test
  (given (normalize {:type "Avg" :source expression-1})
    :source := (normalize expression-1)))

;; 21.4. Count
(deftest normalize-count-test
  (given (normalize {:type "Count" :source expression-1})
    :source := (normalize expression-1)))

;; 21.5. GeometricMean
(deftest normalize-geometric-mean-test
  (given (normalize {:type "GeometricMean" :source expression-1})
    :source := (normalize expression-1)))

;; 21.6. Product
(deftest normalize-product-test
  (given (normalize {:type "Product" :source expression-1})
    :source := (normalize expression-1)))

;; 21.7. Max
(deftest normalize-max-test
  (given (normalize {:type "Max" :source expression-1})
    :source := (normalize expression-1)))

;; 21.8. Median
(deftest normalize-median-test
  (given (normalize {:type "Median" :source expression-1})
    :source := (normalize expression-1)))

;; 21.9. Min
(deftest normalize-min-test
  (given (normalize {:type "Min" :source expression-1})
    :source := (normalize expression-1)))

;; 21.10. Mode
(deftest normalize-mode-test
  (given (normalize {:type "Mode" :source expression-1})
    :source := (normalize expression-1)))

;; 21.11. PopulationVariance
(deftest normalize-population-variance-test
  (given (normalize {:type "PopulationVariance" :source expression-1})
    :source := (normalize expression-1)))

;; 21.12. PopulationStdDev
(deftest normalize-population-std-dev-test
  (given (normalize {:type "PopulationStdDev" :source expression-1})
    :source := (normalize expression-1)))

;; 21.13. Sum
(deftest normalize-sum-test
  (given (normalize {:type "Sum" :source expression-1})
    :source := (normalize expression-1)))

;; 21.14. StdDev
(deftest normalize-std-dev-test
  (given (normalize {:type "StdDev" :source expression-1})
    :source := (normalize expression-1)))

;; 21.15. Variance
(deftest normalize-variance-test
  (given (normalize {:type "Variance" :source expression-1})
    :source := (normalize expression-1)))

;; 22. Type Operators

;; 22.1. As
(deftest normalize-as-test
  (testing "Normalizes the operand of an As expression"
    (given (normalize {:type "As" :operand expression-1 :asType "{urn:hl7-org:elm-types:r1}Integer"})
      :operand := (normalize expression-1))))

;; 22.3. CanConvertQuantity
(deftest normalize-can-convert-quantity-test
  (testing "Normalizes both operands of a CanConvertQuantity expression"
    (given (normalize {:type "CanConvertQuantity" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 22.4. Children
(deftest normalize-children-test
  (testing "Normalizes the source of a Children expression"
    (given (normalize {:type "Children" :source expression-1})
      :source := (normalize expression-1))))

;; 22.6. ConvertQuantity
(deftest normalize-convert-quantity-test
  (testing "Normalizes both operands of a ConvertQuantity expression"
    (given (normalize {:type "ConvertQuantity" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)])))

;; 22.7. ConvertsToBoolean
(deftest normalize-converts-to-boolean-test
  (testing "Normalizes the operand of a ConvertsToBoolean expression"
    (given (normalize {:type "ConvertsToBoolean" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.8. ConvertsToDate
(deftest normalize-converts-to-date-test
  (testing "Normalizes the operand of a ConvertsToDate expression"
    (given (normalize {:type "ConvertsToDate" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.9. ConvertsToDateTime
(deftest normalize-converts-to-date-time-test
  (testing "Normalizes the operand of a ConvertsToDateTime expression"
    (given (normalize {:type "ConvertsToDateTime" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.10. ConvertsToDecimal
(deftest normalize-converts-to-decimal-test
  (testing "Normalizes the operand of a ConvertsToDecimal expression"
    (given (normalize {:type "ConvertsToDecimal" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.11. ConvertsToLong
(deftest normalize-converts-to-long-test
  (testing "Normalizes the operand of a ConvertsToLong expression"
    (given (normalize {:type "ConvertsToLong" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.12. ConvertsToInteger
(deftest normalize-converts-to-integer-test
  (testing "Normalizes the operand of a ConvertsToInteger expression"
    (given (normalize {:type "ConvertsToInteger" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.13. ConvertsToQuantity
(deftest normalize-converts-to-quantity-test
  (testing "Normalizes the operand of a ConvertsToQuantity expression"
    (given (normalize {:type "ConvertsToQuantity" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.14. ConvertsToRatio
(deftest normalize-converts-to-ratio-test
  (testing "Normalizes the operand of a ConvertsToRatio expression"
    (given (normalize {:type "ConvertsToRatio" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.15. ConvertsToString
(deftest normalize-converts-to-string-test
  (testing "Normalizes the operand of a ConvertsToString expression"
    (given (normalize {:type "ConvertsToString" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.16. ConvertsToTime
(deftest normalize-converts-to-time-test
  (testing "Normalizes the operand of a ConvertsToTime expression"
    (given (normalize {:type "ConvertsToTime" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.17. Descendents
(deftest normalize-descendents-test
  (testing "Normalizes the source of a Descendents expression"
    (given (normalize {:type "Descendents" :source expression-1})
      :source := (normalize expression-1))))

;; 22.18. Is
(deftest normalize-is-test
  (testing "Normalizes the operand of an Is expression"
    (given (normalize {:type "Is" :operand expression-1 :isType "{urn:hl7-org:elm-types:r1}Integer"})
      :operand := (normalize expression-1))))

;; 22.19. ToBoolean
(deftest normalize-to-boolean-test
  (testing "Normalizes the operand of a ToBoolean expression"
    (given (normalize {:type "ToBoolean" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.20. ToChars
(deftest normalize-to-chars-test
  (testing "Normalizes the operand of a ToChars expression"
    (given (normalize {:type "ToChars" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.21. ToConcept
(deftest normalize-to-concept-test
  (testing "Normalizes the operand of a ToConcept expression"
    (given (normalize {:type "ToConcept" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.22. ToDate
(deftest normalize-to-date-test
  (testing "Normalizes the operand of a ToDate expression"
    (given (normalize {:type "ToDate" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.23. ToDateTime
(deftest normalize-to-date-time-test
  (testing "Normalizes the operand of a ToDateTime expression"
    (given (normalize {:type "ToDateTime" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.24. ToDecimal
(deftest normalize-to-decimal-test
  (testing "Normalizes the operand of a ToDecimal expression"
    (given (normalize {:type "ToDecimal" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.25. ToInteger
(deftest normalize-to-integer-test
  (testing "Normalizes the operand of a ToInteger expression"
    (given (normalize {:type "ToInteger" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.26. ToList
(deftest normalize-to-list-test
  (testing "Normalizes the operand of a ToList expression"
    (given (normalize {:type "ToList" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.27. ToLong
(deftest normalize-to-long-test
  (testing "Normalizes the operand of a ToLong expression"
    (given (normalize {:type "ToLong" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.28. ToQuantity
(deftest normalize-to-quantity-test
  (testing "Normalizes the operand of a ToQuantity expression"
    (given (normalize {:type "ToQuantity" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.29. ToRatio
(deftest normalize-to-ratio-test
  (testing "Normalizes the operand of a ToRatio expression"
    (given (normalize {:type "ToRatio" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.30. ToString
(deftest normalize-to-string-test
  (testing "Normalizes the operand of a ToString expression"
    (given (normalize {:type "ToString" :operand expression-1})
      :operand := (normalize expression-1))))

;; 22.31. ToTime
(deftest normalize-to-time-test
  (testing "Normalizes the operand of a ToTime expression"
    (given (normalize {:type "ToTime" :operand expression-1})
      :operand := (normalize expression-1))))

;; 23. Clinical Operators

;; 23.3. CalculateAge
(deftest normalize-calculate-age-test
  (testing "Normalizes the operand"
    (given (normalize {:type "CalculateAge" :operand expression-1 :precision "Year"})
      :type := "CalculateAgeAt"
      [:operand 0] := (normalize expression-1)
      [:operand 1 :type] := "Today"
      :precision := "Year")))

;; 23.4. CalculateAgeAt
(deftest normalize-calculate-age-at-test
  (testing "Normalizes both operands"
    (given (normalize {:type "CalculateAgeAt" :operand [expression-1 expression-2] :precision "Year"})
      :operand := [(normalize expression-1) (normalize expression-2)]))

  (testing "Normalizes a CalculateAgeAt expression without precision"
    (given (normalize {:type "CalculateAgeAt" :operand [expression-1 expression-2]})
      :operand := [(normalize expression-1) (normalize expression-2)]
      :precision := nil)))

;; 23.7. InCodeSystem
(deftest normalize-in-code-system-test
  (testing "Normalizes the code and codesystem expression"
    (given (normalize {:type "InCodeSystem" :code expression-1 :codesystemExpression expression-2})
      :code := (normalize expression-1)
      :codesystemExpression := (normalize expression-2)))

  (testing "Normalizes an InCodeSystem expression without codesystemExpression"
    (given (normalize {:type "InCodeSystem" :code expression-1})
      :code := (normalize expression-1)
      :codesystemExpression := nil)))

;; 23.8. InValueSet
(deftest normalize-in-value-set-test
  (testing "Normalizes the code and valueset expression"
    (given (normalize {:type "InValueSet" :code expression-1 :valuesetExpression expression-2})
      :code := (normalize expression-1)
      :valuesetExpression := (normalize expression-2)))

  (testing "Normalizes an InValueSet expression without valuesetExpression"
    (given (normalize {:type "InValueSet" :code expression-1})
      :code := (normalize expression-1)
      :valuesetExpression := nil)))

;; 24. Errors and Messages

;; 24.1. Message
(deftest normalize-message-test
  (testing "Normalizes all operands of a Message expression"
    (given (normalize {:type "Message"
                       :source expression-1
                       :condition expression-2
                       :code expression-3
                       :severity expression-4
                       :message expression-5})
      :source := (normalize expression-1)
      :condition := (normalize expression-2)
      :code := (normalize expression-3)
      :severity := (normalize expression-4)
      :message := (normalize expression-5)))

  (testing "Normalizes a Message expression without optional operands"
    (given (normalize {:type "Message" :source expression-1})
      :source := (normalize expression-1)
      :condition := nil
      :code := nil
      :severity := nil
      :message := nil)))
