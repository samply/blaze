(ns blaze.elm.compiler.type-operators-test
  (:require
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.clinical-operators]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.compiler.type-operators]
    [blaze.elm.decimal :as decimal]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [blaze.elm.protocols :as p]
    [blaze.elm.quantity :as quantity]
    [blaze.fhir.spec.type.system :as system]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]))


(st/instrument)
(tu/instrument-compile)


(defn fixture [f]
  (st/instrument)
  (tu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; 22.1. As
;;
;; The As operator allows the result of an expression to be cast as a given
;; target type. This allows expressions to be written that are statically typed
;; against the expected run-time type of the argument. If the argument is not of
;; the specified type and the strict attribute is false (the default) the
;; result is null. If the argument is not of the specified type and the strict
;; attribute is true an exception is thrown.
(deftest compile-as-test
  (testing "FHIR types"
    (are [elm resource res] (= res (core/-eval (c/compile {} elm) {} nil {"R" resource}))
      #elm/as["{http://hl7.org/fhir}boolean"
              {:path "deceased"
               :scope "R"
               :type "Property"}]
      {:fhir/type :fhir/Patient :id "0" :deceased true}
      true

      #elm/as ["{http://hl7.org/fhir}integer"
               {:path "value"
                :scope "R"
                :type "Property"}]
      {:fhir/type :fhir/Observation :value (int 1)}
      (int 1)

      #elm/as ["{http://hl7.org/fhir}string"
               {:path "name"
                :scope "R"
                :type "Property"}]
      {:fhir/type :fhir/Account :name "a"}
      "a"

      #elm/as ["{http://hl7.org/fhir}decimal"
               {:path "duration"
                :scope "R"
                :type "Property"}]
      {:fhir/type :fhir/Media :duration 1.1M}
      1.1M

      #elm/as ["{http://hl7.org/fhir}uri"
               {:path "url"
                :scope "R"
                :type "Property"}]
      {:fhir/type :fhir/Measure :url #fhir/uri"a"}
      #fhir/uri"a"

      #elm/as ["{http://hl7.org/fhir}url"
               {:path "address"
                :scope "R"
                :type "Property"}]
      {:fhir/type :fhir/Endpoint :address #fhir/url"a"}
      #fhir/url"a"

      #elm/as ["{http://hl7.org/fhir}dateTime"
               {:path "value"
                :scope "R"
                :type "Property"}]
      {:fhir/type :fhir/Observation :value #fhir/dateTime"2019-09-04"}
      #fhir/dateTime"2019-09-04"

      #elm/as ["{http://hl7.org/fhir}Quantity"
               {:path "value"
                :scope "R"
                :type "Property"}]
      {:fhir/type :fhir/Observation :value #fhir/dateTime"2019-09-04"}
      nil))

  (testing "ELM types"
    (are [elm res] (= res (core/-eval (c/compile {} elm) {} nil nil))
      #elm/as ["{urn:hl7-org:elm-types:r1}Boolean" #elm/boolean"true"]
      true

      #elm/as ["{urn:hl7-org:elm-types:r1}Integer" #elm/integer"1"]
      1

      #elm/as ["{urn:hl7-org:elm-types:r1}Integer" {:type "Null"}]
      nil

      #elm/as ["{urn:hl7-org:elm-types:r1}DateTime" #elm/date-time"2019-09-04"]
      (system/date-time 2019 9 4))))


;; TODO 22.2. CanConvert


;; 22.3. CanConvertQuantity
;;
;; The CanConvertQuantity operator returns true if the Quantity can be converted
;; to an equivalent Quantity with the given Unit. Otherwise, the result is false.
;;
;; Note that implementations are not required to support quantity conversion,
;; and so may return false, even if the conversion is valid. Implementations
;; that do support unit conversion shall do so according to the conversion
;; specified by UCUM.
;;
;; If either argument is null, the result is null.
(deftest compile-can-convert-quantity-test
  (are [argument unit] (true? (core/-eval (c/compile {} #elm/can-convert-quantity [argument unit]) {} nil nil))
    #elm/quantity[5 "mg"] #elm/string "g")

  (are [argument unit] (false? (core/-eval (c/compile {} #elm/can-convert-quantity [argument unit]) {} nil nil))
    #elm/quantity[5 "mg"] #elm/string "m")

  (tu/testing-binary-null elm/can-convert-quantity #elm/quantity[1 "m"] #elm/string "m"))


;; 22.4. Children
;;
;; For structured types, the Children operator returns a list of all the values
;; of the elements of the type. List-valued elements are expanded and added to
;; the result individually, rather than as a single list.
;;
;; For list types, the result is the same as invoking Children on each element
;; in the list and flattening the resulting lists into a single result.
;;
;; If the source is null, the result is null.
(deftest compile-children-test
  (testing "Code"
    (are [elm res] (= res (core/-eval (c/compile {} #elm/children elm)
                                      {:now tu/now} nil nil))
      (tu/code "system-134534" "code-134551")
      ["code-134551" nil "system-134534" nil]))

  ;; TODO: other types

  (tu/testing-unary-null elm/children))


;; TODO 22.5. Convert


;; 22.6. ConvertQuantity
;;
;; The ConvertQuantity operator converts a Quantity to an equivalent Quantity
;; with the given unit. If the unit of the input quantity can be converted to
;; the target unit, the result is an equivalent Quantity with the target unit.
;; Otherwise, the result is null.
;;
;; Note that implementations are not required to support quantity conversion.
;; Implementations that do support unit conversion shall do so according to the
;; conversion specified by UCUM. Implementations that do not support unit
;; conversion shall throw an error if an unsupported unit conversion is
;; requested with this operation.
;;
;; If either argument is null, the result is null.
(deftest compile-convert-quantity-test
  (are [argument unit res] (p/equal res (core/-eval (c/compile {} (elm/convert-quantity [argument unit])) {} nil nil))
    #elm/quantity[5 "mg"] #elm/string "g" (quantity/quantity 0.005 "g"))

  (are [argument unit] (nil? (core/-eval (c/compile {} (elm/convert-quantity [argument unit])) {} nil nil))
    #elm/quantity[5 "mg"] #elm/string "m")

  (tu/testing-binary-null elm/convert-quantity #elm/quantity[5 "mg"] #elm/string "m"))


;; TODO 22.7. ConvertsToBoolean


;; TODO 22.8. ConvertsToDate


;; TODO 22.9. ConvertsToDateTime


;; TODO 22.10. ConvertsToDecimal


;; TODO 22.11. ConvertsToInteger


;; TODO 22.12. ConvertsToQuantity


;; TODO 22.13. ConvertsToRatio


;; TODO 22.14. ConvertsToString


;; TODO 22.15. ConvertsToTime


;; 22.16. Descendents
;;
;; For structured types, the Descendents operator returns a list of all the
;; values of the elements of the type, recursively. List-valued elements are
;; expanded and added to the result individually, rather than as a single list.
;;
;; For list types, the result is the same as invoking Descendents on each
;; element in the list and flattening the resulting lists into a single result.
;;
;; If the source is null, the result is null.
(deftest compile-to-descendents-test
  (testing "Code"
    (are [x res] (= res (core/-eval (c/compile {} (elm/descendents x))
                                    {:now tu/now} nil nil))
      (tu/code "system-134534" "code-134551")
      ["code-134551" nil "system-134534" nil]))

  ;; TODO: other types

  (tu/testing-unary-null elm/descendents))


;; TODO 22.17. Is


;; TODO 22.18. ToBoolean


;; TODO 22.19. ToChars


;; TODO 22.20. ToConcept


;; 22.21. ToDate
;;
;; The ToDate operator converts the value of its argument to a Date value.
;;
;; For String values, The operator expects the string to be formatted using the
;; ISO-8601 date representation:
;;
;; YYYY-MM-DD
;;
;; In addition, the string must be interpretable as a valid date value.
;;
;; If the input string is not formatted correctly, or does not represent a valid
;; date value, the result is null.
;;
;; As with date literals, date values may be specified to any precision.
;;
;; For DateTime values, the result is equivalent to extracting the Date
;; component of the DateTime value.
;;
;; If the argument is null, the result is null.
(deftest compile-to-date-test
  (let [eval #(core/-eval % {:now tu/now} nil nil)]
    (testing "String values"
      (are [x res] (= res (eval (tu/compile-unop elm/to-date elm/string x)))
        "2019" (system/date 2019)
        "2019-01" (system/date 2019 1)
        "2019-01-01" (system/date 2019 1 1)

        "aaaa" nil
        "2019-13" nil
        "2019-02-29" nil))

    (testing "Date values"
      (are [x res] (= res (eval (tu/compile-unop elm/to-date elm/date x)))
        "2019" (system/date 2019)
        "2019-01" (system/date 2019 1)
        "2019-01-01" (system/date 2019 1 1)))

    (testing "DateTime values"
      (are [x res] (= res (eval (tu/compile-unop elm/to-date elm/date-time x)))
        "2019" (system/date 2019)
        "2019-01" (system/date 2019 1)
        "2019-01-01" (system/date 2019 1 1)
        "2019-01-01T12:13" (system/date 2019 1 1))))

  (tu/testing-unary-null elm/to-date))


;; 22.22. ToDateTime
;;
;; The ToDateTime operator converts the value of its argument to a DateTime
;; value.
;;
;; For String values, the operator expects the string to be formatted using the
;; ISO-8601 datetime representation:
;;
;; YYYY-MM-DDThh:mm:ss.fff(+|-)hh:mm footnote:formatting-strings[]
;;
;; In addition, the string must be interpretable as a valid DateTime value.
;;
;; If the input string is not formatted correctly, or does not represent a
;; valid DateTime value, the result is null.
;;
;; As with Date and Time literals, DateTime values may be specified to any
;; precision. If no timezone offset is supplied, the timezone offset of the
;; evaluation request timestamp is assumed.
;;
;; For Date values, the result is a DateTime with the time components
;; unspecified, except the timezone offset, which is set to the timezone offset
;; of the evaluation request timestamp.
;;
;; If the argument is null, the result is null.
(deftest compile-to-date-time-test
  (let [eval #(core/-eval % {:now tu/now} nil nil)]
    (testing "String values"
      (are [x res] (= res (eval (tu/compile-unop elm/to-date-time elm/string x)))
        "2020" (system/date-time 2020)
        "2020-03" (system/date-time 2020 3)
        "2020-03-08" (system/date-time 2020 3 8)
        "2020-03-08T12:54:00" (system/date-time 2020 3 8 12 54)
        "2020-03-08T12:54:00+00:00" (system/date-time 2020 3 8 12 54)

        "aaaa" nil
        "2019-13" nil
        "2019-02-29" nil))

    (testing "Date values"
      (are [x res] (= res (eval (tu/compile-unop elm/to-date-time elm/date x)))
        "2020" (system/date-time 2020)
        "2020-03" (system/date-time 2020 3)
        "2020-03-08" (system/date-time 2020 3 8)))

    (testing "DateTime values"
      (are [x res] (= res (eval (tu/compile-unop elm/to-date-time elm/date-time x)))
        "2020" (system/date-time 2020)
        "2020-03" (system/date-time 2020 3)
        "2020-03-08" (system/date-time 2020 3 8)
        "2020-03-08T12:13" (system/date-time 2020 3 8 12 13))))

  (tu/testing-unary-null elm/to-date-time))


;; 22.23. ToDecimal
;;
;; The ToDecimal operator converts the value of its argument to a Decimal value.
;; The operator accepts strings using the following format:
;;
;; (+|-)?#0(.0#)?
;;
;; Meaning an optional polarity indicator, followed by any number of digits
;; (including none), followed by at least one digit, followed optionally by a
;; decimal point, at least one digit, and any number of additional digits
;; (including none).
;;
;; Note that the decimal value returned by this operator must be limited in
;; precision and scale to the maximum precision and scale representable for
;; Decimal values within CQL.
;;
;; If the input string is not formatted correctly, or cannot be interpreted as
;; a valid Decimal value, the result is null.
;;
;; If the argument is null, the result is null.
(deftest compile-to-decimal-test
  (testing "String values"
    (are [x res] (= res (core/-eval (tu/compile-unop elm/to-decimal elm/string x)
                                    {} nil nil))
      (str decimal/min) decimal/min
      "-1.1" -1.1M
      "-1" -1M
      "0" 0M
      "1" 1M
      (str decimal/max) decimal/max

      (str (- decimal/min 1e-8M)) nil
      (str (+ decimal/max 1e-8M)) nil
      "a" nil))

  (tu/testing-unary-null elm/to-decimal))


;; 22.24. ToInteger
;;
;; The ToInteger operator converts the value of its argument to an Integer
;; value. The operator accepts strings using the following format:
;;
;; (+|-)?#0
;;
;; Meaning an optional polarity indicator, followed by any number of digits
;; (including none), followed by at least one digit.
;;
;; Note that the integer value returned by this operator must be a valid value
;; in the range representable for Integer values in CQL.
;;
;; If the input string is not formatted correctly, or cannot be interpreted as
;; a valid Integer value, the result is null.
;;
;; If the argument is null, the result is null.
(deftest compile-to-integer-test
  (testing "String values"
    (are [x res] (= res (core/-eval (tu/compile-unop elm/to-integer elm/string x)
                                    {} nil nil))
      (str Integer/MIN_VALUE) Integer/MIN_VALUE
      "-1" -1
      "0" 0
      "1" 1
      (str Integer/MAX_VALUE) Integer/MAX_VALUE

      (str (dec Integer/MIN_VALUE)) nil
      (str (inc Integer/MAX_VALUE)) nil
      "a" nil))

  (tu/testing-unary-null elm/to-integer))


;; 22.25. ToList
;;
;; The ToList operator returns its argument as a List value. The operator
;; accepts a singleton value of any type and returns a list with the value as
;; the single element.
;;
;; If the argument is null the operator returns an empty list.
;;
;; The operator is effectively shorthand for "if operand is null then { } else
;; { operand }".
;;
;; The operator is used to implement list promotion efficiently.
(deftest compile-to-list-test
  (testing "Boolean"
    (are [x res] (= res (core/-eval (tu/compile-unop elm/to-list elm/boolean x)
                                    {} nil nil))
      "false" [false]))

  (testing "Integer"
    (are [x res] (= res (core/-eval (tu/compile-unop elm/to-list elm/integer x)
                                    {} nil nil))
      "1" [1]))

  (testing "Null"
    (is (= [] (core/-eval (c/compile {} #elm/to-list{:type "Null"}) {} nil nil)))))


;; 22.26. ToQuantity
;;
;; The ToQuantity operator converts the value of its argument to a Quantity
;; value. The operator may be used with Integer, Decimal, Ratio, or String
;; values.
;;
;; For String values, the operator accepts strings using the following format:
;;
;; (+|-)?#0(.0#)?('<unit>')?
;;
;; Meaning an optional polarity indicator, followed by any number of digits
;; (including none) followed by at least one digit, optionally followed by a
;; decimal point, at least one digit, and any number of additional digits, all
;; optionally followed by a unit designator as a string literal specifying a
;; valid UCUM unit of measure. Spaces are allowed between the quantity value and
;; the unit designator.
;;
;; Note that the decimal value of the quantity returned by this operator must be
;; a valid value in the range representable for Decimal values in CQL.
;;
;; If the input string is not formatted correctly, or cannot be interpreted as a
;; valid Quantity value, the result is null.
;;
;; For Integer and Decimal values, the result is a Quantity with the value of
;; the integer or decimal input, and the default unit ('1').
;;
;; For Ratio values, the operation is equivalent to the result of dividing the
;; numerator of the ratio by the denominator.
;;
;; If the argument is null, the result is null.
(deftest compile-to-quantity-test
  (testing "String values"
    (are [x res] (p/equal res (core/-eval (tu/compile-unop elm/to-quantity
                                                           elm/string x)
                                          {} nil nil))
      "1" (quantity/quantity 1 "1")

      "1'm'" (quantity/quantity 1 "m")
      "1 'm'" (quantity/quantity 1 "m")
      "1  'm'" (quantity/quantity 1 "m")

      "10 'm'" (quantity/quantity 10 "m")

      "1.1 'm'" (quantity/quantity 1.1M "m"))

    (are [x] (nil? (core/-eval (tu/compile-unop elm/to-quantity elm/string x)
                               {} nil nil))
      ""
      "a"))

  (testing "Integer values"
    (are [x res] (= res (core/-eval (tu/compile-unop elm/to-quantity elm/integer x)
                                    {} nil nil))
      "1" (quantity/quantity 1 "1")))

  (testing "Decimal values"
    (are [x res] (p/equal res (core/-eval (tu/compile-unop elm/to-quantity
                                                           elm/decimal x)
                                          {} nil nil))
      "1" (quantity/quantity 1 "1")
      "1.1" (quantity/quantity 1.1M "1")))

  ;; TODO: Ratio

  (tu/testing-unary-null elm/to-quantity))


;; TODO 22.27. ToRatio


;; 22.28. ToString
;;
;; The ToString operator converts the value of its argument to a String value.
;; The operator uses the following string representations for each type:
;;
;; Boolean  true/false
;; Integer  (-)?#0
;; Decimal  (-)?#0.0#
;; Quantity (-)?#0.0# '<unit>'
;; Date     YYYY-MM-DD
;; DateTime YYYY-MM-DDThh:mm:ss.fff(+|-)hh:mm
;; Time     hh:mm:ss.fff
;; Ratio    <quantity>:<quantity>
;;
;; If the argument is null, the result is null.
(deftest compile-to-string-test
  (testing "Boolean values"
    (are [x res] (= res (core/-eval (tu/compile-unop elm/to-string elm/boolean x)
                                    {} nil nil))
      "true" "true"
      "false" "false"))

  (testing "Integer values"
    (are [x res] (= res (core/-eval (tu/compile-unop elm/to-string elm/integer x)
                                    {} nil nil))
      "-1" "-1"
      "0" "0"
      "1" "1"))

  (testing "Decimal values"
    (are [x res] (= res (core/-eval (tu/compile-unop elm/to-string elm/decimal x)
                                    {} nil nil))
      "-1" "-1"
      "0" "0"
      "1" "1"

      "-1.1" "-1.1"
      "0.0" "0.0"
      "1.1" "1.1"

      "0.0001" "0.0001"
      "0.00001" "0.00001"
      "0.000001" "0.000001"
      "0.0000001" "0.0000001"
      "0.00000001" "0.00000001"
      "0.000000001" "0.00000000"
      "0.000000005" "0.00000001"))

  (testing "Quantity values"
    (are [x res] (= res (core/-eval (tu/compile-unop elm/to-string elm/quantity
                                                     x)
                                    {} nil nil))
      [1 "m"] "1 'm'"
      [1M "m"] "1 'm'"
      [1.1M "m"] "1.1 'm'"))

  (testing "Date values"
    (are [x res] (= res (core/-eval (tu/compile-unop elm/to-string elm/date x)
                                    {} nil nil))
      "2019" "2019"
      "2019-01" "2019-01"
      "2019-01-01" "2019-01-01"))

  (testing "DateTime values"
    (are [x res] (= res (core/-eval (tu/compile-unop elm/to-string elm/date-time
                                                     x)
                                    {} nil nil))
      "2019-01-01T01:00" "2019-01-01T01:00"))

  (testing "Time values"
    (are [x res] (= res (core/-eval (tu/compile-unop elm/to-string elm/time x)
                                    {} nil nil))
      "01:00" "01:00"))

  ;; TODO: Ratio

  (tu/testing-unary-null elm/to-string))


;; TODO 22.29. ToTime
