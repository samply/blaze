(ns blaze.elm.compiler.type-operators-test
  "22. Type Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
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


(defn- fixture [f]
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
      #elm/as ["{http://hl7.org/fhir}boolean"
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
      #elm/as ["{urn:hl7-org:elm-types:r1}Boolean" #elm/boolean "true"]
      true

      #elm/as ["{urn:hl7-org:elm-types:r1}Integer" #elm/integer "1"]
      1

      #elm/as ["{urn:hl7-org:elm-types:r1}Integer" {:type "Null"}]
      nil

      #elm/as ["{urn:hl7-org:elm-types:r1}DateTime" #elm/date-time "2019-09-04"]
      (system/date-time 2019 9 4)))

  (testing "form"
    (are [elm form] (= form (core/-form (c/compile {} elm)))
      #elm/as ["{urn:hl7-org:elm-types:r1}Integer" {:type "Null"}]
      nil

      #elm/as ["{urn:hl7-org:elm-types:r1}Integer" #elm/integer "1"]
      '(as elm/integer 1)

      #elm/as ["{http://hl7.org/fhir}dateTime"
              {:path "value"
               :scope "R"
               :type "Property"}]
      '(as fhir/dateTime (:value R))

      {:type "As"
       :asTypeSpecifier
       {:type "ListTypeSpecifier"
        :elementType
        {:type "NamedTypeSpecifier"
         :name "{http://hl7.org/fhir}Quantity"}}
       :operand
       {:path "value"
        :scope "R"
        :type "Property"}}
      '(as (list fhir/Quantity) (:value R)))))


;; TODO 22.2. CanConvert
;;
;; The CanConvert operator returns true if the given value can be converted to
;; a specific type, and false otherwise.
;;
;; This operator returns true for conversion:
;;
;; Between String and each of Boolean, Integer, Long, Decimal, Quantity, Ratio,
;; Date, DateTime, and Time,
;;
;; as well as:
;;
;; From Integer to Long, Decimal, or Quantity
;; From Decimal to Quantity
;; Between Date and DateTime
;; From Code to Concept
;; Between Concept and List<Code>
;;
;; Conversion between String and Date/DateTime/Time is checked using the
;; ISO-8601 standard format: YYYY-MM-DDThh:mm:ss(+|-)hh:mm.
;;
;; See Formatting Strings for a description of the formatting strings used in
;; this specification.

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
  (let [compile-op (partial tu/compile-binop elm/can-convert-quantity
                            elm/quantity elm/string)]
    (testing "true"
      (are [argument unit] (true? (compile-op argument unit))
        [5 "mg"] "g"))

    (testing "false"
      (are [argument unit] (false? (compile-op argument unit))
        [5 "mg"] "m")))

  (tu/testing-binary-null elm/can-convert-quantity #elm/quantity [1 "m"]
                          #elm/string "m")

  (testing "form"
    (let [compile-ctx {:library {:parameters {:def [{:name "q"}]}}}
          elm #elm/can-convert-quantity[#elm/parameter-ref "q" #elm/string "g"]
          expr (c/compile compile-ctx elm)]
      (is (= '(can-convert-quantity (param-ref "q") "g") (core/-form expr))))))


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
;;
;; The Convert operator converts a value to a specific type. The result of the
;; operator is the value of the argument converted to the target type, if
;; possible.
;;
;; If no valid conversion exists from the actual value to the target type, the
;; result is null.
;;
;; This operator supports conversion:
;;
;; Between String and each of Boolean, Integer, Long, Decimal, Quantity, Ratio,
;; Date, DateTime, and Time
;;
;; as well as:
;;
;; From Integer to Long, Decimal, or Quantity
;; From Decimal to Quantity
;; Between Date and DateTime
;; From Code to Concept
;; Between Concept and List<Code>
;;
;; Conversion between String and Date/DateTime/Time is performed using the
;; ISO-8601 standard format: YYYY-MM-DDThh:mm:ss(+|-)hh:mm.
;;
;; See Formatting Strings for a description of the formatting strings used in
;; this specification.

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
    #elm/quantity [5 "mg"] #elm/string "g" (quantity/quantity 0.005 "g"))

  (are [argument unit] (nil? (core/-eval (c/compile {} (elm/convert-quantity [argument unit])) {} nil nil))
    #elm/quantity [5 "mg"] #elm/string "m")

  (tu/testing-binary-null elm/convert-quantity #elm/quantity [5 "mg"] #elm/string "m")

  (testing "form"
    (let [compile-ctx {:library {:parameters {:def [{:name "q"}]}}}
          elm #elm/convert-quantity[#elm/parameter-ref "q" #elm/string "g"]
          expr (c/compile compile-ctx elm)]
      (is (= '(convert-quantity (param-ref "q") "g") (core/-form expr))))))


;; 22.7. ConvertsToBoolean
;;
;; The ConvertsToBoolean operator returns true if the value of its argument is
;; or can be converted to a Boolean value.
;;
;; The operator accepts 'true', 't', 'yes', 'y', and '1' as string
;; representations of true, and 'false', 'f', 'no', 'n', and '0' as string
;; representations of false, ignoring case.
;;
;; If the input cannot be interpreted as a valid Boolean value, the result is
;; false.
;;
;; If the input is an Integer or Long, the result is true if the integer is 1
;; or 0.
;;
;; If the input is a Decimal, the result is true if the decimal is 1.0 or 0.0.
;;
;; If the argument is null the result is null.
(deftest compile-converts-to-boolean-test
  (testing "String"
    (are [x] (true? (tu/compile-unop elm/converts-to-boolean elm/string x))
      "true"
      "t"
      "yes"
      "y"
      "1"
      "True"
      "T"
      "TRUE"
      "YES"
      "Yes"
      "Y"
      "false"
      "f"
      "no"
      "n"
      "0"
      "False"
      "F"
      "FALSE"
      "NO"
      "No"
      "N")

    (are [x] (false? (tu/compile-unop elm/converts-to-boolean elm/string x))
      "foo"
      "bar"
      ""))

  (testing "integer"
    (is (true? (tu/compile-unop elm/converts-to-boolean elm/integer "1")))

    (is (true? (tu/compile-unop elm/converts-to-boolean elm/integer "0")))

    (are [x] (false? (tu/compile-unop elm/converts-to-boolean elm/integer x))
      "2"
      "-1"))

  (testing "long"
    (is (true? (tu/compile-unop elm/converts-to-boolean elm/long "1")))

    (is (true? (tu/compile-unop elm/converts-to-boolean elm/long "0")))

    (are [x] (false? (tu/compile-unop elm/converts-to-boolean elm/long x))
      "2"
      "-1"))

  (testing "decimal"
    (are [x] (true? (tu/compile-unop elm/converts-to-boolean elm/decimal x))
      "1"
      "1.0"
      "1.00"
      "1.00000000"
      "0"
      "0.0"
      "0.00"
      "0.00000000")

    (are [x] (false? (tu/compile-unop elm/converts-to-boolean elm/decimal x))
      "0.1"
      "-1.0"
      "2.0"
      "1.1"
      "0.9"))

  (testing "boolean"
    (is (true? (tu/compile-unop elm/converts-to-boolean elm/boolean "true")))

    (is (true? (tu/compile-unop elm/converts-to-boolean elm/boolean "false"))))

  (testing "dynamic"
    (are [x res] (= res (tu/dynamic-compile-eval (elm/converts-to-boolean x)))
      #elm/parameter-ref "A" false))

  (tu/testing-unary-null elm/converts-to-boolean)

  (testing "form"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/converts-to-boolean #elm/parameter-ref "x"
          expr (c/compile compile-ctx elm)]
      (is (= '(converts-to-boolean (param-ref "x")) (core/-form expr))))))

;; TODO 22.8. ConvertsToDate
;;
;; The ConvertsToDate operator returns true if the value of its argument is or
;; can be converted to a Date value.
;;
;; For String values, The operator expects the string to be formatted using the
;; ISO-8601 date representation:
;;
;; YYYY-MM-DD
;;
;; See Formatting Strings for a description of the formatting strings used in
;; this specification.
;;
;; In addition, the string must be interpretable as a valid date value.
;;
;; Note that the operator can take time formatted strings and will ignore the
;; time portions.
;;
;; If the input string is not formatted correctly, or does not represent a
;; valid date value, the result is false.
;;
;; As with date literals, date values may be specified to any precision.
;;
;; If the argument is null, the result is null.

;; TODO 22.9. ConvertsToDateTime
;;
;; The ConvertsToDateTime operator returns true if the value of its argument is
;; or can be converted to a DateTime value.
;;
;; For String values, the operator expects the string to be formatted using the
;; ISO-8601 datetime representation:
;;
;; YYYY-MM-DDThh:mm:ss.fff(Z|+|-)hh:mm
;;
;; See Formatting Strings for a description of the formatting strings used in
;; this specification.
;;
;; In addition, the string must be interpretable as a valid DateTime value.
;;
;; If the input string is not formatted correctly, or does not represent a
;; valid DateTime value, the result is false.
;;
;; As with Date and Time literals, DateTime values may be specified to any
;; precision. If no timezone offset is supplied, the timezone offset of the
;; evaluation request timestamp is assumed.
;;
;; If the argument is null, the result is null.


;; TODO 22.10. ConvertsToDecimal
;;
;; The ConvertsToDecimal operator returns true if the value of its argument is
;; or can be converted to a Decimal value. The operator accepts strings using
;; the following format:
;;
;; (+|-)?#0(.0#)?
;;
;; Meaning an optional polarity indicator, followed by any number of digits
;; (including none), followed by at least one digit, followed optionally by a
;; decimal point, at least one digit, and any number of additional digits
;; (including none).
;;
;; See Formatting Strings for a description of the formatting strings used in
;; this specification.
;;
;; Note that for this operator to return true, the input value must be limited
;; in precision and scale to the maximum precision and scale representable for
;; Decimal values within CQL.
;;
;; If the input string is not formatted correctly, or cannot be interpreted as
;; a valid Decimal value, the result is false.
;;
;; If the input is a Boolean, the result is true.
;;
;; If the argument is null, the result is null.


;; TODO 22.11. ConvertsToLong
;;
;; The ConvertsToLong operator returns true if the value of its argument is or
;; can be converted to a Long value. The operator accepts strings using the
;; following format:
;;
;; (+|-)?#0
;;
;; Meaning an optional polarity indicator, followed by any number of digits
;; (including none), followed by at least one digit.
;;
;; See Formatting Strings for a description of the formatting strings used in
;; this specification.
;;
;; Note that for this operator to return true, the input must be a valid value
;; in the range representable for Long values in CQL.
;;
;; If the input string is not formatted correctly, or cannot be interpreted as
;; a valid Long value, the result is false.
;;
;; If the input is a Boolean, the result is true.
;;
;; If the argument is null, the result is null.

;; TODO 22.12. ConvertsToInteger
;;
;; The ConvertsToInteger operator returns true if the value of its argument is
;; or can be converted to an Integer value. The operator accepts strings using
;; the following format:
;;
;; (+|-)?#0
;;
;; Meaning an optional polarity indicator, followed by any number of digits
;; (including none), followed by at least one digit.
;;
;; See Formatting Strings for a description of the formatting strings used in
;; this specification.
;;
;; Note that for this operator to return true, the input must be a valid value
;; in the range representable for Integer values in CQL.
;;
;; If the input string is not formatted correctly, or cannot be interpreted as
;; a valid Integer value, the result is false.
;;
;; If the input is a Boolean, the result is true
;;
;; If the argument is null, the result is null.

;; TODO 22.13. ConvertsToQuantity
;;
;; The ConvertsToQuantity operator returns true if the value of its argument is
;; or can be converted to a Quantity value. The operator may be used with
;; Integer, Decimal, Ratio, or String values.
;;
;; For String values, the operator accepts strings using the following format:
;;
;; (+|-)?#0(.0#)?('<unit>')?
;;
;; Meaning an optional polarity indicator, followed by any number of digits
;; (including none) followed by at least one digit, optionally followed by a
;; decimal point, at least one digit, and any number of additional digits, all
;; optionally followed by a unit designator as a string literal specifying a
;; valid UCUM unit of measure or calendar duration keyword, singular or plural.
;; Spaces are allowed between the quantity value and the unit designator.
;;
;; See Formatting Strings for a description of the formatting strings used in
;; this specification.
;;
;; Note that the decimal value of the quantity returned by this operator must
;; be a valid value in the range representable for Decimal values in CQL.
;;
;; If the input string is not formatted correctly, or cannot be interpreted as
;; a valid Quantity value, the result is false.
;;
;; For Integer, Decimal, and Ratio values, the operator simply returns true.
;;
;; If the argument is null, the result is null.

;; TODO 22.14. ConvertsToRatio
;;
;; The ConvertsToRatio operator returns true if the value of its argument is or
;; can be converted to a Ratio value. The operator accepts strings using the
;; following format:
;;
;; <quantity>:<quantity>
;;
;; Meaning a quantity, followed by a colon (:), followed by another quantity.
;; The operator accepts quantity strings using the same format as the
;; ToQuantity operator.
;;
;; If the input string is not formatted correctly, or cannot be interpreted as
;; a valid Ratio value, the result is false.
;;
;; If the argument is null, the result is null.

;; 22.15. ConvertsToString
;;
;; The ConvertsToString operator returns true if the value of its argument is
;; or can be converted to a String value.
;;
;; The operator returns true if the argument is any of the following types:
;;
;; Boolean
;; Integer
;; Long
;; Decimal
;; DateTime
;; Date
;; Time
;; Quantity
;; Ratio
;; String
;;
;; If the argument is null, the result is null.
(deftest compile-converts-to-string-test
  (testing "Boolean"
    (are [x] (true? (core/-eval (tu/compile-unop elm/converts-to-string elm/boolean x)
                                    {} nil nil))
      "true"
      "false"))

  (testing "Integer"
    (are [x] (true? (core/-eval (tu/compile-unop elm/converts-to-string elm/integer x)
                                    {} nil nil))
      "-1"
      "0"
      "1"))

  (testing "Decimal"
    (are [x] (true? (core/-eval (tu/compile-unop elm/converts-to-string elm/decimal x)
                                    {} nil nil))
      "-1"
      "0"
      "1"

      "-1.1"
      "0.0"
      "1.1"

      "0.0001"
      "0.00001"
      "0.000001"
      "0.0000001"
      "0.00000001"
      "0.000000001"
      "0.000000005"))

  (testing "Quantity"
    (are [x] (true? (core/-eval (tu/compile-unop elm/converts-to-string elm/quantity
                                                     x)
                                    {} nil nil))
      [1 "m"]
      [1M "m"]
      [1.1M "m"]))

  (testing "Date"
    (are [x] (true? (core/-eval (tu/compile-unop elm/converts-to-string elm/date x)
                                    {} nil nil))
      "2019"
      "2019-01"
      "2019-01-01"))

  (testing "DateTime"
    (are [x] (true? (core/-eval (tu/compile-unop elm/converts-to-string elm/date-time
                                                     x)
                                    {} nil nil))
      "2019-01-01T01:00"))

  (testing "Time"
    (are [x] (true? (core/-eval (tu/compile-unop elm/converts-to-string elm/time x)
                                    {} nil nil))
      "01:00"))

  ;; TODO: Ratio

  (testing "dynamic"
    (are [x res] (= res (tu/dynamic-compile-eval (elm/converts-to-string x)))
      #elm/parameter-ref "A" true))

  (tu/testing-unary-null elm/converts-to-string)

  (testing "form"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/converts-to-string #elm/parameter-ref "x"
          expr (c/compile compile-ctx elm)]
      (is (= '(converts-to-string (param-ref "x")) (core/-form expr))))))

;; TODO 22.16. ConvertsToTime
;;
;; The ConvertsToTime operator returns true if the value of its argument is or
;; can be converted to a Time value.
;;
;; For String values, the operator expects the string to be formatted using
;; ISO-8601 time representation:
;;
;; hh:mm:ss.fff
;;
;; See Formatting Strings for a description of the formatting strings used in
;; this specification.
;;
;; In addition, the string must be interpretable as a valid time-of-day value.
;;
;; If the input string is not formatted correctly, or does not represent a
;; valid time-of-day value, the result is false.
;;
;; As with time-of-day literals, time-of-day values may be specified to any
;; precision. If no timezone offset is supplied, the timezone offset of the
;; evaluation request timestamp is assumed.
;;
;; If the argument is null, the result is null.

;; 22.17. Descendents
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


;; TODO 22.18. Is
;;
;; The Is operator allows the type of a result to be tested. The language must
;; support the ability to test against any type. If the run-time type of the
;; argument is of the type being tested, the result of the operator is true;
;; otherwise, the result is false.

;; 22.19. ToBoolean
;;
;; The ToBoolean operator converts the value of its argument to a Boolean
;; value.
;;
;; The operator accepts 'true', 't', 'yes', 'y', and '1' as string
;; representations of true, and 'false', 'f', 'no', 'n', and '0' as
;; string representations of false, ignoring case.
;;
;; If the input is an Integer or Long, the result is true if the integer is 1,
;; false if the integer is 0.
;;
;; If the input is a Decimal, the result is true if the decimal is 1.0,
;; false if the decimal is 0.0.
;;
;; If the input cannot be interpreted as a valid Boolean value, the result is
;; null.
;;
;; If the argument is null the result is null.
(deftest compile-to-boolean-test
  (testing "String"
    (are [x] (true? (tu/compile-unop elm/to-boolean elm/string x))
      "true"
      "t"
      "yes"
      "y"
      "1"
      "True"
      "T"
      "TRUE"
      "YES"
      "Yes"
      "Y")

    (are [x] (false? (tu/compile-unop elm/to-boolean elm/string x))
      "false"
      "f"
      "no"
      "n"
      "0"
      "False"
      "F"
      "FALSE"
      "NO"
      "No"
      "N")

    (are [x] (nil? (tu/compile-unop elm/to-boolean elm/string x))
      "foo"
      "bar"
      ""))

  (testing "integer"
    (is (true? (tu/compile-unop elm/to-boolean elm/integer "1")))

    (is (false? (tu/compile-unop elm/to-boolean elm/integer "0")))

    (are [x] (nil? (tu/compile-unop elm/to-boolean elm/integer x))
      "2"
      "-1"))

  (testing "long"
    (is (true? (tu/compile-unop elm/to-boolean elm/long "1")))

    (is (false? (tu/compile-unop elm/to-boolean elm/long "0")))

    (are [x] (nil? (tu/compile-unop elm/to-boolean elm/long x))
      "2"
      "-1"))

  (testing "decimal"
    (are [x] (true? (tu/compile-unop elm/to-boolean elm/decimal x))
      "1"
      "1.0"
      "1.00"
      "1.00000000")

    (are [x] (false? (tu/compile-unop elm/to-boolean elm/decimal x))
      "0"
      "0.0"
      "0.00"
      "0.00000000")

    (are [x] (nil? (tu/compile-unop elm/to-boolean elm/decimal x))
      "0.1"
      "-1.0"
      "2.0"
      "1.1"
      "0.9"))

  (testing "boolean"
    (is (true? (tu/compile-unop elm/to-boolean elm/boolean "true")))

    (is (false? (tu/compile-unop elm/to-boolean elm/boolean "false"))))

  (tu/testing-unary-null elm/to-boolean)

  (testing "form"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/to-boolean #elm/parameter-ref "x"
          expr (c/compile compile-ctx elm)]
      (is (= '(to-boolean (param-ref "x")) (core/-form expr))))))


;; 22.20. ToChars
;;
;; The ToChars operator takes a string and returns a list with one string for
;; each character in the input, in the order in which they appear in the
;; string.
;;
;; If the argument is null, the result is null.
(deftest compile-to-chars-test
  (testing "String"
    (are [x res] (= res (tu/compile-unop elm/to-chars elm/string x))
      "A" '("A")
      "ab" '("a" "b")
      "" '()))

  (testing "Integer"
    (are [x] (nil? (tu/compile-unop elm/to-chars elm/integer x))
      "1" ))

  (testing "dynamic"
    (are [x res] (= res (tu/dynamic-compile-eval (elm/to-chars x)))
      #elm/parameter-ref "A" '("A")
      #elm/parameter-ref "ab" '("a" "b")
      #elm/parameter-ref "empty-string" '()))

  (tu/testing-unary-null elm/to-chars)

  (testing "form"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/to-chars #elm/parameter-ref "x"
          expr (c/compile compile-ctx elm)]
      (is (= '(to-chars (param-ref "x")) (core/-form expr))))))

;; TODO 22.21. ToConcept
;;
;; The ToConcept operator converts a value of type Code to a Concept value with
;; the given Code as its primary and only Code. If the Code has a display
;; value, the resulting Concept will have the same display value.
;;
;; If the input is a list of Codes, the resulting Concept will have all the
;; input Codes, and will not have a display value.
;;
;; If the argument is null, the result is null.


;; 22.22. ToDate
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
    (testing "String"
      (are [x res] (= res (eval (tu/compile-unop elm/to-date elm/string x)))
        "2019" (system/date 2019)
        "2019-01" (system/date 2019 1)
        "2019-01-01" (system/date 2019 1 1)

        "aaaa" nil
        "2019-13" nil
        "2019-02-29" nil))

    (testing "Date"
      (are [x res] (= res (eval (tu/compile-unop elm/to-date elm/date x)))
        "2019" (system/date 2019)
        "2019-01" (system/date 2019 1)
        "2019-01-01" (system/date 2019 1 1)))

    (testing "DateTime"
      (are [x res] (= res (eval (tu/compile-unop elm/to-date elm/date-time x)))
        "2019" (system/date 2019)
        "2019-01" (system/date 2019 1)
        "2019-01-01" (system/date 2019 1 1)
        "2019-01-01T12:13" (system/date 2019 1 1))))

  (tu/testing-unary-null elm/to-date))


;; 22.23. ToDateTime
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
    (testing "String"
      (are [x res] (= res (eval (tu/compile-unop elm/to-date-time elm/string x)))
        "2020" (system/date-time 2020)
        "2020-03" (system/date-time 2020 3)
        "2020-03-08" (system/date-time 2020 3 8)
        "2020-03-08T12:54:00" (system/date-time 2020 3 8 12 54)
        "2020-03-08T12:54:00+00:00" (system/date-time 2020 3 8 12 54)
        "2020-03-08T12:54:00+01:00" (system/date-time 2020 3 8 11 54)

        "aaaa" nil
        "2019-13" nil
        "2019-02-29" nil))

    (testing "Date"
      (testing "Static"
        (are [x res] (= res (tu/compile-unop elm/to-date-time elm/date x))
          "2020" (system/date-time 2020)
          "2020-03" (system/date-time 2020 3)
          "2020-03-08" (system/date-time 2020 3 8))))

    (testing "DateTime"
      (are [x res] (= res (eval (tu/compile-unop elm/to-date-time elm/date-time x)))
        "2020" (system/date-time 2020)
        "2020-03" (system/date-time 2020 3)
        "2020-03-08" (system/date-time 2020 3 8)
        "2020-03-08T12:13" (system/date-time 2020 3 8 12 13))))

  (tu/testing-unary-null elm/to-date-time)

  (tu/testing-unary-form elm/to-date-time))


;; 22.24. ToDecimal
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
  (testing "String"
    (are [x res] (= res (tu/compile-unop elm/to-decimal elm/string x))
      (str decimal/min) decimal/min
      "-1.1" -1.1M
      "-1" -1M
      "0" 0M
      "1" 1M
      (str decimal/max) decimal/max

      (str (- decimal/min 1e-8M)) nil
      (str (+ decimal/max 1e-8M)) nil
      "a" nil))

  (tu/testing-unary-null elm/to-decimal)

  (testing "form"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/to-decimal #elm/parameter-ref "x"
          expr (c/compile compile-ctx elm)]
      (is (= '(to-decimal (param-ref "x")) (core/-form expr))))))


;; 22.25. ToInteger
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
;; If the input is Boolean, true will result in 1, false will result in 0.
;;
;; If the argument is null, the result is null.
(deftest compile-to-integer-test
  (testing "String"
    (are [x res] (= res (tu/compile-unop elm/to-integer elm/string x))
      (str Integer/MIN_VALUE) Integer/MIN_VALUE
      "-1" -1
      "0" 0
      "1" 1
      (str Integer/MAX_VALUE) Integer/MAX_VALUE

      (str (dec Integer/MIN_VALUE)) nil
      (str (inc Integer/MAX_VALUE)) nil
      "a" nil))

  (testing "Boolean"
    (are [x res] (= res (tu/compile-unop elm/to-integer elm/boolean x))
      "true" 1
      "false" 0))

  (tu/testing-unary-null elm/to-integer)

  (testing "form"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/to-integer #elm/parameter-ref "x"
          expr (c/compile compile-ctx elm)]
      (is (= '(to-integer (param-ref "x")) (core/-form expr))))))


;; 22.26. ToList
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
    (is (= [] (core/-eval (c/compile {} #elm/to-list{:type "Null"}) {} nil nil))))

  (testing "form"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/to-list #elm/parameter-ref "x"
          expr (c/compile compile-ctx elm)]
      (is (= '(to-list (param-ref "x")) (core/-form expr))))))


;; 22.27. ToLong
;;
;; The ToLong operator converts the value of its argument to a Long value. The
;; operator accepts strings using the following format:
;;
;; (+|-)?#0
;;
;; Meaning an optional polarity indicator, followed by any number of digits
;; (including none), followed by at least one digit.
;;
;; See Formatting Strings for a description of the formatting strings used in
;; this specification.
;;
;; Note that the long value returned by this operator must be a valid value in
;; the range representable for Long values in CQL.
;;
;; If the input string is not formatted correctly, or cannot be interpreted as a
;; valid Long value, the result is null.
;;
;; If the input is Boolean, true will result in 1, false will result in 0.
;;
;; If the argument is null, the result is null.
(deftest compile-to-long-test
  (testing "String"
    (are [x res] (= res (tu/compile-unop elm/to-long elm/string x))
      (str Long/MIN_VALUE) Long/MIN_VALUE
      "-1" -1
      "0" 0
      "1" 1
      (str Long/MAX_VALUE) Long/MAX_VALUE

      (str (dec (bigint Long/MIN_VALUE))) nil
      (str (inc (bigint Long/MAX_VALUE))) nil

      "a" nil))

  (testing "Boolean"
    (are [x res] (= res (tu/compile-unop elm/to-long elm/boolean x))
      "true" 1
      "false" 0))

  (tu/testing-unary-null elm/to-long)

  (testing "form"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/to-long #elm/parameter-ref "x"
          expr (c/compile compile-ctx elm)]
      (is (= '(to-long (param-ref "x")) (core/-form expr))))))


;; 22.28. ToQuantity
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
  (testing "String"
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

  (testing "Integer"
    (are [x res] (= res (core/-eval (tu/compile-unop elm/to-quantity elm/integer x)
                                    {} nil nil))
      "1" (quantity/quantity 1 "1")))

  (testing "Decimal"
    (are [x res] (p/equal res (core/-eval (tu/compile-unop elm/to-quantity
                                                           elm/decimal x)
                                          {} nil nil))
      "1" (quantity/quantity 1 "1")
      "1.1" (quantity/quantity 1.1M "1")))

  ;; TODO: Ratio

  (tu/testing-unary-null elm/to-quantity)

  (testing "form"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/to-quantity #elm/parameter-ref "x"
          expr (c/compile compile-ctx elm)]
      (is (= '(to-quantity (param-ref "x")) (core/-form expr))))))


;; TODO 22.29. ToRatio
;;
;; The ToRatio operator converts the value of its argument to a Ratio value.
;; The operator accepts strings using the following format:
;;
;; <quantity>:<quantity>
;;
;; Meaning a quantity, followed by a colon (:), followed by another quantity.
;; The operator accepts quantity strings using the same format as the
;; ToQuantity operator.
;;
;; If the input string is not formatted correctly, or cannot be interpreted as
;; a valid Ratio value, the result is null.
;;
;; If the argument is null, the result is null.


;; 22.30. ToString
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
  (testing "Boolean"
    (are [x res] (= res (core/-eval (tu/compile-unop elm/to-string elm/boolean x)
                                    {} nil nil))
      "true" "true"
      "false" "false"))

  (testing "Integer"
    (are [x res] (= res (core/-eval (tu/compile-unop elm/to-string elm/integer x)
                                    {} nil nil))
      "-1" "-1"
      "0" "0"
      "1" "1"))

  (testing "Decimal"
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

  (testing "Quantity"
    (are [x res] (= res (core/-eval (tu/compile-unop elm/to-string elm/quantity
                                                     x)
                                    {} nil nil))
      [1 "m"] "1 'm'"
      [1M "m"] "1 'm'"
      [1.1M "m"] "1.1 'm'"))

  (testing "Date"
    (are [x res] (= res (core/-eval (tu/compile-unop elm/to-string elm/date x)
                                    {} nil nil))
      "2019" "2019"
      "2019-01" "2019-01"
      "2019-01-01" "2019-01-01"))

  (testing "DateTime"
    (are [x res] (= res (core/-eval (tu/compile-unop elm/to-string elm/date-time
                                                     x)
                                    {} nil nil))
      "2019-01-01T01:00" "2019-01-01T01:00"))

  (testing "Time"
    (are [x res] (= res (core/-eval (tu/compile-unop elm/to-string elm/time x)
                                    {} nil nil))
      "01:00" "01:00"))

  ;; TODO: Ratio

  (tu/testing-unary-null elm/to-string)

  (testing "form"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/to-string #elm/parameter-ref "x"
          expr (c/compile compile-ctx elm)]
      (is (= '(to-string (param-ref "x")) (core/-form expr))))))


;; TODO 22.31. ToTime
;;
;; The ToTime operator converts the value of its argument to a Time value.
;;
;; For String values, the operator expects the string to be formatted using
;; ISO-8601 time representation:
;;
;; hh:mm:ss.fff
;;
;; See Formatting Strings for a description of the formatting strings used in
;; this specification.
;;
;; In addition, the string must be interpretable as a valid time-of-day value.
;;
;; If the input string is not formatted correctly, or does not represent a
;; valid time-of-day value, the result is null.
;;
;; As with time-of-day literals, time-of-day values may be specified to any
;; precision.
;;
;; For DateTime values, the result is the same as extracting the Time component
;; from the DateTime value.
;;
;; If the argument is null, the result is null.