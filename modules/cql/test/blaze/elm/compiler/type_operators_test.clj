(ns blaze.elm.compiler.type-operators-test
  "22. Type Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.elm.code :as code]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler.clinical-operators]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.core-spec]
   [blaze.elm.compiler.test-util :as ctu :refer [has-form]]
   [blaze.elm.compiler.type-operators]
   [blaze.elm.concept :as concept]
   [blaze.elm.decimal :as decimal]
   [blaze.elm.literal :as elm]
   [blaze.elm.literal-spec]
   [blaze.elm.protocols :as p]
   [blaze.elm.quantity :refer [quantity]]
   [blaze.elm.quantity-spec]
   [blaze.elm.ratio :refer [ratio]]
   [blaze.elm.util-spec]
   [blaze.fhir.spec.type.system :as system]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]))

(st/instrument)
(ctu/instrument-compile)

(defn- fixture [f]
  (st/instrument)
  (ctu/instrument-compile)
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
               #elm/scope-property ["R" "deceased"]]
      {:fhir/type :fhir/Patient :id "0" :deceased #fhir/boolean true}
      #fhir/boolean true

      #elm/as ["{http://hl7.org/fhir}integer"
               #elm/scope-property ["R" "value"]]
      {:fhir/type :fhir/Observation :value #fhir/integer 1}
      #fhir/integer 1

      #elm/as ["{http://hl7.org/fhir}string"
               #elm/scope-property ["R" "name"]]
      {:fhir/type :fhir/Account :name #fhir/string "a"}
      #fhir/string "a"

      #elm/as ["{http://hl7.org/fhir}decimal"
               #elm/scope-property ["R" "duration"]]
      {:fhir/type :fhir/Media :duration #fhir/decimal 1.1M}
      #fhir/decimal 1.1M

      #elm/as ["{http://hl7.org/fhir}uri"
               #elm/scope-property ["R" "url"]]
      {:fhir/type :fhir/Measure :url #fhir/uri "a"}
      #fhir/uri "a"

      #elm/as ["{http://hl7.org/fhir}url"
               #elm/scope-property ["R" "address"]]
      {:fhir/type :fhir/Endpoint :address #fhir/url "a"}
      #fhir/url "a"

      #elm/as ["{http://hl7.org/fhir}dateTime"
               #elm/scope-property ["R" "value"]]
      {:fhir/type :fhir/Observation :value #fhir/dateTime #system/date-time "2019-09-04"}
      #fhir/dateTime #system/date-time "2019-09-04"

      #elm/as ["{http://hl7.org/fhir}Quantity"
               #elm/scope-property ["R" "value"]]
      {:fhir/type :fhir/Observation :value #fhir/dateTime #system/date-time "2019-09-04"}
      nil))

  (testing "ELM types"
    (are [elm res] (= res (core/-eval (c/compile {} elm) {} nil nil))
      #elm/as ["{urn:hl7-org:elm-types:r1}Boolean" #elm/boolean "true"]
      true

      #elm/as ["{urn:hl7-org:elm-types:r1}Integer" #elm/integer "1"]
      1

      #elm/as ["{urn:hl7-org:elm-types:r1}Integer" {:type "Null"}]
      nil

      #elm/as ["{urn:hl7-org:elm-types:r1}DateTime" #elm/date-time"2019-09-04"]
      (system/date-time 2019 9 4)))

  (let [expr (ctu/dynamic-compile #elm/as["{urn:hl7-org:elm-types:r1}Integer"
                                          #elm/parameter-ref "x"])]

    (testing "expression is dynamic"
      (is (false? (core/-static expr))))

    (ctu/testing-constant-attach-cache expr)

    (ctu/testing-constant-patient-count expr)

    (testing "resolve expression references"
      (let [elm #elm/as["{urn:hl7-org:elm-types:r1}Integer"
                        #elm/expression-ref "x"]
            expr-def {:type "ExpressionDef" :name "x" :expression "y"
                      :context "Patient"}
            ctx {:library {:statements {:def [expr-def]}}}
            expr (c/resolve-refs (c/compile ctx elm) {"x" expr-def})]
        (has-form expr '(as elm/integer "y"))))

    (testing "resolve parameters"
      (let [expr (c/resolve-params expr {"x" "y"})]
        (has-form expr '(as elm/integer "y")))))

  (ctu/testing-equals-hash-code #elm/as["{urn:hl7-org:elm-types:r1}Integer"
                                        #elm/parameter-ref "x"])

  (testing "form"
    (are [elm form] (= form (c/form (c/compile {} elm)))
      #elm/as ["{urn:hl7-org:elm-types:r1}Integer" {:type "Null"}]
      nil

      #elm/as ["{urn:hl7-org:elm-types:r1}Integer" #elm/integer "1"]
      '(as elm/integer 1)

      #elm/as ["{http://hl7.org/fhir}dateTime"
               #elm/scope-property ["R" "value"]]
      '(as fhir/dateTime (:value R))

      {:type "As"
       :asTypeSpecifier
       {:type "ListTypeSpecifier"
        :elementType
        {:type "NamedTypeSpecifier"
         :name "{http://hl7.org/fhir}Quantity"}}
       :operand
       #elm/scope-property ["R" "value"]}
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
  (let [compile-op (partial ctu/compile-binop elm/can-convert-quantity
                            elm/quantity elm/string)]
    (testing "true"
      (are [argument unit] (true? (compile-op argument unit))
        [5 "mg"] "g"))

    (testing "false"
      (are [argument unit] (false? (compile-op argument unit))
        [5 "mg"] "m")))

  (ctu/testing-binary-null elm/can-convert-quantity #elm/quantity [1 "m"]
                           #elm/string "m")

  (ctu/testing-binary-op elm/can-convert-quantity))

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
                                      {:now ctu/now} nil nil))
      (ctu/code "system-134534" "code-134551")
      ["code-134551" nil "system-134534" nil]))

  ;; TODO: other types

  (ctu/testing-unary-null elm/children)

  (ctu/testing-unary-op elm/children)

  (ctu/testing-optimize elm/children
    (testing "Code"
      #ctu/optimize-to (code/code "system-164913" "version-164919" "code-164924")
      ["code-164924" nil "system-164913" "version-164919"])))

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
    #elm/quantity [5 "mg"] #elm/string "g" (quantity 0.005M "g"))

  (are [argument unit] (nil? (core/-eval (c/compile {} (elm/convert-quantity [argument unit])) {} nil nil))
    #elm/quantity [5 "mg"] #elm/string "m")

  (ctu/testing-binary-null elm/convert-quantity #elm/quantity [5 "mg"] #elm/string "m")

  (ctu/testing-binary-op elm/convert-quantity))

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
    (are [x] (true? (ctu/compile-unop elm/converts-to-boolean elm/string x))
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

    (are [x] (false? (ctu/compile-unop elm/converts-to-boolean elm/string x))
      "foo"
      "bar"
      ""))

  (testing "Integer"
    (is (true? (ctu/compile-unop elm/converts-to-boolean elm/integer "1")))

    (is (true? (ctu/compile-unop elm/converts-to-boolean elm/integer "0")))

    (are [x] (false? (ctu/compile-unop elm/converts-to-boolean elm/integer x))
      "2"
      "-1"))

  (testing "Long"
    (is (true? (ctu/compile-unop elm/converts-to-boolean elm/long "1")))

    (is (true? (ctu/compile-unop elm/converts-to-boolean elm/long "0")))

    (are [x] (false? (ctu/compile-unop elm/converts-to-boolean elm/long x))
      "2"
      "-1"))

  (testing "Decimal"
    (are [x] (true? (ctu/compile-unop elm/converts-to-boolean elm/decimal x))
      "1"
      "1.0"
      "1.00"
      "1.00000000"
      "0"
      "0.0"
      "0.00"
      "0.00000000")

    (are [x] (false? (ctu/compile-unop elm/converts-to-boolean elm/decimal x))
      "0.1"
      "-1.0"
      "2.0"
      "1.1"
      "0.9"))

  (testing "Boolean"
    (is (true? (ctu/compile-unop elm/converts-to-boolean elm/boolean "true")))

    (is (true? (ctu/compile-unop elm/converts-to-boolean elm/boolean "false"))))

  (testing "Dynamic"
    (are [x res] (= res (ctu/dynamic-compile-eval (elm/converts-to-boolean x)))
      #elm/parameter-ref "A" false))

  (ctu/testing-unary-null elm/converts-to-boolean)

  (ctu/testing-unary-op elm/converts-to-boolean)

  (ctu/testing-optimize elm/converts-to-boolean
    (testing "String"
      #ctu/optimize-to "true"
      #ctu/optimize-to "false"
      true)

    (testing "Integer - true"
      #ctu/optimize-to "0"
      #ctu/optimize-to "1"
      true)

    (testing "Integer - false"
      #ctu/optimize-to "2"
      #ctu/optimize-to "-1"
      false)))

;; 22.8. ConvertsToDate
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
(deftest compile-converts-to-date-test
  (let [eval #(core/-eval % {:now ctu/now} nil nil)]
    (testing "String"
      (are [x] (true? (eval (ctu/compile-unop elm/converts-to-date elm/string x)))
        "2019"
        "2019-01"
        "2019-01-01")

      (are [x] (false? (eval (ctu/compile-unop elm/converts-to-date elm/string x)))
        "aaaa"
        "2019-13"
        "2019-02-29"))

    (testing "Date"
      (are [x] (true? (eval (ctu/compile-unop elm/converts-to-date elm/date x)))
        "2019"
        "2019-01"
        "2019-01-01"))

    (testing "DateTime"
      (are [x] (true? (eval (ctu/compile-unop elm/converts-to-date elm/date-time x)))
        "2019"
        "2019-01"
        "2019-01-01"
        "2019-01-01T12:13")))

  (ctu/testing-unary-null elm/converts-to-date)

  (ctu/testing-unary-op elm/converts-to-date))

;; 22.9. ConvertsToDateTime
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
(deftest compile-converts-to-date-time-test
  (let [eval #(core/-eval % {:now ctu/now} nil nil)]
    (testing "String"
      (are [x] (true? (eval (ctu/compile-unop elm/converts-to-date-time elm/string x)))
        "2020-03-08T12:54:00+01:00")

      (are [x] (false? (eval (ctu/compile-unop elm/converts-to-date-time elm/string x)))
        "2019-13"
        "2019-02-29"))

    (testing "Date"
      (testing "Static"
        (are [x] (true? (ctu/compile-unop elm/converts-to-date-time elm/date x))
          "2020"
          "2020-03"
          "2020-03-08")))

    (testing "DateTime"
      (are [x] (true? (eval (ctu/compile-unop elm/converts-to-date-time elm/date-time x)))
        "2020"
        "2020-03"
        "2020-03-08"
        "2020-03-08T12:13")))

  (ctu/testing-unary-null elm/converts-to-date-time)

  (ctu/testing-unary-op elm/converts-to-date-time))

;; 22.10. ConvertsToDecimal
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
(deftest compile-converts-to-decimal-test
  (testing "String"
    (are [x] (true? (ctu/compile-unop elm/converts-to-decimal elm/string x))
      (str decimal/min)
      "-1"
      "0"
      "1"
      (str decimal/max))

    (are [x] (false? (ctu/compile-unop elm/converts-to-decimal elm/string x))
      (str (- decimal/min 1e-8M))
      (str (+ decimal/max 1e-8M))
      "a"))

  (testing "Boolean"
    (are [x] (true? (ctu/compile-unop elm/converts-to-decimal elm/boolean x))
      "true"))

  (testing "Decimal"
    (are [x] (true? (ctu/compile-unop elm/converts-to-decimal elm/decimal x))
      "1.1"))

  (testing "Dynamic"
    (are [x] (false? (ctu/dynamic-compile-eval (elm/converts-to-decimal x)))
      #elm/parameter-ref "A")
    (are [x] (true? (ctu/dynamic-compile-eval (elm/converts-to-decimal x)))
      #elm/parameter-ref "1"))

  (ctu/testing-unary-null elm/converts-to-decimal)

  (ctu/testing-unary-op elm/converts-to-decimal)

  (ctu/testing-optimize elm/converts-to-decimal
    (testing "String"
      #ctu/optimize-to "-1"
      #ctu/optimize-to "0"
      #ctu/optimize-to "1"
      true)))

;; 22.11. ConvertsToLong
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
(deftest compile-converts-to-long-test
  (testing "String"
    (are [x] (true? (ctu/compile-unop elm/converts-to-long elm/string x))
      (str Long/MIN_VALUE)
      "-1"
      "0"
      "1"
      (str Long/MAX_VALUE))

    (are [x] (false? (ctu/compile-unop elm/converts-to-long elm/string x))
      (str (dec (bigint Long/MIN_VALUE)))
      (str (inc (bigint Long/MAX_VALUE)))
      "a"))

  (testing "Boolean"
    (are [x] (true? (ctu/compile-unop elm/converts-to-long elm/boolean x))
      "true"))

  (testing "Long"
    (are [x] (true? (ctu/compile-unop elm/converts-to-long elm/long x))
      "1"))

  (testing "Dynamic"
    (are [x] (false? (ctu/dynamic-compile-eval (elm/converts-to-long x)))
      #elm/parameter-ref "A")
    (are [x] (true? (ctu/dynamic-compile-eval (elm/converts-to-long x)))
      #elm/parameter-ref "1"))

  (ctu/testing-unary-null elm/converts-to-long)

  (ctu/testing-unary-op elm/converts-to-long)

  (ctu/testing-optimize elm/converts-to-long
    (testing "String"
      #ctu/optimize-to "-1"
      #ctu/optimize-to "0"
      #ctu/optimize-to "1"
      true)))

;; 22.12. ConvertsToInteger
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
(deftest compile-converts-to-integer-test
  (testing "String"
    (are [x] (true? (ctu/compile-unop elm/converts-to-integer elm/string x))
      (str Integer/MIN_VALUE)
      "-1"
      "0"
      "1"
      (str Integer/MAX_VALUE))
    (are [x] (false? (ctu/compile-unop elm/converts-to-integer elm/string x))
      (str (dec Integer/MIN_VALUE))
      (str (inc Integer/MAX_VALUE))
      "a"))

  (testing "Boolean"
    (are [x] (true? (ctu/compile-unop elm/converts-to-integer elm/boolean x))
      "true"))

  (testing "Integer"
    (are [x] (true? (ctu/compile-unop elm/converts-to-integer elm/integer x))
      "1"))

  (testing "Dynamic"
    (are [x] (false? (ctu/dynamic-compile-eval (elm/converts-to-integer x)))
      #elm/parameter-ref "A")
    (are [x] (true? (ctu/dynamic-compile-eval (elm/converts-to-integer x)))
      #elm/parameter-ref "1"))

  (ctu/testing-unary-null elm/converts-to-integer)

  (ctu/testing-unary-op elm/converts-to-integer)

  (ctu/testing-optimize elm/converts-to-integer
    (testing "String"
      #ctu/optimize-to "-1"
      #ctu/optimize-to "0"
      #ctu/optimize-to "1"
      true)))

;; 22.13. ConvertsToQuantity
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
(deftest compile-converts-to-quantity-test
  (testing "String"
    (are [x] (true? (ctu/compile-unop elm/converts-to-quantity elm/string x))
      (str decimal/min "'m'")
      "-1'm'"
      "0'm'"
      "1'm'"
      (str decimal/max "'m'"))

    (are [x] (false? (ctu/compile-unop elm/converts-to-quantity elm/string x))
      (str (- decimal/min 1e-8M))
      (str (+ decimal/max 1e-8M))
      (str (- decimal/min 1e-8M) "'m'")
      (str (+ decimal/max 1e-8M) "'m'")
      ""
      "a"))

  (testing "Integer"
    (is (true? (ctu/compile-unop elm/converts-to-quantity elm/integer "1"))))

  (testing "Decimal"
    (is (true? (ctu/compile-unop elm/converts-to-quantity elm/decimal "1.1"))))

  (testing "Ratio"
    (are [x] (true? (ctu/compile-unop elm/converts-to-quantity elm/ratio x))
      [[-1] [-1]]
      [[1] [1]]
      [[1 "s"] [1 "s"]]
      [[1 "m"] [1 "s"]]
      [[10 "s"] [1 "s"]]))

  (testing "Dynamic"
    (are [x] (false? (ctu/dynamic-compile-eval (elm/converts-to-quantity x)))
      #elm/parameter-ref "A")
    (are [x] (true? (ctu/dynamic-compile-eval (elm/converts-to-quantity x)))
      #elm/parameter-ref "1"))

  (ctu/testing-unary-null elm/converts-to-quantity)

  (ctu/testing-unary-op elm/converts-to-quantity)

  (ctu/testing-optimize elm/converts-to-quantity
    (testing "String"
      #ctu/optimize-to "-1'm'"
      #ctu/optimize-to "0'm'"
      #ctu/optimize-to "1'm'"
      true)))

;; 22.14. ConvertsToRatio
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
(deftest compile-converts-to-ratio-test
  (testing "String"
    (are [x] (true? (ctu/compile-unop elm/converts-to-ratio elm/string x))
      "-1'm':-1'm'"
      "0'm':0'm'"
      "1'm':1'm'")

    (are [x] (false? (ctu/compile-unop elm/converts-to-ratio elm/string x))
      ""
      "a"
      "0'm';0'm'"))

  (testing "Dynamic"
    (are [x] (false? (ctu/dynamic-compile-eval (elm/converts-to-ratio x)))
      #elm/parameter-ref "A"))

  (ctu/testing-unary-null elm/converts-to-ratio)

  (ctu/testing-unary-op elm/converts-to-ratio)

  (ctu/testing-optimize elm/converts-to-ratio
    (testing "String"
      #ctu/optimize-to "-1'm':-1'm'"
      #ctu/optimize-to "0'm':0'm'"
      #ctu/optimize-to "1'm':1'm'"
      true)))

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
  (testing "String"
    (are [x] (true? (ctu/compile-unop elm/converts-to-string elm/string x))
      "foo"))

  (testing "Long"
    (are [x] (true? (ctu/compile-unop elm/converts-to-string elm/long x))
      "1"))

  (testing "Boolean"
    (are [x] (true? (ctu/compile-unop elm/converts-to-string elm/boolean x))
      "true"))

  (testing "Integer"
    (are [x] (true? (ctu/compile-unop elm/converts-to-string elm/integer x))
      "1"))

  (testing "Decimal"
    (are [x] (true? (ctu/compile-unop elm/converts-to-string elm/decimal x))
      "1.1"))

  (testing "Quantity"
    (are [x] (true? (ctu/compile-unop elm/converts-to-string elm/quantity x))
      [1M "m"]))

  (testing "Date"
    (are [x] (true? (ctu/compile-unop elm/converts-to-string elm/date x))
      "2019-01-01"))

  (testing "DateTime"
    (are [x] (true? (ctu/compile-unop elm/converts-to-string elm/date-time x))
      "2019-01-01T01:00"))

  (testing "Time"
    (are [x] (true? (ctu/compile-unop elm/converts-to-string elm/time x))
      "01:00"))

  (testing "Ratio"
    (are [x] (true? (ctu/compile-unop elm/converts-to-string elm/ratio x))
      [[1M "m"] [1M "m"]]))

  (testing "Tuple"
    (are [x] (false? (c/compile {} (elm/converts-to-string (elm/tuple x))))
      {"foo" #elm/integer "1"}))

  (testing "Dynamic"
    (are [x] (true? (ctu/dynamic-compile-eval (elm/converts-to-string x)))
      #elm/parameter-ref "A"))

  (ctu/testing-unary-null elm/converts-to-string)

  (ctu/testing-unary-op elm/converts-to-string)

  (ctu/testing-optimize elm/converts-to-string
    (testing "String"
      #ctu/optimize-to "foo"
      true)))

;; 22.16. ConvertsToTime
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
(deftest compile-converts-to-time-test
  (let [compile (partial ctu/compile-unop elm/converts-to-time)
        eval #(core/-eval % {:now ctu/now} nil nil)]
    (testing "String"
      (testing "expression is dynamic"
        (is (not (core/static? (compile elm/string "")))))

      (are [x] (true? (eval (compile elm/string x)))
        "12:54:30"
        "12:54:30.010")

      (are [x] (false? (eval (compile elm/string x)))
        "aaaa"
        "24:54:00"
        "23:60:00"
        "14-30-00.0"))

    (testing "Time"
      (testing "expression is dynamic"
        (is (not (core/static? (compile elm/time "12:54")))))

      (are [x] (true? (eval (compile elm/time x)))
        "12:54"
        "12:54:00"
        "12:54:30.010"))

    (testing "DateTime"
      (testing "expression is dynamic"
        (is (not (core/static? (compile elm/string "2020-03-08T12:54:00")))))

      (are [x] (true? (eval (compile elm/date-time x)))
        "2020-03-08T12:54:00"
        "2020-03-08T12:54:30.010"))

    (testing "Dynamic"
      (are [x] (true? (ctu/dynamic-compile-eval (elm/converts-to-time x)))
        #elm/parameter-ref "12:54:00"
        #elm/parameter-ref "2020-01-02T03:04:05.006Z")))

  (ctu/testing-unary-null elm/converts-to-time)

  (ctu/testing-unary-op elm/converts-to-time))

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
    (are [x res] (= res (c/compile {} (elm/descendents x)))
      (ctu/code "system-134534" "code-134551")
      ["code-134551" nil "system-134534" nil]))

  ;; TODO: other types

  (ctu/testing-unary-null elm/descendents)

  (ctu/testing-unary-op elm/descendents)

  (ctu/testing-optimize elm/descendents
    (testing "Code"
      #ctu/optimize-to (code/code "system-164913" "version-164919" "code-164924")
      ["code-164924" nil "system-164913" "version-164919"])))

;; 22.18. Is
;;
;; The Is operator allows the type of a result to be tested. The language must
;; support the ability to test against any type. If the run-time type of the
;; argument is of the type being tested, the result of the operator is true;
;; otherwise, the result is false.
(deftest compile-is-test
  (testing "FHIR types"
    (are [elm resource] (true? (core/-eval (c/compile {} elm) {} nil {"R" resource}))
      #elm/is ["{http://hl7.org/fhir}boolean"
               #elm/scope-property ["R" "deceased"]]
      {:fhir/type :fhir/Patient :id "0" :deceased #fhir/boolean true}

      #elm/is ["{http://hl7.org/fhir}integer"
               #elm/scope-property ["R" "value"]]
      {:fhir/type :fhir/Observation :value #fhir/integer 1}

      #elm/is ["{http://hl7.org/fhir}decimal"
               #elm/scope-property ["R" "duration"]]
      {:fhir/type :fhir/Media :duration #fhir/decimal 1.1M}

      #elm/is ["{http://hl7.org/fhir}string"
               #elm/scope-property ["R" "name"]]
      {:fhir/type :fhir/Account :name #fhir/string "a"}

      #elm/is ["{http://hl7.org/fhir}uri"
               #elm/scope-property ["R" "url"]]
      {:fhir/type :fhir/Measure :url #fhir/uri "a"}

      #elm/is ["{http://hl7.org/fhir}url"
               #elm/scope-property ["R" "address"]]
      {:fhir/type :fhir/Endpoint :address #fhir/url "a"}

      #elm/is ["{http://hl7.org/fhir}dateTime"
               #elm/scope-property ["R" "value"]]
      {:fhir/type :fhir/Observation :value #fhir/dateTime #system/date-time "2019-09-04"})

    (are [elm resource] (false? (core/-eval (c/compile {} elm) {} nil {"R" resource}))
      #elm/is ["{http://hl7.org/fhir}boolean"
               #elm/scope-property ["R" "deceased"]]
      {:fhir/type :fhir/Patient :id "0" :deceased #fhir/string "foo"}

      #elm/is ["{http://hl7.org/fhir}integer"
               #elm/scope-property ["R" "value"]]
      {:fhir/type :fhir/Observation :value #fhir/boolean true}

      #elm/is ["{http://hl7.org/fhir}decimal"
               #elm/scope-property ["R" "duration"]]
      {:fhir/type :fhir/Media :duration #fhir/uri "a"}

      #elm/is ["{http://hl7.org/fhir}string"
               #elm/scope-property ["R" "name"]]
      {:fhir/type :fhir/Account :name #fhir/integer 1}

      #elm/is ["{http://hl7.org/fhir}uri"
               #elm/scope-property ["R" "url"]]
      {:fhir/type :fhir/Measure :url #fhir/decimal 1.1M}

      #elm/is ["{http://hl7.org/fhir}url"
               #elm/scope-property ["R" "address"]]
      {:fhir/type :fhir/Endpoint :address #fhir/dateTime #system/date-time "2019-09-04"}

      #elm/is ["{http://hl7.org/fhir}dateTime"
               #elm/scope-property ["R" "value"]]
      {:fhir/type :fhir/Observation :value #fhir/url "a"}

      #elm/is ["{http://hl7.org/fhir}Quantity"
               #elm/scope-property ["R" "value"]]
      {:fhir/type :fhir/Observation :value #fhir/dateTime #system/date-time "2019-09-04"}))

  (testing "ELM types"
    (are [elm] (true? (core/-eval (c/compile {} elm) {} nil nil))
      #elm/is ["{urn:hl7-org:elm-types:r1}Boolean" #elm/boolean "true"]

      #elm/is ["{urn:hl7-org:elm-types:r1}Integer" #elm/integer "1"]

      #elm/is ["{urn:hl7-org:elm-types:r1}Long" #elm/long "1"]

      #elm/is ["{urn:hl7-org:elm-types:r1}Decimal" #elm/decimal "-1.1"]

      #elm/is ["{urn:hl7-org:elm-types:r1}Quantity" #elm/quantity [10 "m"]]

      #elm/is ["{urn:hl7-org:elm-types:r1}String" #elm/string "foo"]

      #elm/is ["{urn:hl7-org:elm-types:r1}Date" #elm/date "2020-03-08"]

      #elm/is ["{urn:hl7-org:elm-types:r1}DateTime" #elm/date-time"2019-09-04"])

    (are [elm] (false? (core/-eval (c/compile {} elm) {} nil nil))
      #elm/is ["{urn:hl7-org:elm-types:r1}Boolean" #elm/integer "1"]
      #elm/is ["{urn:hl7-org:elm-types:r1}Boolean" {:type "Null"}]

      #elm/is ["{urn:hl7-org:elm-types:r1}Integer" #elm/boolean "true"]
      #elm/is ["{urn:hl7-org:elm-types:r1}Integer" {:type "Null"}]

      #elm/is ["{urn:hl7-org:elm-types:r1}Long" #elm/string "foo"]
      #elm/is ["{urn:hl7-org:elm-types:r1}Long" {:type "Null"}]

      #elm/is ["{urn:hl7-org:elm-types:r1}Decimal" #elm/integer "1"]
      #elm/is ["{urn:hl7-org:elm-types:r1}Decimal" {:type "Null"}]

      #elm/is ["{urn:hl7-org:elm-types:r1}Quantity" #elm/long "1"]
      #elm/is ["{urn:hl7-org:elm-types:r1}Quantity" {:type "Null"}]

      #elm/is ["{urn:hl7-org:elm-types:r1}String" #elm/decimal "-1.1"]
      #elm/is ["{urn:hl7-org:elm-types:r1}String" {:type "Null"}]

      #elm/is ["{urn:hl7-org:elm-types:r1}Date" #elm/date-time"2020-03-08"]
      #elm/is ["{urn:hl7-org:elm-types:r1}Date" {:type "Null"}]

      #elm/is ["{urn:hl7-org:elm-types:r1}DateTime" #elm/string "2019-09-04"]
      #elm/is ["{urn:hl7-org:elm-types:r1}DateTime" {:type "Null"}]))

  (let [expr (ctu/dynamic-compile #elm/is["{urn:hl7-org:elm-types:r1}Integer"
                                          #elm/parameter-ref "x"])]

    (testing "expression is dynamic"
      (is (false? (core/-static expr))))

    (ctu/testing-constant-attach-cache expr)

    (testing "patient count"
      (is (nil? (core/-patient-count expr))))

    (testing "resolve expression references"
      (let [elm #elm/is["{urn:hl7-org:elm-types:r1}Integer"
                        #elm/expression-ref "x"]
            expr-def {:type "ExpressionDef" :name "x" :expression "y"
                      :context "Patient"}
            ctx {:library {:statements {:def [expr-def]}}}
            expr (c/resolve-refs (c/compile ctx elm) {"x" expr-def})]
        (has-form expr '(is elm/integer "y"))))

    (testing "resolve parameters"
      (let [expr (c/resolve-params expr {"x" "y"})]
        (has-form expr '(is elm/integer "y")))))

  (ctu/testing-equals-hash-code #elm/is["{urn:hl7-org:elm-types:r1}Integer"
                                        #elm/parameter-ref "x"])

  (testing "form"
    (are [elm form] (= form (c/form (c/compile {} elm)))
      #elm/is ["{urn:hl7-org:elm-types:r1}Integer" {:type "Null"}]
      '(is elm/integer nil)

      #elm/is ["{urn:hl7-org:elm-types:r1}Integer" #elm/integer "1"]
      '(is elm/integer 1)

      #elm/is ["{http://hl7.org/fhir}dateTime"
               #elm/scope-property ["R" "value"]]
      '(is fhir/dateTime (:value R))

      {:type "Is"
       :isTypeSpecifier
       {:type "ListTypeSpecifier"
        :elementType
        {:type "NamedTypeSpecifier"
         :name "{http://hl7.org/fhir}Quantity"}}
       :operand #elm/integer "1"}
      '(is (list fhir/Quantity) 1))))

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
  (testing "Static"
    (testing "String"
      (are [x] (true? (ctu/compile-unop elm/to-boolean elm/string x))
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

      (are [x] (false? (ctu/compile-unop elm/to-boolean elm/string x))
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

      (are [x] (nil? (ctu/compile-unop elm/to-boolean elm/string x))
        "foo"
        "bar"
        ""))

    (testing "Integer"
      (is (true? (ctu/compile-unop elm/to-boolean elm/integer "1")))

      (is (false? (ctu/compile-unop elm/to-boolean elm/integer "0")))

      (are [x] (nil? (ctu/compile-unop elm/to-boolean elm/integer x))
        "2"
        "-1"))

    (testing "Long"
      (is (true? (ctu/compile-unop elm/to-boolean elm/long "1")))

      (is (false? (ctu/compile-unop elm/to-boolean elm/long "0")))

      (are [x] (nil? (ctu/compile-unop elm/to-boolean elm/long x))
        "2"
        "-1"))

    (testing "Decimal"
      (are [x] (true? (ctu/compile-unop elm/to-boolean elm/decimal x))
        "1"
        "1.0"
        "1.00"
        "1.00000000")

      (are [x] (false? (ctu/compile-unop elm/to-boolean elm/decimal x))
        "0"
        "0.0"
        "0.00"
        "0.00000000")

      (are [x] (nil? (ctu/compile-unop elm/to-boolean elm/decimal x))
        "0.1"
        "-1.0"
        "2.0"
        "1.1"
        "0.9"))

    (testing "Boolean"
      (is (true? (ctu/compile-unop elm/to-boolean elm/boolean "true")))

      (is (false? (ctu/compile-unop elm/to-boolean elm/boolean "false")))))

  (ctu/testing-unary-null elm/to-boolean)

  (ctu/testing-unary-op elm/to-boolean)

  (ctu/testing-optimize elm/to-boolean
    (testing "String - true"
      #ctu/optimize-to "true"
      true)

    (testing "String - false"
      #ctu/optimize-to "false"
      false)))

;; 22.20. ToChars
;;
;; The ToChars operator takes a string and returns a list with one string for
;; each character in the input, in the order in which they appear in the
;; string.
;;
;; If the argument is null, the result is null.
(deftest compile-to-chars-test
  (testing "String"
    (are [x res] (= res (ctu/compile-unop elm/to-chars elm/string x))
      "A" ["A"]
      "ab" ["a" "b"]
      "" []))

  (testing "Integer"
    (are [x] (nil? (ctu/compile-unop elm/to-chars elm/integer x))
      "1"))

  (testing "Dynamic"
    (are [x res] (= res (ctu/dynamic-compile-eval (elm/to-chars x)))
      #elm/parameter-ref "A" ["A"]
      #elm/parameter-ref "ab" ["a" "b"]
      #elm/parameter-ref "empty-string" []))

  (ctu/testing-unary-null elm/to-chars)

  (ctu/testing-unary-op elm/to-chars)

  (ctu/testing-optimize elm/to-chars
    (testing "String"
      #ctu/optimize-to "ab"
      ["a" "b"])))

;; 22.21. ToConcept
;;
;; The ToConcept operator converts a value of type Code to a Concept value with
;; the given Code as its primary and only Code. If the Code has a display
;; value, the resulting Concept will have the same display value.
;;
;; If the input is a list of Codes, the resulting Concept will have all the
;; input Codes, and will not have a display value.
;;
;; If the argument is null, the result is null.
(deftest compile-to-concept-test
  (testing "Code"
    (are [x res] (= res (core/-eval (c/compile {} (elm/to-concept x))
                                    {:now ctu/now} nil nil))

      (ctu/code "system-134534" "code-134551")
      (concept/concept [(code/code "system-134534" nil "code-134551")])

      (elm/list [(ctu/code "system-134534" "code-134551")
                 (ctu/code "system-134535" "code-134552")])
      (concept/concept [(code/code "system-134534" nil "code-134551")
                        (code/code "system-134535" nil "code-134552")])))

  (ctu/testing-unary-null elm/to-concept)

  (ctu/testing-unary-op elm/to-concept)

  (ctu/testing-optimize elm/to-concept
    (testing "String"
      #ctu/optimize-to (code/code "system-134534" "version-171346" "code-134551")
      '(concept (code "system-134534" "version-171346" "code-134551")))))

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
  (let [compile (partial ctu/compile-unop elm/to-date)
        eval #(core/-eval % {:now ctu/now} nil nil)]
    (testing "String"
      (testing "expression is dynamic"
        (is (not (core/static? (compile elm/string "")))))

      (are [x res] (= res (eval (compile elm/string x)))
        "2019" #system/date"2019"
        "2019-01" #system/date"2019-01"
        "2019-01-01" #system/date"2019-01-01"

        "aaaa" nil
        "2019-13" nil
        "2019-02-29" nil))

    (testing "Date"
      (testing "Static"
        (testing "expression is static"
          (is (core/static? (compile elm/date "2023"))))

        (are [x res] (= res (compile elm/date x))
          "2020" #system/date"2020"
          "2020-03" #system/date"2020-03"
          "2020-03-08" #system/date"2020-03-08")))

    (testing "DateTime"
      (testing "expression is dynamic"
        (is (not (core/static? (compile elm/string "2019")))))

      (are [x res] (= res (eval (compile elm/date-time x)))
        "2019" #system/date"2019"
        "2019-01" #system/date"2019-01"
        "2019-01-01" #system/date"2019-01-01"
        "2019-01-01T12:13" #system/date"2019-01-01"
        "2019-01-01T12:13:14" #system/date"2019-01-01"
        "2019-01-01T12:13:14.000-01:00" #system/date"2019-01-01")

      (testing "Dynamic"
        (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
              elm #elm/to-date #elm/parameter-ref "x"
              expr (c/compile compile-ctx elm)
              eval-ctx (fn [x] {:now ctu/now :parameters {"x" x}})]
          (are [date-time date] (= date (core/-eval expr (eval-ctx date-time) nil nil))
            #system/date-time"2023" #system/date"2023"
            #system/date-time"2023-05" #system/date"2023-05"
            #system/date-time"2023-05-07" #system/date"2023-05-07"
            #system/date-time"2023-05-07T16" #system/date"2023-05-07"
            #system/date-time"2023-05-07T16:07" #system/date"2023-05-07"
            #system/date-time"2023-05-07T16:07:00" #system/date"2023-05-07"
            #system/date-time"2023-05-07T16:07:00+02:00" #system/date"2023-05-07")))))

  (ctu/testing-unary-null elm/to-date)

  (ctu/testing-unary-op elm/to-date))

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
  (let [compile (partial ctu/compile-unop elm/to-date-time)
        eval #(core/-eval % {:now ctu/now} nil nil)]
    (testing "String"
      (testing "expression is dynamic"
        (is (not (core/static? (compile elm/string "")))))

      (are [x res] (= res (eval (compile elm/string x)))
        "2020" #system/date-time"2020"
        "2020-03" #system/date-time"2020-03"
        "2020-03-08" #system/date-time"2020-03-08"
        "2020-03-08T12:54:00" (system/date-time 2020 3 8 12 54)
        "2020-03-08T12:54:00+00:00" (system/date-time 2020 3 8 12 54)
        "2020-03-08T12:54:00+01:00" (system/date-time 2020 3 8 11 54)

        "aaaa" nil
        "2019-13" nil
        "2019-02-29" nil))

    (testing "Date"
      (testing "Static"
        (testing "expression is static"
          (is (core/static? (compile elm/date "2023"))))

        (are [x res] (= res (compile elm/date x))
          "2020" #system/date-time"2020"
          "2020-03" #system/date-time"2020-03"
          "2020-03-08" #system/date-time"2020-03-08")))

    (testing "DateTime"
      (are [x res] (= res (eval (compile elm/date-time x)))
        "2020" #system/date-time"2020"
        "2020-03" #system/date-time"2020-03"
        "2020-03-08" #system/date-time"2020-03-08"
        "2020-03-08T12:13" #system/date-time"2020-03-08T12:13")))

  (ctu/testing-unary-null elm/to-date-time)

  (ctu/testing-unary-op elm/to-date-time))

;; 22.24. ToDecimal
;;
;; The ToDecimal operator converts the value of its argument to a Decimal
;; value. The operator accepts strings using the following format:
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
;; Note that the Decimal value returned by this operator will be limited in
;; precision and scale to the maximum precision and scale representable by the
;; implementation (at least 28 digits of precision, and 8 digits of scale).
;;
;; If the input string is not formatted correctly, or cannot be interpreted as
;; a valid Decimal value, the result is null.
;;
;; If the input is Boolean, true will result in 1.0, false will result in 0.0.
;;
;; If the argument is null, the result is null.
(deftest compile-to-decimal-test
  (testing "String"
    (are [x res] (= res (ctu/compile-unop elm/to-decimal elm/string x))
      (str decimal/min) decimal/min
      "-1.1" -1.1M
      "-1" -1M
      "0" 0M
      "1" 1M
      (str decimal/max) decimal/max

      (str (- decimal/min 1e-8M)) nil
      (str (+ decimal/max 1e-8M)) nil
      "a" nil))

  (testing "Boolean"
    (are [x res] (= res (ctu/compile-unop elm/to-decimal elm/boolean x))
      "true" 1.0
      "false" 0.0))

  (ctu/testing-unary-null elm/to-decimal)

  (ctu/testing-unary-op elm/to-decimal)

  (ctu/testing-optimize elm/to-decimal
    (testing "String"
      #ctu/optimize-to "0"
      0M)))

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
    (are [x res] (= res (ctu/compile-unop elm/to-integer elm/string x))
      (str Integer/MIN_VALUE) Integer/MIN_VALUE
      "-1" -1
      "0" 0
      "1" 1
      (str Integer/MAX_VALUE) Integer/MAX_VALUE

      (str (dec Integer/MIN_VALUE)) nil
      (str (inc Integer/MAX_VALUE)) nil
      "a" nil))

  (testing "Boolean"
    (are [x res] (= res (ctu/compile-unop elm/to-integer elm/boolean x))
      "true" 1
      "false" 0))

  (ctu/testing-unary-null elm/to-integer)

  (ctu/testing-unary-op elm/to-integer)

  (ctu/testing-optimize elm/to-integer
    (testing "String"
      #ctu/optimize-to "0"
      0)))

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
  (testing "Static"
    (testing "Boolean"
      (are [x res] (= res (ctu/compile-unop elm/to-list elm/boolean x))
        "false" [false]))

    (testing "Integer"
      (are [x res] (= res (ctu/compile-unop elm/to-list elm/integer x))
        "1" [1]))

    (testing "Null"
      (is (= [] (c/compile {} #elm/to-list{:type "Null"})))))

  (testing "Dynamic"
    (are [x res] (= res (ctu/dynamic-compile-eval (elm/to-list x)))
      #elm/parameter-ref "nil" []
      #elm/parameter-ref "a" ["a"]))

  (ctu/testing-unary-op elm/to-list)

  (ctu/testing-optimize elm/to-list
    (testing "String"
      #ctu/optimize-to "a"
      ["a"])))

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
    (are [x res] (= res (ctu/compile-unop elm/to-long elm/string x))
      (str Long/MIN_VALUE) Long/MIN_VALUE
      "-1" -1
      "0" 0
      "1" 1
      (str Long/MAX_VALUE) Long/MAX_VALUE

      (str (dec (bigint Long/MIN_VALUE))) nil
      (str (inc (bigint Long/MAX_VALUE))) nil

      "a" nil))

  (testing "Boolean"
    (are [x res] (= res (ctu/compile-unop elm/to-long elm/boolean x))
      "true" 1
      "false" 0))

  (ctu/testing-unary-null elm/to-long)

  (ctu/testing-unary-op elm/to-long)

  (ctu/testing-optimize elm/to-long
    (testing "String"
      #ctu/optimize-to "0"
      0)))

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
    (are [x res] (p/equal res (ctu/compile-unop elm/to-quantity elm/string x))
      "-1" (quantity -1 "1")
      "1" (quantity 1 "1")

      "1'm'" (quantity 1 "m")
      "1 'm'" (quantity 1 "m")
      "1  'm'" (quantity 1 "m")

      "10 'm'" (quantity 10 "m")

      "1.1 'm'" (quantity 1.1M "m"))

    (are [x] (nil? (ctu/compile-unop elm/to-quantity elm/string x))
      (str (- decimal/min 1e-8M))
      (str (+ decimal/max 1e-8M))
      (str (- decimal/min 1e-8M) "'m'")
      (str (+ decimal/max 1e-8M) "'m'")
      ""
      "a"))

  (testing "Integer"
    (are [x res] (= res (ctu/compile-unop elm/to-quantity elm/integer x))
      "1" (quantity 1 "1")))

  (testing "Decimal"
    (are [x res] (p/equal res (ctu/compile-unop elm/to-quantity elm/decimal x))
      "1" (quantity 1 "1")
      "1.1" (quantity 1.1M "1")))

  (testing "Ratio"
    (are [x res] (p/equal res (ctu/compile-unop elm/to-quantity elm/ratio x))
      [[1] [1]] (quantity 1 "1")
      [[-1] [1]] (quantity -1 "1")

      [[1 "s"] [1 "s"]] (quantity 1 "1")
      [[1 "s"] [2 "s"]] (quantity 2 "1")

      [[1 "m"] [1 "s"]] (quantity 1 "s/m")
      [[1 "s"] [1 "m"]] (quantity 1 "m/s")
      [[100 "cm"] [1 "m"]] (quantity 1 "1")))

  (ctu/testing-unary-null elm/to-quantity)

  (ctu/testing-unary-op elm/to-quantity)

  (ctu/testing-optimize elm/to-quantity
    (testing "String"
      #ctu/optimize-to "1'm'"
      '(quantity 1M "m"))))

;; 22.29. ToRatio
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
(deftest compile-to-ratio-test
  (testing "String"
    (are [x res] (p/equal res (ctu/compile-unop elm/to-ratio elm/string x))
      "-1:-1" (ratio (quantity -1 "1") (quantity -1 "1"))
      "1:1" (ratio (quantity 1 "1") (quantity 1 "1"))
      "1:100" (ratio (quantity 1 "1") (quantity 100 "1"))
      "100:1" (ratio (quantity 100 "1") (quantity 1 "1"))

      "1'm':1'm'" (ratio (quantity 1 "m") (quantity 1 "m"))
      "1 'm':1 'm'" (ratio (quantity 1 "m") (quantity 1 "m"))
      "1  'm':1  'm'" (ratio (quantity 1 "m") (quantity 1 "m"))

      "2'm':1'm'" (ratio (quantity 2 "m") (quantity 1 "m"))
      "1'm':2'm'" (ratio (quantity 1 "m") (quantity 2 "m"))

      "1'cm':1'm'" (ratio (quantity 1 "cm") (quantity 1 "m"))
      "1'm':1'cm'" (ratio (quantity 1 "m") (quantity 1 "cm"))

      "10 'm':10 'm'" (ratio (quantity 10 "m") (quantity 10 "m"))

      "1.1 'm':1.1 'm'" (ratio (quantity 1.1M "m") (quantity 1.1M "m"))))

  (are [x] (nil? (ctu/compile-unop elm/to-ratio elm/string x))
    ":"
    "a"
    ""
    "1:"
    ":1"
    "1:1:1")

  (ctu/testing-unary-null elm/to-ratio)

  (ctu/testing-unary-op elm/to-ratio)

  (ctu/testing-optimize elm/to-ratio
    (testing "String"
      #ctu/optimize-to "1:2"
      '(ratio (quantity 1M "1") (quantity 2M "1")))))

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
    (are [x res] (= res (ctu/compile-unop elm/to-string elm/boolean x))
      "true" "true"
      "false" "false"))

  (testing "Integer"
    (are [x res] (= res (ctu/compile-unop elm/to-string elm/integer x))
      "-1" "-1"
      "0" "0"
      "1" "1"))

  (testing "Decimal"
    (are [x res] (= res (ctu/compile-unop elm/to-string elm/decimal x))
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
    (are [x res] (= res (ctu/compile-unop elm/to-string elm/quantity x))
      [1 "m"] "1 'm'"
      [1M "m"] "1 'm'"
      [1.1M "m"] "1.1 'm'"))

  (testing "Date"
    (are [x res] (= res (ctu/compile-unop elm/to-string elm/date x))
      "2019" "2019"
      "2019-01" "2019-01"
      "2019-01-01" "2019-01-01"))

  (testing "DateTime"
    (are [x res] (= res (ctu/compile-unop elm/to-string elm/date-time x))
      "2019" "2019"
      "2019-01" "2019-01"
      "2019-01-01" "2019-01-01"
      "2019-01-01T01:00" "2019-01-01T01:00"))

  (testing "Time"
    (are [x res] (= res (ctu/compile-unop elm/to-string elm/time x))
      "01:00" "01:00"))

  (testing "Ratio"
    (are [x res] (= res (ctu/compile-unop elm/to-string elm/ratio x))
      [[1 "m"] [1 "m"]] "1 'm':1 'm'"
      [[1 "m"] [2 "m"]] "1 'm':2 'm'"
      [[1M "m"] [1M "m"]] "1 'm':1 'm'"
      [[100M "m"] [1M "m"]] "100 'm':1 'm'"
      [[1.1M "m"] [1.1M "m"]] "1.1 'm':1.1 'm'"))

  (ctu/testing-unary-null elm/to-string)

  (ctu/testing-unary-op elm/to-string)

  (ctu/testing-optimize elm/to-string
    (testing "Boolean"
      #ctu/optimize-to true
      "true")))

;; 22.31. ToTime
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
(deftest compile-to-time-test
  (let [eval #(core/-eval % {:now ctu/now} nil nil)]
    (testing "String"
      (are [x res] (= res (eval (ctu/compile-unop elm/to-time elm/string x)))
        "12:54" #system/time"12:54:00"
        "12:54:30" #system/time"12:54:30"
        "12:54:30.010" #system/time"12:54:30.010"

        "aaaa" nil
        "24:54:00" nil
        "23:60:00" nil
        "14-30-00.0" nil))

    (testing "Time"
      (are [x res] (= res (eval (ctu/compile-unop elm/to-time elm/time x)))
        "12:54" #system/time"12:54:00"
        "12:54:00" #system/time"12:54:00"
        "12:54:30.010" #system/time"12:54:30.010"))

    (testing "DateTime"
      (are [x res] (= res (eval (ctu/compile-unop elm/to-time elm/date-time x)))
        "2020-03-08T12:54:00" #system/time"12:54:00"
        "2020-03-08T12:54:30.010" #system/time"12:54:30.010"))

    (testing "Dynamic"
      (are [x res] (= res (ctu/dynamic-compile-eval (elm/to-time x)))
        #elm/parameter-ref "12:54:00" #system/time"12:54:00"
        #elm/parameter-ref "2020-01-02T03:04:05.006Z" #system/time"03:04:05.006")))

  (ctu/testing-unary-null elm/to-time)

  (ctu/testing-unary-op elm/to-time))
