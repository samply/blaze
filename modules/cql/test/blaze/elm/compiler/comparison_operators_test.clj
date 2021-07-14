(ns blaze.elm.compiler.comparison-operators-test
  (:require
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.comparison-operators]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]))


(st/instrument)
(tu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (tu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; 12.1. Equal
;;
;; The Equal operator returns true if the arguments are equal; false if the
;; arguments are known unequal, and null otherwise. Equality semantics are
;; defined to be value-based.
;;
;; For simple types, this means that equality returns true if and only if the
;; result of each argument evaluates to the same value.
;;
;; For string values, equality is strictly lexical based on the Unicode values
;; for the individual characters in the strings.
;;
;; For decimal values, trailing zeroes are ignored.
;;
;; For quantities, this means that the dimensions of each quantity must be the
;; same, but not necessarily the unit. For example, units of 'cm' and 'm' are
;; comparable, but units of 'cm2' and 'cm' are not. Attempting to operate on
;; quantities with invalid units will result in a run-time error.
;;
;; For ratios, this means that the numerator and denominator must be the same,
;; using quantity equality semantics.
;;
;; For tuple types, this means that equality returns true if and only if the
;; tuples are of the same type, and the values for all elements that have
;; values, by name, are equal.
;;
;; For list types, this means that equality returns true if and only if the
;; lists contain elements of the same type, have the same number of elements,
;; and for each element in the lists, in order, the elements are equal using
;; equality semantics, with the exception that null elements are considered
;; equal.
;;
;; For interval types, equality returns true if and only if the intervals are
;; over the same point type, and they have the same value for the starting and
;; ending points of the interval as determined by the Start and End operators.
;;
;; For Date, DateTime, and Time values, the comparison is performed by
;; considering each precision in order, beginning with years (or hours for time
;; values). If the values are the same, comparison proceeds to the next
;; precision; if the values are different, the comparison stops and the result
;; is false. If one input has a value for the precision and the other does not,
;; the comparison stops and the result is null; if neither input has a value for
;; the precision or the last precision has been reached, the comparison stops
;; and the result is true. For the purposes of comparison, seconds and
;; milliseconds are combined as a single precision using a decimal, with decimal
;; equality semantics.
;;
;; If either argument is null, the result is null.
(deftest compile-equal-test
  (testing "Integer"
    (are [x y res] (= res (tu/compile-binop elm/equal elm/integer x y))
      "1" "1" true
      "1" "2" false
      "2" "1" false)

    (tu/testing-binary-null elm/equal #elm/integer"1"))

  (testing "Decimal"
    (are [x y res] (= res (tu/compile-binop elm/equal elm/decimal x y))
      "1.1" "1.1" true
      "1.1" "2.1" false
      "2.1" "1.1" false

      "1.1" "1.10" true
      "1.10" "1.1" true)

    (tu/testing-binary-null elm/equal #elm/decimal"1.1"))

  (testing "Mixed Integer Decimal"
    (are [x y res] (= res (c/compile {} (elm/equal [x y])))
      #elm/integer"1" #elm/decimal"1" true
      #elm/decimal"1" #elm/integer"1" true))

  (testing "Mixed Integer String"
    (are [x y res] (= res (c/compile {} (elm/equal [x y])))
      #elm/integer"1" #elm/string"1" false
      #elm/string"1" #elm/integer"1" false))

  (testing "Mixed Decimal String"
    (are [x y res] (= res (c/compile {} (elm/equal [x y])))
      #elm/decimal"1" #elm/string"1" false
      #elm/string"1" #elm/decimal"1" false))

  (testing "String"
    (are [x y res] (= res (tu/compile-binop elm/equal elm/string x y))
      "a" "a" true
      "a" "b" false
      "b" "a" false)

    (tu/testing-binary-null elm/equal #elm/string "a"))

  (testing "Quantity"
    (are [x y res] (= res (tu/compile-binop elm/equal elm/quantity x y))
      [1] [1] true
      [1] [2] false

      [1 "s"] [1 "s"] true
      [1 "m"] [1 "m"] true
      [100 "cm"] [1 "m"] true
      [1 "s"] [2 "s"] false
      [1 "s"] [1 "m"] false)

    (tu/testing-binary-null elm/equal #elm/quantity[1]))

  ;; TODO: Ratio

  (testing "Tuple"
    (are [x y res] (= res (tu/compile-binop elm/equal elm/tuple x y))
      {} {} true
      {"id" #elm/string"1"} {"id" #elm/string"1"} true
      {"id" #elm/string"1"} {"id" #elm/string"2"} false
      {"id" #elm/string"1"} {"foo" #elm/string"1"} false))

  (testing "List"
    (are [x y res] (= res (tu/compile-binop elm/equal elm/list x y))
      [#elm/integer"1"] [#elm/integer"1"] true
      [] [] true

      [#elm/integer"1"] [] false
      [#elm/integer"1"] [#elm/integer"2"] false
      [#elm/integer"1" #elm/integer"1"]
      [#elm/integer"1" #elm/integer"2"] false

      [#elm/integer"1" {:type "Null"}] [#elm/integer"1" {:type "Null"}] nil
      [{:type "Null"}] [{:type "Null"}] nil
      [#elm/date"2019"] [#elm/date"2019-01"] nil)

    (tu/testing-binary-null elm/equal #elm/list []))

  (testing "Interval"
    (are [x y res] (= res (tu/compile-binop elm/equal elm/interval x y))
      [#elm/integer"1" #elm/integer"2"]
      [#elm/integer"1" #elm/integer"2"] true)

    (tu/testing-binary-null elm/equal #elm/interval [#elm/integer"1" #elm/integer"2"]))

  (testing "Date with year precision"
    (are [x y res] (= res (tu/compile-binop elm/equal elm/date x y))
      "2013" "2013" true
      "2012" "2013" false
      "2013" "2012" false)

    (tu/testing-binary-null elm/equal #elm/date"2013"))

  (testing "Date with year-month precision"
    (are [x y res] (= res (tu/compile-binop elm/equal elm/date x y))
      "2013-01" "2013-01" true
      "2013-01" "2013-02" false
      "2013-02" "2013-01" false)

    (tu/testing-binary-null elm/equal #elm/date"2013-01"))

  (testing "Date with full precision"
    (are [x y res] (= res (tu/compile-binop elm/equal elm/date x y))
      "2013-01-01" "2013-01-01" true
      "2013-01-01" "2013-01-02" false
      "2013-01-02" "2013-01-01" false)

    (tu/testing-binary-null elm/equal #elm/date"2013-01-01"))

  (testing "Date with differing precisions"
    (are [x y res] (= res (tu/compile-binop elm/equal elm/date x y))
      "2013" "2013-01" nil))

  (testing "Today() = Today()"
    (are [x y] (true? (core/-eval (c/compile {} (elm/equal [x y])) {:now tu/now} nil nil))
      {:type "Today"} {:type "Today"}))

  (testing "DateTime with full precision (there is only one precision)"
    (are [x y res] (= res (tu/compile-binop elm/equal elm/date-time x y))
      "2013-01-01T00:00:00" "2013-01-01T00:00:00" true

      "2013-01-01T00:00" "2013-01-01T00:00:00" true

      "2013-01-01T00" "2013-01-01T00:00:00" true)

    (tu/testing-binary-null elm/equal #elm/date-time"2013-01-01"))

  (testing "Time"
    (are [x y res] (= res (tu/compile-binop elm/equal elm/time x y))
      "12:30:15" "12:30:15" true
      "12:30:15" "12:30:16" false
      "12:30:16" "12:30:15" false

      "12:30.00" "12:30" nil

      "12:00" "12" nil)

    (tu/testing-binary-null elm/equal #elm/time"12:30:15"))

  (testing "Code"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/equal [x y])) {} nil nil))
      (tu/code "a" "0") (tu/code "a" "0") true
      (tu/code "a" "0") (tu/code "a" "1") false
      (tu/code "a" "0") (tu/code "b" "0") false

      (tu/code "a" "0") (tu/code "a" "2010" "0") false
      (tu/code "a" "2010" "0") (tu/code "a" "0") false

      (tu/code "a" "2010" "0") (tu/code "a" "2020" "0") false
      (tu/code "a" "2020" "0") (tu/code "a" "2010" "0") false)

    (tu/testing-binary-null elm/equal (tu/code "a" "0"))))


;; 12.2. Equivalent
;;
;; The Equivalent operator returns true if the arguments are the same value, or
;; if they are both null; and false otherwise.
;;
;; With the exception of null behavior and the semantics for specific types
;; defined below, equivalence is the same as equality.
;;
;; For string values, equivalence returns true if the strings are the same value
;; while ignoring case and locale, and normalizing whitespace. Normalizing
;; whitespace means that all whitespace characters are treated as equivalent,
;; with whitespace characters as defined in the whitespace lexical category.
;;
;; For decimals, equivalent means the values are the same with the comparison
;; done on values rounded to the precision of the least precise operand;
;; trailing zeroes after the decimal are ignored in determining precision for
;; equivalent comparison.
;;
;; For quantities, equivalent means the values are the same quantity when
;; considering unit conversion (e.g. 100 'cm' ~ 1 'm') and using decimal
;; equivalent semantics for the value. Note that implementations are not
;; required to support unit conversion and so are allowed to return null for
;; equivalence of quantities with different units.
;;
;; For ratios, equivalent means that the numerator and denominator represent the
;; same ratio (e.g. 1:100 ~ 10:1000).
;;
;; For tuple types, this means that two tuple values are equivalent if and only
;; if the tuples are of the same type, and the values for all elements by name
;; are equivalent.
;;
;; For list types, this means that two lists are equivalent if and only if the
;; lists contain elements of the same type, have the same number of elements,
;; and for each element in the lists, in order, the elements are equivalent.
;;
;; For interval types, this means that two intervals are equivalent if and only
;; if the intervals are over the same point type, and the starting and ending
;; points of the intervals as determined by the Start and End operators are
;; equivalent.
;;
;; For Date, DateTime, and Time values, the comparison is performed in the same
;; way as it is for equality, except that if one input has a value for a given
;; precision and the other does not, the comparison stops and the result is
;; false, rather than null. As with equality, the second and millisecond
;; precisions are combined and combined as a single precision using a decimal,
;; with decimal equivalence semantics.
;;
;; For Code values, equivalence is defined based on the code and system elements
;; only. The display and version elements are ignored for the purposes of
;; determining Code equivalence.
;;
;; For Concept values, equivalence is defined as a non-empty intersection of the
;; codes in each Concept.
;;
;; Note that this operator will always return true or false, even if either or
;; both of its arguments are null or contain null components.
(deftest compile-equivalent-test
  (testing "Both null"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/equivalent [x y])) {} nil nil))
      {:type "Null"} {:type "Null"} true))

  (testing "Boolean"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/equivalent [x y])) {} nil nil))
      #elm/boolean"true" #elm/boolean"true" true
      #elm/boolean"true" #elm/boolean"false" false

      {:type "Null"} #elm/boolean"true" false
      #elm/boolean"true" {:type "Null"} false))

  (testing "Integer"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/equivalent [x y])) {} nil nil))
      #elm/integer"1" #elm/integer"1" true
      #elm/integer"1" #elm/integer"2" false

      {:type "Null"} #elm/integer"1" false
      #elm/integer"1" {:type "Null"} false))

  (testing "Decimal"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/equivalent [x y])) {} nil nil))
      #elm/decimal"1.1" #elm/decimal"1.1" true
      #elm/decimal"1.1" #elm/decimal"2.1" false

      {:type "Null"} #elm/decimal"1.1" false
      #elm/decimal"1.1" {:type "Null"} false))

  (testing "Mixed Integer Decimal"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/equivalent [x y])) {} nil nil))
      #elm/integer"1" #elm/decimal"1" true
      #elm/decimal"1" #elm/integer"1" true))

  (testing "Quantity"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/equivalent [x y])) {} nil nil))
      #elm/quantity[1] #elm/quantity[1] true
      #elm/quantity[1] #elm/quantity[2] false

      #elm/quantity[1 "s"] #elm/quantity[1 "s"] true
      #elm/quantity[1 "m"] #elm/quantity[1 "m"] true
      #elm/quantity[100 "cm"] #elm/quantity[1 "m"] true
      #elm/quantity[1 "s"] #elm/quantity[2 "s"] false
      #elm/quantity[1 "s"] #elm/quantity[1 "m"] false

      {:type "Null"} #elm/quantity[1] false
      #elm/quantity[1] {:type "Null"} false

      {:type "Null"} #elm/quantity[1 "s"] false
      #elm/quantity[1 "s"] {:type "Null"} false))

  (testing "List"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/equivalent [x y])) {} nil nil))
      #elm/list [#elm/integer"1"] #elm/list [#elm/integer"1"] true
      #elm/list [] #elm/list [] true

      #elm/list [#elm/integer"1"] #elm/list [] false
      #elm/list [#elm/integer"1"] #elm/list [#elm/integer"2"] false
      #elm/list [#elm/integer"1" #elm/integer"1"]
      #elm/list [#elm/integer"1" #elm/integer"2"] false

      #elm/list [#elm/integer"1" {:type "Null"}]
      #elm/list [#elm/integer"1" {:type "Null"}] true
      #elm/list [{:type "Null"}] #elm/list [{:type "Null"}] true
      #elm/list [#elm/date"2019"] #elm/list [#elm/date"2019-01"] false

      {:type "Null"} #elm/list [] false
      #elm/list [] {:type "Null"} false))

  (testing "Code"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/equivalent [x y])) {} nil nil))
      (tu/code "a" "0") (tu/code "a" "0") true
      (tu/code "a" "0") (tu/code "a" "1") false
      (tu/code "a" "0") (tu/code "b" "0") false

      (tu/code "a" "0") (tu/code "a" "2010" "0") true
      (tu/code "a" "2010" "0") (tu/code "a" "0") true

      (tu/code "a" "2010" "0") (tu/code "a" "2020" "0") true
      (tu/code "a" "2020" "0") (tu/code "a" "2010" "0") true

      {:type "Null"} (tu/code "a" "0") false
      (tu/code "a" "0") {:type "Null"} false)))


;; 12.3. Greater
;;
;; The Greater operator returns true if the first argument is greater than the
;; second argument.
;;
;; For comparisons involving quantities, the dimensions of each quantity must be
;; the same, but not necessarily the unit. For example, units of 'cm' and 'm'
;; are comparable, but units of 'cm2' and 'cm' are not. Attempting to operate on
;; quantities with invalid units will result in a run-time error.
;;
;; For Date, DateTime, and Time values, the comparison is performed by
;; considering each precision in order, beginning with years (or hours for time
;; values). If the values are the same, comparison proceeds to the next
;; precision; if the first value is greater than the second, the result is true;
;; if the first value is less than the second, the result is false; if one input
;; has a value for the precision and the other does not, the comparison stops
;; and the result is null; if neither input has a value for the precision or the
;; last precision has been reached, the comparison stops and the result is
;; false. For the purposes of comparison, seconds and milliseconds are combined
;; as a single precision using a decimal, with decimal comparison semantics.
;;
;; If either argument is null, the result is null.
;;
;; The Greater operator is defined for the Integer, Decimal, String, Date,
;; DateTime, Time, and Quantity types.
(deftest compile-greater-test
  (testing "Integer"
    (are [x y res] (= res (tu/compile-binop elm/greater elm/integer x y))
      "2" "1" true
      "1" "1" false)

    (tu/testing-binary-null elm/greater #elm/integer"1"))

  (testing "Decimal"
    (are [x y res] (= res (tu/compile-binop elm/greater elm/decimal x y))
      "2.1" "1.1" true
      "1.1" "1.1" false)

    (tu/testing-binary-null elm/greater #elm/decimal"1.1"))

  (testing "String"
    (are [x y res] (= res (tu/compile-binop elm/greater elm/string x y))
      "b" "a" true
      "a" "a" false)

    (tu/testing-binary-null elm/greater #elm/string "a"))

  (testing "Quantity"
    (are [x y res] (= res (tu/compile-binop elm/greater elm/quantity x y))
      [2] [1] true
      [1] [1] false

      [2 "s"] [1 "s"] true
      [2 "m"] [1 "m"] true
      [101 "cm"] [1 "m"] true
      [1 "s"] [1 "s"] false
      [1 "m"] [1 "m"] false
      [100 "cm"] [1 "m"] false)

    (tu/testing-binary-null elm/greater #elm/quantity[1]))

  (testing "Date with year precision"
    (are [x y res] (= res (tu/compile-binop elm/greater elm/date x y))
      "2014" "2013" true
      "2013" "2013" false)

    (tu/testing-binary-null elm/greater #elm/date"2013"))

  (testing "DateTime with year precision"
    (are [x y res] (= res (tu/compile-binop elm/greater elm/date-time x y))
      "2014" "2013" true
      "2013" "2013" false)

    (tu/testing-binary-null elm/greater #elm/date-time"2013"))

  (testing "DateTime with year-month precision"
    (are [x y res] (= res (tu/compile-binop elm/greater elm/date-time x y))
      "2013-07" "2013-06" true
      "2013-06" "2013-06" false)

    (tu/testing-binary-null elm/greater #elm/date-time"2013-06"))

  (testing "DateTime with date precision"
    (are [x y res] (= res (tu/compile-binop elm/greater elm/date-time x y))
      "2013-06-16" "2013-06-15" true
      "2013-06-15" "2013-06-15" false)

    (tu/testing-binary-null elm/greater #elm/date-time"2013-06-15"))

  (testing "Comparing dates with mixed precisions (year and year-month) results in null."
    (are [x y res] (= res (tu/compile-binop elm/greater elm/date x y))
      "2013" "2013-01" nil
      "2013-01" "2013" nil))

  (testing "Time"
    (are [x y res] (= res (tu/compile-binop elm/greater elm/time x y))
      "00:00:01" "00:00:00" true
      "00:00:00" "00:00:00" false)

    (tu/testing-binary-null elm/greater #elm/time"00:00:00")))


;; 12.4. GreaterOrEqual
;;
;; The GreaterOrEqual operator returns true if the first argument is greater
;; than or equal to the second argument.
;;
;; For comparisons involving quantities, the dimensions of each quantity must be
;; the same, but not necessarily the unit. For example, units of 'cm' and 'm'
;; are comparable, but units of 'cm2' and 'cm' are not. Attempting to operate on
;; quantities with invalid units will result in a run-time error.
;;
;; For Date, DateTime, and Time values, the comparison is performed by
;; considering each precision in order, beginning with years (or hours for time
;; values). If the values are the same, comparison proceeds to the next
;; precision; if the first value is greater than the second, the result is true;
;; if the first value is less than the second, the result is false; if one input
;; has a value for the precision and the other does not, the comparison stops
;; and the result is null; if neither input has a value for the precision or the
;; last precision has been reached, the comparison stops and the result is true.
;; For the purposes of comparison, seconds and milliseconds are combined as a
;; single precision using a decimal, with decimal comparison semantics.
;;
;; If either argument is null, the result is null.
;;
;; The GreaterOrEqual operator is defined for the Integer, Decimal, String,
;; Date, DateTime, Time, and Quantity types.
(deftest compile-greater-or-equal-test
  (testing "Integer"
    (are [x y res] (= res (tu/compile-binop elm/greater-or-equal elm/integer x y))
      "1" "1" true
      "2" "1" true
      "1" "2" false)

    (tu/testing-binary-null elm/greater-or-equal #elm/integer"1"))

  (testing "Decimal"
    (are [x y res] (= res (tu/compile-binop elm/greater-or-equal elm/decimal x y))
      "1.1" "1.1" true
      "2.1" "1.1" true
      "1.1" "2.1" false)

    (tu/testing-binary-null elm/greater-or-equal #elm/decimal"1.1"))

  (testing "String"
    (are [x y res] (= res (tu/compile-binop elm/greater-or-equal elm/string x y))
      "a" "a" true
      "b" "a" true
      "a" "b" false)

    (tu/testing-binary-null elm/greater-or-equal #elm/string "a"))

  (testing "Date with full precision"
    (are [x y res] (= res (tu/compile-binop elm/greater-or-equal elm/date x y))
      "2013-06-16" "2013-06-15" true
      "2013-06-15" "2013-06-15" true
      "2013-06-14" "2013-06-15" false)

    (tu/testing-binary-null elm/greater-or-equal #elm/date"2013-06-15"))

  (testing "DateTime with year precision"
    (are [x y res] (= res (tu/compile-binop elm/greater-or-equal elm/date-time x y))
      "2014" "2013" true
      "2013" "2013" true
      "2012" "2013" false)

    (tu/testing-binary-null elm/greater-or-equal #elm/date"2013"))

  (testing "DateTime with year-month precision"
    (are [x y res] (= res (tu/compile-binop elm/greater-or-equal elm/date-time x y))
      "2013-07" "2013-06" true
      "2013-06" "2013-06" true
      "2013-05" "2013-06" false)

    (tu/testing-binary-null elm/greater-or-equal #elm/date"2013-06"))

  (testing "DateTime with date precision"
    (are [x y res] (= res (tu/compile-binop elm/greater-or-equal elm/date-time x y))
      "2013-06-16" "2013-06-15" true
      "2013-06-15" "2013-06-15" true
      "2013-06-14" "2013-06-15" false)

    (tu/testing-binary-null elm/greater-or-equal #elm/date"2013-06-15"))

  (testing "Time"
    (are [x y res] (= res (tu/compile-binop elm/greater-or-equal elm/time x y))
      "00:00:00" "00:00:00" true
      "00:00:01" "00:00:00" true
      "00:00:00" "00:00:01" false)

    (tu/testing-binary-null elm/greater-or-equal #elm/time"00:00:00"))

  (testing "Quantity"
    (are [x y res] (= res (tu/compile-binop elm/greater-or-equal elm/quantity x y))
      [1] [1] true
      [2] [1] true
      [1] [2] false

      [1 "s"] [1 "s"] true
      [2 "s"] [1 "s"] true
      [1 "s"] [2 "s"] false

      [101 "cm"] [1 "m"] true
      [100 "cm"] [1 "m"] true
      [1 "m"] [101 "cm"] false)

    (tu/testing-binary-null elm/greater-or-equal #elm/quantity[1])))


;; 12.5. Less
;;
;; The Less operator returns true if the first argument is less than the second
;; argument.
;;
;; For comparisons involving quantities, the dimensions of each quantity must be
;; the same, but not necessarily the unit. For example, units of 'cm' and 'm'
;; are comparable, but units of 'cm2' and 'cm' are not. Attempting to operate on
;; quantities with invalid units will result in a run-time error.
;;
;; For date/time values, the comparison is performed by considering each
;; precision in order, beginning with years (or hours for time values). If the
;; values are the same, comparison proceeds to the next precision; if the first
;; value is less than the second, the result is true; if the first value is
;; greater than the second, the result is false; if one input has a value for
;; the precision and the other does not, the comparison stops and the result is
;; null; if neither input has a value for the precision or the last precision
;; has been reached, the comparison stops and the result is false.
;;
;; If either argument is null, the result is null.
;;
;; The Less operator is defined for the Integer, Decimal, String, Date,
;; DateTime, Time, and Quantity types.
(deftest compile-less-test
  (testing "Integer"
    (are [x y res] (= res (tu/compile-binop elm/less elm/integer x y))
      "1" "2" true
      "1" "1" false)

    (tu/testing-binary-null elm/less #elm/integer"1"))

  (testing "Decimal"
    (are [x y res] (= res (tu/compile-binop elm/less elm/decimal x y))
      "1.1" "2.1" true
      "1.1" "1.1" false)

    (tu/testing-binary-null elm/less #elm/decimal"1.1"))

  (testing "String"
    (are [x y res] (= res (tu/compile-binop elm/less elm/string x y))
      "a" "b" true
      "a" "a" false)

    (tu/testing-binary-null elm/less #elm/string "a"))

  (testing "Date with year precision"
    (are [x y res] (= res (tu/compile-binop elm/less elm/date x y))
      "2012" "2013" true
      "2013" "2013" false)

    (tu/testing-binary-null elm/less #elm/date"2013"))

  (testing "Comparing dates with mixed precisions (year and year-month) results in null."
    (are [x y res] (= res (tu/compile-binop elm/less elm/date x y))
      "2013" "2013-01" nil
      "2013-01" "2013" nil))

  (testing "Date with full precision"
    (are [x y res] (= res (tu/compile-binop elm/less elm/date x y))
      "2013-06-14" "2013-06-15" true
      "2013-06-15" "2013-06-15" false)

    (tu/testing-binary-null elm/less #elm/date"2013-06-15"))

  (testing "Comparing dates with mixed precisions (year-month and full) results in null."
    (are [x y res] (= res (tu/compile-binop elm/less elm/date x y))
      "2013-01" "2013-01-01" nil
      "2013-01-01" "2013-01" nil))

  (testing "DateTime with year precision"
    (are [x y res] (= res (tu/compile-binop elm/less elm/date-time x y))
      "2012" "2013" true
      "2013" "2013" false)

    (tu/testing-binary-null elm/less #elm/date-time"2013"))

  (testing "DateTime with year-month precision"
    (are [x y res] (= res (tu/compile-binop elm/less elm/date-time x y))
      "2013-05" "2013-06" true
      "2013-06" "2013-06" false)

    (tu/testing-binary-null elm/less #elm/date-time"2013-06"))

  (testing "DateTime with date precision"
    (are [x y res] (= res (tu/compile-binop elm/less elm/date-time x y))
      "2013-06-14" "2013-06-15" true
      "2013-06-15" "2013-06-15" false)

    (tu/testing-binary-null elm/less #elm/date-time"2013-06-15"))

  (testing "DateTime with full precision (there is only one precision)"
    (are [x y res] (= res (tu/compile-binop elm/less elm/date-time x y))
      "2013-06-15T11" "2013-06-15T12" true
      "2013-06-15T12" "2013-06-15T12" false)

    (tu/testing-binary-null elm/less #elm/date-time"2013-06-15T12"))

  (testing "Time with full precision (there is only one precision)"
    (are [x y res] (= res (tu/compile-binop elm/less elm/time x y))
      "12:30:14" "12:30:15" true
      "12:30:15" "12:30:15" false)

    (tu/testing-binary-null elm/less #elm/time"12:30:15"))

  (testing "Quantity"
    (are [x y res] (= res (tu/compile-binop elm/less elm/quantity x y))
      [1] [2] true
      [1] [1] false

      [1 "s"] [2 "s"] true
      [1 "s"] [1 "s"] false

      [1 "m"] [101 "cm"] true
      [1 "m"] [100 "cm"] false)

    (tu/testing-binary-null elm/less #elm/quantity[1])))


;; 12.6. LessOrEqual
;;
;; The LessOrEqual operator returns true if the first argument is less than or
;; equal to the second argument.
;;
;; For comparisons involving quantities, the dimensions of each quantity must be
;; the same, but not necessarily the unit. For example, units of 'cm' and 'm'
;; are comparable, but units of 'cm2' and 'cm' are not. Attempting to operate on
;; quantities with invalid units will result in a run-time error.
;;
;; For Date, DateTime, and Time values, the comparison is performed by
;; considering each precision in order, beginning with years (or hours for time
;; values). If the values are the same, comparison proceeds to the next
;; precision; if the first value is less than the second, the result is true; if
;; the first value is greater than the second, the result is false; if one input
;; has a value for the precision and the other does not, the comparison stops
;; and the result is null; if neither input has a value for the precision or the
;; last precision has been reached, the comparison stops and the result is true.
;; For the purposes of comparison, seconds and milliseconds are combined as a
;; single precision using a decimal, with decimal comparison semantics.
;;
;; If either argument is null, the result is null.
;;
;; The LessOrEqual operator is defined for the Integer, Decimal, String, Date,
;; DateTime, Time, and Quantity types.
(deftest compile-less-or-equal-test
  (testing "Integer"
    (are [x y res] (= res (tu/compile-binop elm/less-or-equal elm/integer x y))
      "1" "1" true
      "1" "2" true
      "2" "1" false)

    (tu/testing-binary-null elm/less-or-equal #elm/integer"1"))

  (testing "Decimal"
    (are [x y res] (= res (tu/compile-binop elm/less-or-equal elm/decimal x y))
      "1.1" "1.1" true
      "1.1" "2.1" true
      "2.1" "1.1" false)

    (tu/testing-binary-null elm/less-or-equal #elm/decimal"1.1"))

  (testing "Date with full precision"
    (are [x y res] (= res (tu/compile-binop elm/less-or-equal elm/date x y))
      "2013-06-14" "2013-06-15" true
      "2013-06-15" "2013-06-15" true
      "2013-06-16" "2013-06-15" false)

    (tu/testing-binary-null elm/less-or-equal #elm/date"2013-06-15"))

  (testing "Mixed Date and DateTime"
    (are [x y res] (= res (c/compile {} (elm/less-or-equal [x y])))
      #elm/date"2013-06-15" #elm/date-time"2013-06-15T00" nil
      #elm/date-time"2013-06-15T00" #elm/date"2013-06-15" nil))

  (testing "DateTime with year precision"
    (are [x y res] (= res (tu/compile-binop elm/less-or-equal elm/date-time x y))
      "2012" "2013" true
      "2013" "2013" true
      "2014" "2013" false)

    (tu/testing-binary-null elm/less-or-equal #elm/date"2013"))

  (testing "DateTime with year-month precision"
    (are [x y res] (= res (tu/compile-binop elm/less-or-equal elm/date-time x y))
      "2013-05" "2013-06" true
      "2013-06" "2013-06" true
      "2013-07" "2013-06" false)

    (tu/testing-binary-null elm/less-or-equal #elm/date"2013-06"))

  (testing "DateTime with date precision"
    (are [x y res] (= res (tu/compile-binop elm/less-or-equal elm/date-time x y))
      "2013-06-14" "2013-06-15" true
      "2013-06-15" "2013-06-15" true
      "2013-06-16" "2013-06-15" false)

    (tu/testing-binary-null elm/less-or-equal #elm/date"2013-06-15"))

  (testing "Time"
    (are [x y res] (= res (tu/compile-binop elm/less-or-equal elm/time x y))
      "00:00:00" "00:00:00" true
      "00:00:00" "00:00:01" true
      "00:00:01" "00:00:00" false)

    (tu/testing-binary-null elm/less-or-equal #elm/time"00:00:00"))

  (testing "Quantity"
    (are [x y res] (= res (tu/compile-binop elm/less-or-equal elm/quantity x y))
      [1] [2] true
      [1] [1] true
      [2] [1] false

      [1 "s"] [2 "s"] true
      [1 "s"] [1 "s"] true
      [2 "s"] [1 "s"] false

      [1 "m"] [101 "cm"] true
      [1 "m"] [100 "cm"] true
      [101 "cm"] [1 "m"] false)

    (tu/testing-binary-null elm/less-or-equal #elm/quantity[1])))


;; 12.7. NotEqual
;;
;; Normalized to Not Equal
(deftest compile-not-equal-test
  (tu/unsupported-binary-operand "NotEqual"))



;; 13. Logical Operators

;; 13.1. And
;;
;; The And operator returns the logical conjunction of its arguments. Note that
;; this operator is defined using 3-valued logic semantics. This means that if
;; either argument is false, the result is false; if both arguments are true,
;; the result is true; otherwise, the result is null. Note also that ELM does
;; not prescribe short-circuit evaluation.
(deftest compile-and-test
  (testing "Static"
    (are [x y res] (= res (c/compile {} (elm/and [x y])))
      #elm/boolean"true" #elm/boolean"true" true
      #elm/boolean"true" #elm/boolean"false" false
      #elm/boolean"true" {:type "Null"} nil

      #elm/boolean"false" #elm/boolean"true" false
      #elm/boolean"false" #elm/boolean"false" false
      #elm/boolean"false" {:type "Null"} false

      {:type "Null"} #elm/boolean"true" nil
      {:type "Null"} #elm/boolean"false" false
      {:type "Null"} {:type "Null"} nil))

  (testing "Dynamic"
    (are [x y res] (= res (tu/dynamic-compile-eval (elm/and [x y])))
      #elm/boolean"true" #elm/parameter-ref"true" true
      #elm/parameter-ref"true" #elm/boolean"true" true
      #elm/parameter-ref"true" #elm/parameter-ref"true" true
      #elm/parameter-ref"true" {:type "Null"} nil
      {:type "Null"} #elm/parameter-ref"true" nil

      #elm/boolean"true" #elm/parameter-ref"false" false
      #elm/parameter-ref"false" #elm/boolean"true" false
      #elm/parameter-ref"false" #elm/parameter-ref"false" false
      #elm/parameter-ref"false" {:type "Null"} false
      {:type "Null"} #elm/parameter-ref"false" false

      #elm/boolean"false" #elm/parameter-ref"nil" false
      #elm/parameter-ref"nil" #elm/boolean"false" false
      #elm/boolean"true" #elm/parameter-ref"nil" nil
      #elm/parameter-ref"nil" #elm/boolean"true" nil
      #elm/parameter-ref"nil" #elm/parameter-ref"nil" nil)))


;; 13.2. Implies
;;
;; The Implies operator returns the logical implication of its arguments. Note
;; that this operator is defined using 3-valued logic semantics. This means that
;; if the left operand evaluates to true, this operator returns the boolean
;; evaluation of the right operand. If the left operand evaluates to false, this
;; operator returns true. Otherwise, this operator returns true if the right
;; operand evaluates to true, and null otherwise.
;;
;; Note that implies may use short-circuit evaluation in the case that the first
;; operand evaluates to false.
(deftest compile-implies-test
  (are [x y res] (= res (core/-eval (c/compile {} {:type "Or" :operand [{:type "Not" :operand x} y]}) {} nil nil))
    #elm/boolean"true" #elm/boolean"true" true
    #elm/boolean"true" #elm/boolean"false" false
    #elm/boolean"true" {:type "Null"} nil

    #elm/boolean"false" #elm/boolean"true" true
    #elm/boolean"false" #elm/boolean"false" true
    #elm/boolean"false" {:type "Null"} true

    {:type "Null"} #elm/boolean"true" true
    {:type "Null"} #elm/boolean"false" nil
    {:type "Null"} {:type "Null"} nil))


;; 13.3. Not
;;
;; The Not operator returns the logical negation of its argument. If the
;; argument is true, the result is false; if the argument is false, the result
;; is true; otherwise, the result is null.
(deftest compile-not-test
  (testing "Static"
    (are [x res] (= res (c/compile {} (elm/not x)))
      #elm/boolean"true" false
      #elm/boolean"false" true
      {:type "Null"} nil))

  (testing "Dynamic"
    (are [x res] (= res (tu/dynamic-compile-eval (elm/not x)))
      #elm/parameter-ref"true" false
      #elm/parameter-ref"false" true
      #elm/parameter-ref"nil" nil)))


;; 13.4. Or
;;
;; The Or operator returns the logical disjunction of its arguments. Note that
;; this operator is defined using 3-valued logic semantics. This means that if
;; either argument is true, the result is true; if both arguments are false, the
;; result is false; otherwise, the result is null. Note also that ELM does not
;; prescribe short-circuit evaluation.
(deftest compile-or-test
  (testing "Static"
    (are [x y res] (= res (c/compile {} (elm/or [x y])))
      #elm/boolean"true" #elm/boolean"true" true
      #elm/boolean"true" #elm/boolean"false" true
      #elm/boolean"true" {:type "Null"} true

      #elm/boolean"false" #elm/boolean"true" true
      #elm/boolean"false" #elm/boolean"false" false
      #elm/boolean"false" {:type "Null"} nil

      {:type "Null"} #elm/boolean"true" true
      {:type "Null"} #elm/boolean"false" nil
      {:type "Null"} {:type "Null"} nil))

  (testing "Dynamic"
    (are [x y res] (= res (tu/dynamic-compile-eval (elm/or [x y])))
      #elm/boolean"false" #elm/parameter-ref"true" true
      #elm/parameter-ref"true" #elm/boolean"false" true
      #elm/parameter-ref"true" #elm/parameter-ref"true" true
      #elm/parameter-ref"true" {:type "Null"} true
      {:type "Null"} #elm/parameter-ref"true" true

      #elm/boolean"false" #elm/parameter-ref"false" false
      #elm/parameter-ref"false" #elm/boolean"false" false
      #elm/parameter-ref"false" #elm/parameter-ref"false" false
      #elm/parameter-ref"false" {:type "Null"} nil
      {:type "Null"} #elm/parameter-ref"false" nil

      #elm/boolean"true" #elm/parameter-ref"nil" true
      #elm/parameter-ref"nil" #elm/boolean"true" true
      #elm/boolean"false" #elm/parameter-ref"nil" nil
      #elm/parameter-ref"nil" #elm/boolean"false" nil
      #elm/parameter-ref"nil" #elm/parameter-ref"nil" nil)))


;; 13.5. Xor
;;
;; The Xor operator returns the exclusive or of its arguments. Note that this
;; operator is defined using 3-valued logic semantics. This means that the
;; result is true if and only if one argument is true and the other is false,
;; and that the result is false if and only if both arguments are true or both
;; arguments are false. If either or both arguments are null, the result is
;; null.
(deftest compile-xor-test
  (testing "Static"
    (are [x y res] (= res (c/compile {} (elm/xor [x y])))
      #elm/boolean"true" #elm/boolean"true" false
      #elm/boolean"true" #elm/boolean"false" true
      #elm/boolean"true" {:type "Null"} nil

      #elm/boolean"false" #elm/boolean"true" true
      #elm/boolean"false" #elm/boolean"false" false
      #elm/boolean"false" {:type "Null"} nil

      {:type "Null"} #elm/boolean"true" nil
      {:type "Null"} #elm/boolean"false" nil
      {:type "Null"} {:type "Null"} nil))

  (testing "Dynamic"
    (are [x y res] (= res (tu/dynamic-compile-eval (elm/xor [x y])))
      #elm/boolean"true" #elm/parameter-ref"true" false
      #elm/parameter-ref"true" #elm/boolean"true" false
      #elm/boolean"false" #elm/parameter-ref"true" true
      #elm/parameter-ref"true" #elm/boolean"false" true
      #elm/parameter-ref"true" #elm/parameter-ref"true" false

      #elm/boolean"true" #elm/parameter-ref"false" true
      #elm/parameter-ref"false" #elm/boolean"true" true
      #elm/boolean"false" #elm/parameter-ref"false" false
      #elm/parameter-ref"false" #elm/boolean"false" false
      #elm/parameter-ref"false" #elm/parameter-ref"false" false

      #elm/boolean"true" #elm/parameter-ref"nil" nil
      #elm/parameter-ref"nil" #elm/boolean"true" nil
      #elm/boolean"false" #elm/parameter-ref"nil" nil
      #elm/parameter-ref"nil" #elm/boolean"false" nil
      {:type "Null"} #elm/parameter-ref"nil" nil
      #elm/parameter-ref"nil" {:type "Null"} nil
      #elm/parameter-ref"nil" #elm/parameter-ref"nil" nil)))
