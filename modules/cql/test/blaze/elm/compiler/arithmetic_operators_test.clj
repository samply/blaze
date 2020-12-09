(ns blaze.elm.compiler.arithmetic-operators-test
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.date-time :as date-time]
    [blaze.elm.decimal :as decimal]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [blaze.elm.protocols :as p]
    [blaze.elm.quantity :as quantity]
    [blaze.fhir.spec.type.system :as system]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [cognitect.anomalies :as anom])
  (:import
    [javax.measure UnconvertibleException]))


(st/instrument)
(tu/instrument-compile)


(defn fixture [f]
  (st/instrument)
  (tu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; 16. Arithmetic Operators

;; 16.1. Abs
;;
;; The Abs operator returns the absolute value of its argument.
;;
;; When taking the absolute value of a quantity, the unit is unchanged.
;;
;; If the argument is null, the result is null.
;;
;; The Abs operator is defined for the Integer, Decimal, and Quantity types.
(deftest compile-abs-test
  (testing "Static"
    (testing "Integer"
      (are [x res] (= res (tu/compile-unop elm/abs elm/integer x))
        "-1" 1
        "0" 0
        "1" 1))

    (testing "Decimal"
      (are [x res] (= res (tu/compile-unop elm/abs elm/decimal x))
        "-1" 1M
        "0" 0M
        "1" 1M))

    (testing "Quantity"
      (are [x res] (= res (tu/compile-unop elm/abs elm/quantity x))
        [-1] (quantity/quantity 1 "1")
        [0] (quantity/quantity 0 "1")
        [1] (quantity/quantity 1 "1")

        [-1M] (quantity/quantity 1M "1")
        [0M] (quantity/quantity 0M "1")
        [1M] (quantity/quantity 1M "1")

        [-1 "m"] (quantity/quantity 1 "m")
        [0 "m"] (quantity/quantity 0 "m")
        [1 "m"] (quantity/quantity 1 "m")

        [-1M "m"] (quantity/quantity 1M "m")
        [0M "m"] (quantity/quantity 0M "m")
        [1M "m"] (quantity/quantity 1M "m"))))

  (tu/testing-unary-null elm/abs))


;; 16.2. Add
;;
;; The Add operator performs numeric addition of its arguments.
;;
;; When adding quantities, the dimensions of each quantity must be the same, but
;; not necessarily the unit. For example, units of 'cm' and 'm' can be added,
;; but units of 'cm2' and 'cm' cannot. The unit of the result will be the most
;; granular unit of either input. Attempting to operate on quantities with
;; invalid units will result in a run-time error.
;;
;; The Add operator is defined for the Integer, Decimal, and Quantity types. In
;; addition, a time-valued Quantity can be added to a Date, DateTime or Time
;; using this operator.
;;
;; For Date, DateTime, and Time values, the operator returns the value of the
;; first argument, incremented by the time-valued quantity, respecting variable
;; length periods for calendar years and months.
;;
;; For Date values, the quantity unit must be one of years, months, weeks, or
;; days.
;;
;; For DateTime values, the quantity unit must be one of years, months, weeks,
;; days, hours, minutes, seconds, or milliseconds.
;;
;; For Time values, the quantity unit must be one of hours, minutes, seconds,
;; or milliseconds.
;;
;; Note that as with any date and time operations, temporal units may be
;; specified with either singular, plural, or UCUM units.
;;
;; The operation is performed by converting the time-based quantity to the
;; highest specified granularity in the first argument (truncating any resulting
;; decimal portion) and then adding it to the first argument.
;;
;; If either argument is null, the result is null.
;;
;; If the result of the addition cannot be represented (i.e. arithmetic
;; overflow), the result is null.
(deftest compile-add-test
  (testing "Integer"
    (testing "Static"
      (are [x y res] (= res (tu/compile-binop elm/add elm/integer x y))
        "-1" "-1" -2
        "-1" "0" -1
        "-1" "1" 0
        "1" "0" 1
        "1" "1" 2))

    (tu/testing-binary-null elm/add #elm/integer"1"))

  (testing "Adding zero integer to any integer or decimal doesn't change it"
    (tu/satisfies-prop 100
      (prop/for-all [operand (s/gen (s/or :i :elm/integer :d :elm/decimal))]
        (let [elm (elm/equal [(elm/add [operand #elm/integer"0"]) operand])]
          (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  (testing "Adding zero decimal to any decimal doesn't change it"
    (tu/satisfies-prop 100
      (prop/for-all [operand (s/gen :elm/decimal)]
        (let [elm (elm/equal [(elm/add [operand #elm/decimal"0"]) operand])]
          (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  (testing "Adding identical integers equals multiplying the same integer by two"
    (tu/satisfies-prop 100
      (prop/for-all [integer (s/gen :elm/integer)]
        (let [elm (elm/equivalent [(elm/add [integer integer])
                                   (elm/multiply [integer #elm/integer"2"])])]
          (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  (testing "Decimal"
    (testing "Static"
      (are [x y res] (= res (tu/compile-binop elm/add elm/decimal x y))
        "-1.1" "-1.1" -2.2M
        "-1.1" "0" -1.1M
        "-1.1" "1.1" 0M
        "1.1" "0" 1.1M
        "1.1" "1.1" 2.2M)

      (tu/testing-binary-null elm/add #elm/decimal"1.1"))

    (testing "Mix with integer"
      (are [x y res] (= res (c/compile {} (elm/add [x y])))
        #elm/decimal"1.1" #elm/integer"1" 2.1M
        #elm/integer"1" #elm/decimal"1.1" 2.1M)

      (tu/testing-binary-null elm/add #elm/integer"1" #elm/decimal"1.1")
      (tu/testing-binary-null elm/add #elm/decimal"1.1" #elm/integer"1"))

    (testing "Trailing zeros are preserved"
      (are [x y res] (= res (str (core/-eval (c/compile {} (elm/add [x y])) {} nil nil)))
        #elm/decimal"1.23" #elm/decimal"1.27" "2.50"))

    (testing "Arithmetic overflow results in nil"
      (are [x y] (nil? (core/-eval (c/compile {} (elm/add [x y])) {} nil nil))
        #elm/decimal"99999999999999999999" #elm/decimal"1"
        #elm/decimal"99999999999999999999.99999999" #elm/decimal"1")))

  (testing "Adding identical decimals equals multiplying the same decimal by two"
    (tu/satisfies-prop 100
      (prop/for-all [decimal (s/gen :elm/decimal)]
        (let [elm (elm/equal [(elm/add [decimal decimal])
                              (elm/multiply [decimal #elm/integer"2"])])]
          (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  (testing "Adding identical decimals and dividing by two results in the same decimal"
    (tu/satisfies-prop 100
      (prop/for-all [decimal (s/gen :elm/decimal)]
        (let [elm (elm/equal [(elm/divide [(elm/add [decimal decimal])
                                           #elm/integer"2"])
                              decimal])]
          (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  (testing "Time-based quantity"
    (are [x y res] (= res (tu/compile-binop elm/add elm/quantity x y))
      [1 "year"] [1 "year"] (date-time/period 2 0 0)
      [1 "year"] [1 "month"] (date-time/period 1 1 0)
      [1 "year"] [1 "day"] (date-time/period 1 0 (* 24 3600 1000))

      [1 "day"] [1 "day"] (date-time/period 0 0 (* 2 24 3600 1000))
      [1 "day"] [1 "hour"] (date-time/period 0 0 (* 25 3600 1000))

      [1 "year"] [1.1M "year"] (date-time/period 2.1M 0 0)
      [1 "year"] [13.1M "month"] (date-time/period 2 1.1M 0)))

  (testing "UCUM quantity"
    (are [x y res] (p/equal res (tu/compile-binop elm/add elm/quantity x y))
      [1 "m"] [1 "m"] (quantity/quantity 2 "m")
      [1 "m"] [1 "cm"] (quantity/quantity 1.01M "m")))

  (testing "Incompatible UCUM Quantity Subtractions"
    (are [x y] (thrown? UnconvertibleException (tu/compile-binop elm/add elm/quantity x y))
      [1 "cm2"] [1 "cm"]
      [1 "m"] [1 "s"]))

  (testing "Adding identical quantities equals multiplying the same quantity with two"
    (tu/satisfies-prop
      100
      (prop/for-all [quantity (gen/such-that :value (s/gen :elm/quantity) 100)]
        (let [elm (elm/equal [(elm/add [quantity quantity])
                              (elm/multiply [quantity #elm/integer"2"])])]
          (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  (testing "Adding identical quantities and dividing by two results in the same quantity"
    (tu/satisfies-prop
      100
      (prop/for-all [quantity (gen/such-that :value (s/gen :elm/quantity) 100)]
        (let [elm (elm/equal [(elm/divide [(elm/add [quantity quantity])
                                           #elm/integer"2"])
                              quantity])]
          (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  (testing "Date + Quantity"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/add [x y])) {} nil nil))
      #elm/date"2019" #elm/quantity[1 "year"] (system/date 2020)
      #elm/date"2019" #elm/quantity[13 "months"] (system/date 2020)

      #elm/date"2019-01" #elm/quantity[1 "month"] (system/date 2019 2)
      #elm/date"2019-01" #elm/quantity[12 "month"] (system/date 2020 1)
      #elm/date"2019-01" #elm/quantity[13 "month"] (system/date 2020 2)
      #elm/date"2019-01" #elm/quantity[1 "year"] (system/date 2020 1)

      #elm/date"2019-01-01" #elm/quantity[1 "year"] (system/date 2020 1 1)
      #elm/date"2012-02-29" #elm/quantity[1 "year"] (system/date 2013 2 28)
      #elm/date"2019-01-01" #elm/quantity[1 "month"] (system/date 2019 2 1)
      #elm/date"2019-01-01" #elm/quantity[1 "day"] (system/date 2019 1 2)))

  (testing "Adding a positive amount of years to a year makes it greater"
    (tu/satisfies-prop 100
      (prop/for-all [year (s/gen :elm/year)
                     years (s/gen :elm/pos-years)]
        (let [elm (elm/greater [(elm/add [year years]) year])]
          (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  (testing "Adding a positive amount of years to a year-month makes it greater"
    (tu/satisfies-prop 100
      (prop/for-all [year-month (s/gen :elm/year-month)
                     years (s/gen :elm/pos-years)]
        (let [elm (elm/greater [(elm/add [year-month years]) year-month])]
          (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  (testing "Adding a positive amount of years to a date makes it greater"
    (tu/satisfies-prop 100
      (prop/for-all [date (s/gen :elm/literal-date)
                     years (s/gen :elm/pos-years)]
        (let [elm (elm/greater [(elm/add [date years]) date])]
          (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  (testing "Adding a positive amount of years to a date-time makes it greater"
    (tu/satisfies-prop 100
      (prop/for-all [date-time (s/gen :elm/literal-date-time)
                     years (s/gen :elm/pos-years)]
        (let [elm (elm/greater [(elm/add [date-time years]) date-time])]
          (true? (core/-eval (c/compile {} elm) {:now tu/now} nil nil))))))

  (testing "Adding a positive amount of months to a year-month makes it greater"
    (tu/satisfies-prop 100
      (prop/for-all [year-month (s/gen :elm/year-month)
                     months (s/gen :elm/pos-months)]
        (let [elm (elm/greater [(elm/add [year-month months]) year-month])]
          (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  (testing "Adding a positive amount of months to a date makes it greater or lets it equal because a date can be also a year and adding a small amount of months to a year doesn't change it."
    (tu/satisfies-prop 100
      (prop/for-all [date (s/gen :elm/literal-date)
                     months (s/gen :elm/pos-months)]
        (let [elm (elm/greater-or-equal [(elm/add [date months]) date])]
          (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  (testing "Adding a positive amount of months to a date-time makes it greater or lets it equal because a date-time can be also a year and adding a small amount of months to a year doesn't change it."
    (tu/satisfies-prop 100
      (prop/for-all [date-time (s/gen :elm/literal-date-time)
                     months (s/gen :elm/pos-months)]
        (let [elm (elm/greater-or-equal [(elm/add [date-time months]) date-time])]
          (true? (core/-eval (c/compile {} elm) {:now tu/now} nil nil))))))

  ;; TODO: is that right?
  (testing "Adding a positive amount of days to a year doesn't change it."
    (tu/satisfies-prop 100
      (prop/for-all [year (s/gen :elm/year)
                     days (s/gen :elm/pos-days)]
        (let [elm (elm/equal [(elm/add [year days]) year])]
          (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  ;; TODO: is that right?
  (testing "Adding a positive amount of days to a year-month doesn't change it."
    (tu/satisfies-prop 100
      (prop/for-all [year-month (s/gen :elm/year-month)
                     days (s/gen :elm/pos-days)]
        (let [elm (elm/equal [(elm/add [year-month days]) year-month])]
          (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  (testing "Adding a positive amount of days to a date makes it greater or lets it equal because a date can be also a year or year-month and adding any amount of days to a year or year-month doesn't change it."
    (tu/satisfies-prop 100
      (prop/for-all [date (s/gen :elm/literal-date)
                     days (s/gen :elm/pos-days)]
        (let [elm (elm/greater-or-equal [(elm/add [date days]) date])]
          (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  (testing "Adding a positive amount of days to a date-time makes it greater or lets it equal because a date-time can be also a year or year-month and adding any amount of days to a year or year-month doesn't change it."
    (tu/satisfies-prop 100
      (prop/for-all [date-time (s/gen :elm/literal-date-time)
                     days (s/gen :elm/pos-days)]
        (let [elm (elm/greater-or-equal [(elm/add [date-time days]) date-time])]
          (true? (core/-eval (c/compile {} elm) {:now tu/now} nil nil))))))

  (testing "DateTime + Quantity"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/add [x y])) {} nil nil))
      #elm/date-time"2019-01-01T00" #elm/quantity[1 "year"] (system/date-time 2020 1 1 0 0 0)
      #elm/date-time"2012-02-29T00" #elm/quantity[1 "year"] (system/date-time 2013 2 28 0 0 0)
      #elm/date-time"2019-01-01T00" #elm/quantity[1 "month"] (system/date-time 2019 2 1 0 0 0)
      #elm/date-time"2019-01-01T00" #elm/quantity[1 "day"] (system/date-time 2019 1 2 0 0 0)
      #elm/date-time"2019-01-01T00" #elm/quantity[1 "hour"] (system/date-time 2019 1 1 1 0 0)
      #elm/date-time"2019-01-01T00" #elm/quantity[1 "minute"] (system/date-time 2019 1 1 0 1 0)
      #elm/date-time"2019-01-01T00" #elm/quantity[1 "second"] (system/date-time 2019 1 1 0 0 1)))

  (testing "Time + Quantity"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/add [x y])) {} nil nil))
      #elm/time"00:00:00" #elm/quantity[1 "hour"] (date-time/local-time 1 0 0)
      #elm/time"00:00:00" #elm/quantity[1 "minute"] (date-time/local-time 0 1 0)
      #elm/time"00:00:00" #elm/quantity[1 "second"] (date-time/local-time 0 0 1))))


;; 16.3. Ceiling
;;
;; The Ceiling operator returns the first integer greater than or equal to the
;; argument.
;;
;; If the argument is null, the result is null.
(deftest compile-ceiling-test
  (are [x res] (= res (c/compile {} (elm/ceiling x)))
    #elm/integer"1" 1
    #elm/decimal"1.1" 2)

  (tu/testing-unary-null elm/ceiling))


;; 16.4. Divide
;;
;; The Divide operator performs numeric division of its arguments. Note that the
;; result type of Divide is Decimal, even if its arguments are of type Integer.
;; For integer division, use the truncated divide operator.
;;
;; For division operations involving quantities, the resulting quantity will
;; have the appropriate unit.
;;
;; If either argument is null, the result is null.
;;
;; If the result of the division cannot be represented, or the right argument is
;; 0, the result is null.
;;
;; The Divide operator is defined for the Decimal and Quantity types.
(deftest compile-divide-test
  (testing "Decimal"
    (testing "Static"
      (are [x y res] (= res (tu/compile-binop elm/divide elm/decimal x y))
        "1" "2" 0.5M
        "1.1" "2" 0.55M
        "10" "3" 3.33333333M

        "1" "0" nil
        "1" "0.0" nil))

    (tu/testing-binary-null elm/divide #elm/decimal"1.1"))

  (testing "Integer"
    (testing "Static"
      (are [x y res] (= res (tu/compile-binop elm/divide elm/integer x y))
        "1" "2" 0.5M
        "10" "3" 3.33333333M

        "1" "0" nil))

    (tu/testing-binary-null elm/divide #elm/integer"1"))

  (testing "Decimal/Integer"
    (testing "Static"
      (are [x y res] (= res (tu/compile-binop elm/divide elm/decimal elm/integer x y))
        "3" "2" 1.5M

        "1" "0" nil))

    (tu/testing-binary-null elm/divide #elm/decimal"1.1" #elm/integer"1"))

  (testing "Integer/Decimal"
    (testing "Static"
      (are [x y res] (= res (tu/compile-binop elm/divide elm/integer elm/decimal x y))
        "3" "2" 1.5M

        "1" "0" nil
        "1" "0.0" nil))

    (tu/testing-binary-null elm/divide #elm/integer"1" #elm/decimal"1.1"))

  (testing "Quantity"
    (testing "Static"
      (are [x y res] (p/equal res (tu/compile-binop elm/divide elm/quantity x y))
        [1 "m"] [1 "s"] (quantity/quantity 1 "m/s")
        [1M "m"] [1M "s"] (quantity/quantity 1M "m/s")

        [12 "cm2"] [3 "cm"] (quantity/quantity 4 "cm")))

    (tu/testing-binary-null elm/divide #elm/quantity[1]))

  (testing "Quantity/Integer"
    (testing "Static"
      (are [x y res] (p/equal res (tu/compile-binop elm/divide elm/quantity elm/integer x y))
        [1M "m"] "2" (quantity/quantity 0.5M "m")))

    (tu/testing-binary-null elm/divide #elm/quantity[1] #elm/integer"1"))

  (testing "Quantity/Decimal"
    (testing "Static"
      (are [x y res] (p/equal res (tu/compile-binop elm/divide elm/quantity elm/decimal x y))
        [2.5M "m"] "2.5" (quantity/quantity 1M "m")))

    (tu/testing-binary-null elm/divide #elm/quantity[1] #elm/decimal"1.1"))

  (testing "(d / d) * d = d"
    (tu/satisfies-prop 100
      (prop/for-all [decimal (s/gen :elm/non-zero-decimal)]
        (let [elm (elm/equal [(elm/multiply [(elm/divide [decimal decimal]) decimal]) decimal])]
          (true? (core/-eval (c/compile {} elm) {} nil nil)))))))


;; 16.5. Exp
;;
;; The Exp operator returns e raised to the given power.
;;
;; If the argument is null, the result is null.
(deftest compile-exp-test
  (are [x res] (= res (c/compile {} (elm/exp x)))
    #elm/integer"0" 1M
    #elm/decimal"0" 1M)

  (tu/testing-unary-null elm/exp))


;; 16.6. Floor
;;
;; The Floor operator returns the first integer less than or equal to the
;; argument.
;;
;; If the argument is null, the result is null.
(deftest compile-floor-test
  (are [x res] (= res (c/compile {} (elm/floor x)))
    #elm/integer"1" 1
    #elm/decimal"1.1" 1)

  (tu/testing-unary-null elm/floor))


;; 16.7. Log
;;
;; The Log operator computes the logarithm of its first argument, using the
;; second argument as the base.
;;
;; If either argument is null, the result is null.
(deftest compile-log-test
  (are [x base res] (= res (c/compile {} (elm/log [x base])))
    #elm/integer"16" #elm/integer"2" 4M

    #elm/decimal"100" #elm/decimal"10" 2M
    #elm/decimal"1" #elm/decimal"1" nil

    #elm/integer"0" #elm/integer"2" nil
    #elm/decimal"0" #elm/integer"2" nil)

  (tu/testing-binary-null elm/log #elm/integer"1")

  (tu/testing-binary-null elm/log #elm/decimal"1.1"))


;; 16.8. Ln
;;
;; The Ln operator computes the natural logarithm of its argument.
;;
;; If the argument is null, the result is null.
;;
;; If the result of the operation cannot be represented, the result is null.
(deftest compile-ln-test
  (are [x res] (= res (c/compile {} (elm/ln x)))
    #elm/integer"1" 0M
    #elm/integer"2" 0.69314718M
    #elm/integer"3" 1.09861229M

    #elm/decimal"1" 0M
    #elm/decimal"1.1" 0.09531018M

    #elm/integer"0" nil
    #elm/decimal"0" nil

    #elm/integer"-1" nil
    #elm/decimal"-1" nil)

  (tu/testing-unary-null elm/ln))


;; 16.9. MaxValue
;;
;; The MaxValue operator returns the maximum representable value for the given
;; type.
;;
;; The MaxValue operator is defined for the Integer, Decimal, Date, DateTime,
;; and Time types.
;;
;; For Integer, MaxValue returns the maximum signed 32-bit integer, 2^31 - 1.
;;
;; For Decimal, MaxValue returns the maximum representable Decimal value,
;; (10^28 - 1) / 10^8 (99999999999999999999.99999999).
;;
;; For Date, MaxValue returns the maximum representable Date value,
;; Date(9999, 12, 31).
;;
;; For DateTime, MaxValue returns the maximum representable DateTime value,
;; DateTime(9999, 12, 31, 23, 59, 59, 999).
;;
;; For Time, MaxValue returns the maximum representable Time value,
;; Time(23, 59, 59, 999).
;;
;; For any other type, attempting to invoke MaxValue results in an error.
(deftest compile-max-value-test
  (are [type res] (= res (c/compile {} (elm/max-value type)))
    "{urn:hl7-org:elm-types:r1}Integer" Integer/MAX_VALUE
    "{urn:hl7-org:elm-types:r1}Decimal" (/ (- 1E28M 1) 1E8M)
    "{urn:hl7-org:elm-types:r1}Date" (system/date 9999 12 31)
    "{urn:hl7-org:elm-types:r1}DateTime" (system/date-time 9999 12 31 23 59 59 999)
    "{urn:hl7-org:elm-types:r1}Time" (date-time/local-time 23 59 59 999))

  (are [type] (thrown-anom? ::anom/incorrect (c/compile {} (elm/max-value type)))
    "{urn:hl7-org:elm-types:r1}Foo"
    "{foo}Bar"))


;; 16.10. MinValue
;;
;; The MinValue operator returns the minimum representable value for the given
;; type.
;;
;; The MinValue operator is defined for the Integer, Decimal, Date, DateTime,
;; and Time types.
;;
;; For Integer, MinValue returns the minimum signed 32-bit integer, -(2^31).
;;
;; For Decimal, MinValue returns the minimum representable Decimal value,
;; (-10^28 + 1) / 10^8 (-99999999999999999999.99999999).
;;
;; For Date, MinValue returns the minimum representable Date value,
;; Date(1, 1, 1).
;;
;; For DateTime, MinValue returns the minimum representable DateTime value,
;; DateTime(1, 1, 1, 0, 0, 0, 0).
;;
;; For Time, MinValue returns the minimum representable Time value,
;; Time(0, 0, 0, 0).
;;
;; For any other type, attempting to invoke MinValue results in an error.
(deftest compile-min-value-test
  (are [type res] (= res (c/compile {} (elm/min-value type)))
    "{urn:hl7-org:elm-types:r1}Integer" Integer/MIN_VALUE
    "{urn:hl7-org:elm-types:r1}Decimal" (/ (+ -1E28M 1) 1E8M)
    "{urn:hl7-org:elm-types:r1}Date" (system/date 1 1 1)
    "{urn:hl7-org:elm-types:r1}DateTime" (system/date-time 1 1 1 0 0 0 0)
    "{urn:hl7-org:elm-types:r1}Time" (date-time/local-time 0 0 0 0))

  (are [type] (thrown-anom? ::anom/incorrect (c/compile {} (elm/min-value type)))
    "{urn:hl7-org:elm-types:r1}Foo"
    "{foo}Bar"))


;; 16.11. Modulo
;;
;; The Modulo operator computes the remainder of the division of its arguments.
;;
;; If either argument is null, the result is null.
;;
;; If the result of the modulo cannot be represented, or the right argument is
;; 0, the result is null.
;;
;; The Modulo operator is defined for the Integer and Decimal types.
(deftest compile-modulo-test
  (testing "Integer"
    (are [x div res] (= res (tu/compile-binop elm/modulo elm/integer x div))
      "1" "2" 1
      "3" "2" 1
      "5" "3" 2)

    (tu/testing-binary-null elm/modulo #elm/integer"1"))

  (testing "Decimal"
    (are [x div res] (= res (tu/compile-binop elm/modulo elm/decimal x div))
      "1" "2" 1M
      "3" "2" 1M
      "5" "3" 2M

      "2.5" "2" 0.5M)

    (tu/testing-binary-null elm/modulo #elm/decimal"1.1"))

  (testing "Mixed Integer and Decimal"
    (are [x div res] (= res (core/-eval (c/compile {} (elm/modulo [x div])) {} nil nil))
      #elm/integer"1" #elm/integer"0" nil
      #elm/decimal"1" #elm/decimal"0" nil)))


;; 16.12. Multiply
;;
;; The Multiply operator performs numeric multiplication of its arguments.
;;
;; For multiplication operations involving quantities, the resulting quantity
;; will have the appropriate unit.
;;
;; If either argument is null, the result is null.
;;
;; If the result of the operation cannot be represented, the result is null.
;;
;; The Multiply operator is defined for the Integer, Decimal and Quantity types.
(deftest compile-multiply-test
  (testing "Integer"
    (are [x y res] (= res (tu/compile-binop elm/multiply elm/integer x y))
      "1" "2" 2
      "2" "2" 4)

    (tu/testing-binary-null elm/multiply #elm/integer"1"))

  (testing "Decimal"
    (testing "Decimal"
      (are [x y res] (= res (tu/compile-binop elm/multiply elm/decimal x y))
        "1" "2" 2M
        "1.23456" "1.23456" 1.52413839M)

      (tu/testing-binary-null elm/multiply #elm/decimal"1.1"))

    (testing "Arithmetic overflow results in nil"
      (are [x y] (nil? (tu/compile-binop elm/multiply elm/decimal x y))
        "99999999999999999999" "2"
        "99999999999999999999.99999999" "2")))

  (testing "Quantity"
    (are [x y res] (p/equal res (core/-eval (c/compile {} (elm/multiply [x y])) {} nil nil))
      #elm/quantity[1 "m"] #elm/integer"2" (quantity/quantity 2 "m")
      #elm/quantity[1 "m"] #elm/quantity[2 "m"] (quantity/quantity 2 "m2"))

    (tu/testing-binary-null elm/multiply #elm/quantity[1])))


;; 16.13. Negate
;;
;; The Negate operator returns the negative of its argument.
;;
;; When negating quantities, the unit is unchanged.
;;
;; If the argument is null, the result is null.
;;
;; The Negate operator is defined for the Integer, Decimal, and Quantity types.
(deftest compile-negate-test
  (testing "Integer"
    (are [x res] (= res (c/compile {} (elm/negate (elm/integer x))))
      "1" -1))

  (testing "Decimal"
    (are [x res] (= res (c/compile {} (elm/negate (elm/decimal x))))
      "1" -1M))

  (testing "Quantity"
    (are [x res] (= res (c/compile {} (elm/negate x)))
      #elm/quantity[1] (quantity/quantity -1 "1")
      #elm/quantity[1M] (quantity/quantity -1M "1")
      #elm/quantity[1 "m"] (quantity/quantity -1 "m")
      #elm/quantity[1M "m"] (quantity/quantity -1M "m")))

  (tu/testing-unary-null elm/negate))


;; 16.14. Power
;;
;; The Power operator raises the first argument to the power given by the
;; second argument.
;;
;; When invoked with mixed Integer and Decimal arguments, the Integer argument
;; will be implicitly converted to Decimal.
;;
;; If either argument is null, the result is null.
(deftest compile-power-test
  (testing "Integer"
    (are [x y res] (= res (tu/compile-binop elm/power elm/integer x y))
      "10" "2" 100
      "2" "-2" 0.25M)

    (tu/testing-binary-null elm/power #elm/integer"1"))

  (testing "Decimal"
    (are [x y res] (= res (tu/compile-binop elm/power elm/decimal x y))
      "2.5" "2" 6.25M
      "10" "2" 100M
      "4" "0.5" 2M)

    (tu/testing-binary-null elm/power #elm/decimal"1.1"))

  (testing "Mixed"
    (are [x y res] (= res (c/compile {} (elm/power [x y])))
      #elm/decimal"2.5" #elm/integer"2" 6.25M
      #elm/decimal"10" #elm/integer"2" 100M
      #elm/decimal"10" #elm/integer"2" 100M)))


;; 16.15. Predecessor
;;
;; The Predecessor operator returns the predecessor of the argument. For
;; example, the predecessor of 2 is 1. If the argument is already the minimum
;; value for the type, a run-time error is thrown.
;;
;; The Predecessor operator is defined for the Integer, Decimal, Quantity, Date,
;; DateTime, and Time types.
;;
;; For Integer, Predecessor is equivalent to subtracting 1.
;;
;; For Decimal, Predecessor is equivalent to subtracting the minimum precision
;; value for the Decimal type, or 10^-08.
;;
;; For Date, DateTime, and Time values, Predecessor is equivalent to
;; subtracting a time-unit quantity for the lowest specified precision of the
;; value. For example, if the DateTime is fully specified, Predecessor is
;; equivalent to subtracting 1 millisecond; if the DateTime is specified to the
;; second, Predecessor is equivalent to subtracting one second, etc.
;;
;; For Quantity values, the predecessor is equivalent to subtracting 1 if the
;; quantity is an integer, and the minimum precision value for the Decimal type
;; if the quantity is a decimal. The units are unchanged.
;;
;; If the argument is null, the result is null.
;;
;; If the result of the operation cannot be represented, the result is null.
(deftest compile-predecessor-test
  (testing "Integer"
    (are [x res] (= res (c/compile {} (elm/predecessor x)))
      #elm/integer"0" -1))

  (testing "Decimal"
    (are [x res] (= res (c/compile {} (elm/predecessor x)))
      #elm/decimal"0" -1E-8M))

  (testing "Date"
    (are [x res] (= res (c/compile {} (elm/predecessor x)))
      #elm/date"2019" (system/date 2018)
      #elm/date"2019-01" (system/date 2018 12)
      #elm/date"2019-01-01" (system/date 2018 12 31)))

  (testing "DateTime"
    (are [x res] (= res (c/compile {} (elm/predecessor x)))
      #elm/date-time"2019" (system/date-time 2018)
      #elm/date-time"2019-01" (system/date-time 2018 12)
      #elm/date-time"2019-01-01" (system/date-time 2018 12 31)
      #elm/date-time"2019-01-01T00" (system/date-time 2018 12 31 23 59 59 999)))

  (testing "Time"
    (are [x res] (= res (c/compile {} (elm/predecessor x)))
      #elm/time"12:00" (date-time/local-time 11 59)))

  (testing "Quantity"
    (are [x res] (= res (c/compile {} (elm/predecessor x)))
      #elm/quantity[0 "m"] (quantity/quantity -1 "m")
      #elm/quantity[0M "m"] (quantity/quantity -1E-8M "m")))

  (tu/testing-unary-null elm/predecessor)

  (testing "throws error if the argument is already the minimum value"
    (are [x] (thrown-anom? ::anom/incorrect (c/compile {} (elm/predecessor x)))
      (elm/decimal (str decimal/min))
      #elm/date"0001"
      #elm/date"0001-01"
      #elm/date"0001-01-01"
      #elm/time"00:00:00.0"
      #elm/date-time"0001"
      #elm/date-time"0001-01"
      #elm/date-time"0001-01-01"
      #elm/date-time"0001-01-01T00:00:00.0"
      (elm/quantity [decimal/min]))))


;; 16.16. Round
;;
;; The Round operator returns the nearest integer to its argument. The semantics
;; of round are defined as a traditional round, meaning that a decimal value of
;; 0.5 or higher will round to 1.
;;
;; If the argument is null, the result is null.
;;
;; Precision determines the decimal place at which the rounding will occur. If
;; precision is not specified or null, 0 is assumed.
(deftest compile-round-test
  (with-open [node (mem-node-with [])]
    (let [context {:eval-context "Patient" :node node}]
      (testing "Without precision"
        (testing "Static"
          (are [x res] (= res (c/compile {} (elm/round [x])))
            #elm/integer"1" 1M
            #elm/decimal"1" 1M
            #elm/decimal"0.5" 1M
            #elm/decimal"0.4" 0M
            #elm/decimal"-0.4" 0M
            #elm/decimal"-0.5" -1M
            #elm/decimal"-0.6" -1M
            #elm/decimal"-1.1" -1M
            #elm/decimal"-1.5" -2M
            #elm/decimal"-1.6" -2M
            {:type "Null"} nil))

        (testing "Dynamic Null"
          (let [elm (elm/round [(elm/singleton-from tu/patient-retrieve-elm)])
                expr (c/compile context elm)]
            (is (nil? (core/-eval expr {} nil nil))))))

      (testing "With precision"
        (testing "Static"
          (are [x precision res] (= res (c/compile {} (elm/round [x precision])))
            #elm/decimal"3.14159" #elm/integer"3" 3.142M
            {:type "Null"} #elm/integer"3" nil))

        (testing "Dynamic Null"
          (let [elm (elm/round [(elm/singleton-from tu/patient-retrieve-elm) #elm/integer"3"])
                expr (c/compile context elm)]
            (is (nil? (core/-eval expr {} nil nil)))))))))


;; 16.17. Subtract
;;
;; The Subtract operator performs numeric subtraction of its arguments.
;;
;; When subtracting quantities, the dimensions of each quantity must be the same,
;; but not necessarily the unit. For example, units of 'cm' and 'm' can be
;; subtracted, but units of 'cm2' and 'cm' cannot. The unit of the result will
;; be the most granular unit of either input. Attempting to operate on
;; quantities with invalid units will result in a run-time error.
;;
;; The Subtract operator is defined for the Integer, Decimal, and Quantity types.
;; In addition, a time-valued Quantity can be subtracted from a Date, DateTime,
;; or Time using this operator.
;;
;; For Date, DateTime, Time values, the operator returns the value of the given
;; date/time, decremented by the time-valued quantity, respecting variable
;; length periods for calendar years and months.
;;
;; For Date values, the quantity unit must be one of years, months, weeks, or
;; days.
;;
;; For DateTime values, the quantity unit must be one of years, months, weeks,
;; days, hours, minutes, seconds, or milliseconds.
;;
;; For Time values, the quantity unit must be one of hours, minutes, seconds, or
;; milliseconds.
;;
;; The operation is performed by converting the time-based quantity to the
;; highest specified granularity in the date/time value (truncating any
;; resulting decimal portion) and then adding it to the date/time value.
;;
;; If either argument is null, the result is null.
(deftest compile-subtract-test
  (testing "Integer"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/subtract [x y])) {} nil nil))
      #elm/integer"-1" #elm/integer"-1" 0
      #elm/integer"-1" #elm/integer"0" -1
      #elm/integer"1" #elm/integer"1" 0
      #elm/integer"1" #elm/integer"0" 1
      #elm/integer"1" #elm/integer"-1" 2

      {:type "Null"} #elm/integer"1" nil
      #elm/integer"1" {:type "Null"} nil))

  (testing "Subtracting identical integers results in zero"
    (tu/satisfies-prop 100
      (prop/for-all [integer (s/gen :elm/integer)]
        (zero? (core/-eval (c/compile {} (elm/subtract [integer integer])) {} nil nil)))))

  (testing "Decimal"
    (testing "Decimal"
      (are [x y res] (= res (core/-eval (c/compile {} (elm/subtract [x y])) {} nil nil))
        #elm/decimal"-1" #elm/decimal"-1" 0M
        #elm/decimal"-1" #elm/decimal"0" -1M
        #elm/decimal"1" #elm/decimal"1" 0M
        #elm/decimal"1" #elm/decimal"0" 1M
        #elm/decimal"1" #elm/decimal"-1" 2M

        {:type "Null"} #elm/decimal"1.1" nil
        #elm/decimal"1.1" {:type "Null"} nil))

    (testing "Mix with integer"
      (are [x y res] (= res (core/-eval (c/compile {} (elm/subtract [x y])) {} nil nil))
        #elm/decimal"1" #elm/integer"1" 0M))

    (testing "Arithmetic overflow results in nil"
      (are [x y] (nil? (core/-eval (c/compile {} (elm/subtract [x y])) {} nil nil))
        #elm/decimal"-99999999999999999999" #elm/decimal"1"
        #elm/decimal"-99999999999999999999.99999999" #elm/decimal"1")))

  (testing "Subtracting identical decimals results in zero"
    (tu/satisfies-prop 100
      (prop/for-all [decimal (s/gen :elm/decimal)]
        (zero? (core/-eval (c/compile {} (elm/subtract [decimal decimal])) {} nil nil)))))

  (testing "Time-based quantity"
    (are [x y res] (= res (core/-eval (c/compile {} (elm/subtract [x y])) {} nil nil))
      #elm/quantity[1 "year"] #elm/quantity[1 "year"] (date-time/period 0 0 0)
      #elm/quantity[1 "year"] #elm/quantity[1 "month"] (date-time/period 0 11 0)
      #elm/quantity[1 "year"] #elm/quantity[1 "day"] (date-time/period 1 0 (- (* 24 3600 1000)))

      #elm/quantity[1 "day"] #elm/quantity[1 "day"] (date-time/period 0 0 0)
      #elm/quantity[1 "day"] #elm/quantity[1 "hour"] (date-time/period 0 0 (* 23 3600 1000))

      #elm/quantity[1 "year"] #elm/quantity[1.1M "year"] (date-time/period -0.1M 0 0)
      #elm/quantity[1 "year"] #elm/quantity[13.1M "month"] (date-time/period 0 -1.1M 0)))

  (testing "UCUM quantity"
    (are [x y res] (p/equal res (core/-eval (c/compile {} (elm/subtract [x y])) {} nil nil))
      #elm/quantity[1 "m"] #elm/quantity[1 "m"] (quantity/quantity 0 "m")
      #elm/quantity[1 "m"] #elm/quantity[1 "cm"] (quantity/quantity 0.99 "m")))

  (testing "Incompatible UCUM Quantity Subtractions"
    (are [x y] (thrown? UnconvertibleException (core/-eval (c/compile {} (elm/subtract [x y])) {} nil nil))
      #elm/quantity[1 "cm2"] #elm/quantity[1 "cm"]
      #elm/quantity[1 "m"] #elm/quantity[1 "s"]))

  (testing "Subtracting identical quantities results in zero"
    (tu/satisfies-prop
      100
      (prop/for-all [quantity (gen/such-that :value (s/gen :elm/quantity) 100)]
        ;; Can't test for zero because can't extract value from quantity
        ;; so use negate trick
        (let [elm (elm/equal [(elm/negate (elm/subtract [quantity quantity]))
                              (elm/subtract [quantity quantity])])]
          (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  (testing "Date - Quantity"
    (are [x y res] (= res (c/compile {} (elm/subtract [x y])))
      #elm/date"2019" #elm/quantity[1 "year"] (system/date 2018)
      #elm/date"2019" #elm/quantity[13 "months"] (system/date 2018)

      #elm/date"2019-01" #elm/quantity[1 "month"] (system/date 2018 12)
      #elm/date"2019-01" #elm/quantity[12 "month"] (system/date 2018 1)
      #elm/date"2019-01" #elm/quantity[13 "month"] (system/date 2017 12)
      #elm/date"2019-01" #elm/quantity[1 "year"] (system/date 2018 1)

      #elm/date"2019-01-01" #elm/quantity[1 "year"] (system/date 2018 1 1)
      #elm/date"2012-02-29" #elm/quantity[1 "year"] (system/date 2011 2 28)
      #elm/date"2019-01-01" #elm/quantity[1 "month"] (system/date 2018 12 1)
      #elm/date"2019-01-01" #elm/quantity[1 "day"] (system/date 2018 12 31)))

  ;; TODO: find a solution to avoid overflow
  #_(testing "Subtracting a positive amount of years from a year makes it smaller"
      (tu/satisfies-prop 100
        (prop/for-all [year (s/gen :elm/year)
                       years (s/gen :elm/pos-years)]
          (let [elm (elm/less [(elm/subtract [year years]) year])]
            (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  ;; TODO: find a solution to avoid overflow
  #_(testing "Subtracting a positive amount of years from a year-month makes it smaller"
      (tu/satisfies-prop 100
        (prop/for-all [year-month (s/gen :elm/year-month)
                       years (s/gen :elm/pos-years)]
          (let [elm (elm/less [(elm/subtract [year-month years]) year-month])]
            (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  ;; TODO: find a solution to avoid overflow
  #_(testing "Subtracting a positive amount of years from a date makes it smaller"
      (tu/satisfies-prop 100
        (prop/for-all [date (s/gen :elm/literal-date)
                       years (s/gen :elm/pos-years)]
          (let [elm (elm/less [(elm/subtract [date years]) date])]
            (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  ;; TODO: find a solution to avoid overflow
  #_(testing "Subtracting a positive amount of months from a year-month makes it smaller"
      (tu/satisfies-prop 100
        (prop/for-all [year-month (s/gen :elm/year-month)
                       months (s/gen :elm/pos-months)]
          (let [elm (elm/less [(elm/subtract [year-month months]) year-month])]
            (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  ;; TODO: find a solution to avoid overflow
  #_(testing "Subtracting a positive amount of months from a date makes it smaller or lets it equal because a date can be also a year and subtracting a small amount of months from a year doesn't change it."
      (tu/satisfies-prop 100
        (prop/for-all [date (s/gen :elm/literal-date)
                       months (s/gen :elm/pos-months)]
          (let [elm (elm/less-or-equal [(elm/subtract [date months]) date])]
            (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  ;; TODO: find a solution to avoid overflow
  #_(testing "Subtracting a positive amount of days from a date makes it smaller or lets it equal because a date can be also a year or year-month and subtracting any amount of days from a year or year-month doesn't change it."
      (tu/satisfies-prop 100
        (prop/for-all [date (s/gen :elm/literal-date)
                       days (s/gen :elm/pos-days)]
          (let [elm (elm/less-or-equal [(elm/subtract [date days]) date])]
            (true? (core/-eval (c/compile {} elm) {} nil nil))))))

  (testing "DateTime - Quantity"
    (are [x y res] (= res (c/compile {} (elm/subtract [x y])))
      #elm/date-time"2019" #elm/quantity[1 "year"] (system/date-time 2018)
      #elm/date-time"2019" #elm/quantity[13 "months"] (system/date-time 2018)

      #elm/date-time"2019-01" #elm/quantity[1 "month"] (system/date-time 2018 12)
      #elm/date-time"2019-01" #elm/quantity[12 "month"] (system/date-time 2018 1)
      #elm/date-time"2019-01" #elm/quantity[13 "month"] (system/date-time 2017 12)
      #elm/date-time"2019-01" #elm/quantity[1 "year"] (system/date-time 2018 1)

      #elm/date-time"2019-01-01" #elm/quantity[1 "year"] (system/date-time 2018 1 1)
      #elm/date-time"2012-02-29" #elm/quantity[1 "year"] (system/date-time 2011 2 28)
      #elm/date-time"2019-01-01" #elm/quantity[1 "month"] (system/date-time 2018 12 1)
      #elm/date-time"2019-01-01" #elm/quantity[1 "day"] (system/date-time 2018 12 31)

      #elm/date-time"2019-01-01T00" #elm/quantity[1 "year"] (system/date-time 2018 1 1 0 0 0)
      #elm/date-time"2019-01-01T00" #elm/quantity[1 "month"] (system/date-time 2018 12 1 0 0 0)
      #elm/date-time"2019-01-01T00" #elm/quantity[1 "day"] (system/date-time 2018 12 31 0 0 0)
      #elm/date-time"2019-01-01T00" #elm/quantity[1 "hour"] (system/date-time 2018 12 31 23 0 0)
      #elm/date-time"2019-01-01T00" #elm/quantity[1 "minute"] (system/date-time 2018 12 31 23 59 0)
      #elm/date-time"2019-01-01T00" #elm/quantity[1 "second"] (system/date-time 2018 12 31 23 59 59)))

  (testing "Time - Quantity"
    (are [x y res] (= res (c/compile {} (elm/subtract [x y])))
      #elm/time"00:00:00" #elm/quantity[1 "hour"] (date-time/local-time 23 0 0)
      #elm/time"00:00:00" #elm/quantity[1 "minute"] (date-time/local-time 23 59 0)
      #elm/time"00:00:00" #elm/quantity[1 "second"] (date-time/local-time 23 59 59))))


;; 16.18. Successor
;;
;; The Successor operator returns the successor of the argument. For example,
;; the successor of 1 is 2. If the argument is already the maximum value for the
;; type, a run-time error is thrown.
;;
;; The Successor operator is defined for the Integer, Decimal, Date, DateTime,
;; and Time types.
;;
;; For Integer, Successor is equivalent to adding 1.
;;
;; For Decimal, Successor is equivalent to adding the minimum precision value
;; for the Decimal type, or 10^-08.
;;
;; For Date, DateTime, and Time values, Successor is equivalent to adding a
;; time-unit quantity for the lowest specified precision of the value. For
;; example, if the DateTime is fully specified, Successor is equivalent to
;; adding 1 millisecond; if the DateTime is specified to the second, Successor
;; is equivalent to adding one second, etc.
;;
;; If the argument is null, the result is null.
(deftest compile-successor-test
  (testing "Integer"
    (are [x res] (= res (c/compile {} (elm/successor x)))
      #elm/integer"0" 1))

  (testing "Decimal"
    (are [x res] (= res (c/compile {} (elm/successor x)))
      #elm/decimal"0" 1E-8M))

  (testing "Date"
    (are [x res] (= res (c/compile {} (elm/successor x)))
      #elm/date"2019" (system/date 2020)
      #elm/date"2019-01" (system/date 2019 2)
      #elm/date"2019-01-01" (system/date 2019 1 2)))

  (testing "DateTime"
    (are [x res] (= res (c/compile {} (elm/successor x)))
      #elm/date-time"2019" (system/date-time 2020)
      #elm/date-time"2019-01" (system/date-time 2019 2)
      #elm/date-time"2019-01-01" (system/date-time 2019 1 2)
      #elm/date-time"2019-01-01T00" (system/date-time 2019 1 1 0 0 0 1)))

  (testing "Time"
    (are [x res] (= res (c/compile {} (elm/successor x)))
      #elm/time"00:00:00" (date-time/local-time 0 0 1)))

  (testing "Quantity"
    (are [x res] (= res (c/compile {} (elm/successor x)))
      #elm/quantity[0 "m"] (quantity/quantity 1 "m")
      #elm/quantity[0M "m"] (quantity/quantity 1E-8M "m")))

  (tu/testing-unary-null elm/successor)

  (are [x] (thrown? Exception (core/-eval (c/compile {} (elm/successor x)) {} nil nil))
    (elm/decimal (str decimal/max))
    #elm/date"9999"
    #elm/date"9999-12"
    #elm/date"9999-12-31"
    #elm/time"23:59:59.999"
    #elm/date-time"9999"
    #elm/date-time"9999-12"
    #elm/date-time"9999-12-31"
    #elm/date-time"9999-12-31T23:59:59.999"))


;; 16.19. Truncate
;;
;; The Truncate operator returns the integer component of its argument.
;;
;; If the argument is null, the result is null.
(deftest compile-truncate-test
  (testing "Static"
    (are [x res] (= res (c/compile {} (elm/truncate x)))
      #elm/integer"1" 1
      #elm/decimal"1.1" 1))

  (tu/testing-unary-null elm/truncate))


;; 16.20. TruncatedDivide
;;
;; The TruncatedDivide operator performs integer division of its arguments.
;;
;; If either argument is null, the result is null.
;;
;; If the result of the operation cannot be represented, or the right argument
;; is 0, the result is null.
;;
;; The TruncatedDivide operator is defined for the Integer and Decimal types.
(deftest compile-truncated-divide-test
  (testing "Decimal"
    (testing "Static"
      (are [num div res] (= res (tu/compile-binop elm/truncated-divide
                                                  elm/decimal num div))
        "4.14" "2.06" 2M

        "1" "0" nil
        "1" "0.0" nil))

    (tu/testing-binary-null elm/truncated-divide #elm/decimal"1.1"))

  (testing "Integer"
    (testing "Static"
      (are [num div res] (= res (tu/compile-binop elm/truncated-divide
                                                  elm/integer num div))
        "1" "2" 0
        "2" "2" 1

        "1" "0" nil))

    (tu/testing-binary-null elm/truncated-divide #elm/integer"1"))

  (testing "Decimal/Integer"
    (testing "Static"
      (are [num div res] (= res (tu/compile-binop elm/truncated-divide
                                                  elm/decimal elm/integer
                                                  num div))
        "3" "2" 1M

        "1" "0" nil))

    (tu/testing-binary-null elm/truncated-divide #elm/decimal"1.1"
                            #elm/integer"1"))

  (testing "Integer/Decimal"
    (testing "Static"
      (are [num div res] (= res (tu/compile-binop elm/truncated-divide
                                                  elm/integer elm/decimal
                                                  num div))
        "3" "2" 1M

        "1" "0" nil
        "1" "0.0" nil))

    (tu/testing-binary-null elm/truncated-divide #elm/integer"1"
                            #elm/decimal"1.1")))
