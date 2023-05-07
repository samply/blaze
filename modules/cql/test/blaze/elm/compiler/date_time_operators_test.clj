(ns blaze.elm.compiler.date-time-operators-test
  "18. Date and Time Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.date-time :as date-time]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [blaze.fhir.spec.type.system :as system]
    [blaze.test-util :refer [given-thrown satisfies-prop]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [clojure.test.check.properties :as prop]
    [java-time.api :as time])
  (:import
    [blaze.fhir.spec.type.system DateDate DateYear DateYearMonth]
    [java.time OffsetDateTime]
    [java.time.temporal Temporal]))


(set! *warn-on-reflection* true)
(st/instrument)
(tu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (tu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; 18.1 Add
;;
;; See 16.2. Add


;; 18.2 After
;;
;; See 19.2. After


;; 18.3 Before
;;
;; See 19.3. Before


;; 18.4. Equal
;;
;; See 12.1. Equal


;; 18.5. Equivalent
;;
;; See 12.2. Equivalent


;; 18.6. Date
;;
;; The Date operator constructs a date value from the given components.
;;
;; At least one component must be specified, and no component may be specified
;; at a precision below an unspecified precision. For example, month may be
;; null, but if it is, day must be null as well.
(deftest compile-date-test
  (testing "Static Null year"
    (is (nil? (c/compile {} #elm/date [{:type "null"}]))))

  (testing "Static year"
    (is (= #system/date"2019" (c/compile {} #elm/date"2019"))))

  (testing "Static year over 9.999"
    (given-thrown (c/compile {} #elm/date"10000")
      :message := "Invalid value for Year (valid values 1 - 9999): 10000"))

  (testing "Dynamic year has type :system/date"
    (let [compile-ctx {:library {:parameters {:def [{:name "year"}]}}}
          elm #elm/date [#elm/parameter-ref "year"]]
      (is (= :system/date (system/type (c/compile compile-ctx elm))))))

  (testing "Dynamic Null year"
    (let [compile-ctx {:library {:parameters {:def [{:name "year"}]}}}
          elm #elm/date [#elm/parameter-ref "year"]
          expr (c/compile compile-ctx elm)
          eval-ctx {:parameters {"year" nil}}]
      (is (nil? (core/-eval expr eval-ctx nil nil)))))

  (testing "Dynamic year"
    (let [compile-ctx {:library {:parameters {:def [{:name "year"}]}}}
          elm #elm/date [#elm/parameter-ref "year"]
          expr (c/compile compile-ctx elm)
          eval-ctx {:parameters {"year" 2019}}]
      (is (= #system/date"2019" (core/-eval expr eval-ctx nil nil)))))

  (testing "Dynamic Null month"
    (let [compile-ctx {:library {:parameters {:def [{:name "month"}]}}}
          elm #elm/date [#elm/integer "2018" #elm/parameter-ref "month"]
          expr (c/compile compile-ctx elm)
          eval-ctx {:parameters {"month" nil}}]
      (is (= #system/date"2018" (core/-eval expr eval-ctx nil nil)))))

  (testing "Static year-month"
    (are [elm res] (= res (c/compile {} elm))
      #elm/date"2019-03"
      #system/date"2019-03"))

  (testing "Dynamic year-month has type :system/date"
    (let [compile-ctx {:library {:parameters {:def [{:name "month"}]}}}
          elm #elm/date [#elm/integer "2019" #elm/parameter-ref "month"]]
      (is (= :system/date (system/type (c/compile compile-ctx elm))))))

  (testing "Dynamic year-month"
    (let [compile-ctx {:library {:parameters {:def [{:name "month"}]}}}
          elm #elm/date [#elm/integer "2019" #elm/parameter-ref "month"]
          expr (c/compile compile-ctx elm)
          eval-ctx {:parameters {"month" 3}}]
      (is (= #system/date"2019-03" (core/-eval expr eval-ctx nil nil)))))

  (testing "Dynamic Null month and day"
    (let [compile-ctx {:library
                       {:parameters {:def [{:name "month"} {:name "day"}]}}}
          elm #elm/date [#elm/integer "2020"
                         #elm/parameter-ref "month"
                         #elm/parameter-ref "day"]
          expr (c/compile compile-ctx elm)
          eval-ctx {:parameters {"month" nil "day" nil}}]
      (is (= #system/date"2020" (core/-eval expr eval-ctx nil nil)))))

  (testing "Dynamic date has type :system/date"
    (let [compile-ctx {:library {:parameters {:def [{:name "day"}]}}}
          elm #elm/date [#elm/integer "2018"
                         #elm/integer "5"
                         #elm/parameter-ref "day"]]
      (is (= :system/date (system/type (c/compile compile-ctx elm))))))

  (testing "Dynamic Null day"
    (let [compile-ctx {:library {:parameters {:def [{:name "day"}]}}}
          elm #elm/date [#elm/integer "2018"
                         #elm/integer "5"
                         #elm/parameter-ref "day"]
          expr (c/compile compile-ctx elm)
          eval-ctx {:parameters {"day" nil}}]
      (is (= #system/date"2018-05" (core/-eval expr eval-ctx nil nil)))))

  (testing "Static date"
    (are [elm res] (= res (c/compile {} elm))
      #elm/date"2019-03-23"
      #system/date"2019-03-23"))

  (testing "Dynamic date"
    (let [compile-ctx {:library {:parameters {:def [{:name "day"}]}}}
          elm #elm/date [#elm/integer "2019"
                         #elm/integer "3"
                         #elm/parameter-ref "day"]
          expr (c/compile compile-ctx elm)
          eval-ctx {:parameters {"day" 23}}]
      (is (= #system/date"2019-03-23" (core/-eval expr eval-ctx nil nil)))))

  (testing "an ELM year (only literals) always compiles to a DateYear"
    (satisfies-prop 100
      (prop/for-all [year (s/gen :elm/literal-year)]
        (instance? DateYear (c/compile {} year)))))

  (testing "an ELM year-month (only literals) always compiles to a DateYearMonth"
    (satisfies-prop 100
      (prop/for-all [year-month (s/gen :elm/literal-year-month)]
        (instance? DateYearMonth (c/compile {} year-month)))))

  (testing "an ELM date (only literals) always compiles to something implementing Temporal"
    (satisfies-prop 100
      (prop/for-all [date (s/gen :elm/literal-date)]
        (instance? Temporal (c/compile {} date))))))


;; 18.7. DateFrom
;;
;; The DateFrom operator returns the date (with no time components specified) of
;; the argument.
;;
;; If the argument is null, the result is null.
(deftest compile-date-from-test
  (are [x res] (= res (c/compile {} (elm/date-from x)))
    #elm/date"2019" #system/date"2019"
    #elm/date"2019-04" #system/date"2019-04"
    #elm/date"2019-04-17" #system/date"2019-04-17"
    #elm/date-time"2019" #system/date"2019"
    #elm/date-time"2019-04" #system/date"2019-04"
    #elm/date-time"2019-04-17" #system/date"2019-04-17"
    #elm/date-time"2019-04-17T12:48" #system/date"2019-04-17")

  (tu/testing-unary-null elm/date-from)

  (tu/testing-unary-form elm/date-from))


;; 18.8. DateTime
;;
;; The DateTime operator constructs a DateTime value from the given components.
;;
;; At least one component other than timezoneOffset must be specified, and no
;; component may be specified at a precision below an unspecified precision. For
;; example, hour may be null, but if it is, minute, second, and millisecond must
;; all be null as well.
;;
;; If all the arguments are null, the result is null, as opposed to a DateTime
;; with no components specified.
;;
;; Although the milliseconds are specified with a separate component, seconds
;; and milliseconds are combined and represented as a Decimal for the purposes
;; of comparison.
;;
;; If timezoneOffset is not specified, it is defaulted to the timezone offset of
;; the evaluation request.
(deftest compile-date-time-test
  (testing "Static Null year"
    (is (nil? (c/compile {} #elm/date-time[{:type "null"}]))))

  (testing "Static year"
    (is (= #system/date-time"2019" (c/compile {} #elm/date-time"2019"))))

  (testing "Dynamic Null year"
    (let [compile-ctx {:library {:parameters {:def [{:name "year"}]}}}
          elm #elm/date-time[#elm/parameter-ref "year"]
          expr (c/compile compile-ctx elm)
          eval-ctx {:parameters {"year" nil}}]
      (is (nil? (core/-eval expr eval-ctx nil nil)))))

  (testing "Dynamic year"
    (let [compile-ctx {:library {:parameters {:def [{:name "year"}]}}}
          elm #elm/date-time[#elm/parameter-ref "year"]
          expr (c/compile compile-ctx elm)
          eval-ctx {:parameters {"year" 2019}}]
      (is (= #system/date-time"2019" (core/-eval expr eval-ctx nil nil)))))

  (testing "Dynamic Null month"
    (let [compile-ctx {:library {:parameters {:def [{:name "month"}]}}}
          elm #elm/date-time[#elm/integer "2018" #elm/parameter-ref "month"]
          expr (c/compile compile-ctx elm)
          eval-ctx {:parameters {"month" nil}}]
      (is (= #system/date-time"2018" (core/-eval expr eval-ctx nil nil)))))

  (testing "Static year-month"
    (are [elm res] (= res (c/compile {} elm))
      #elm/date-time"2019-03"
      #system/date-time"2019-03"))

  (testing "Dynamic year-month"
    (let [compile-ctx {:library {:parameters {:def [{:name "month"}]}}}
          elm #elm/date-time[#elm/integer "2019" #elm/parameter-ref "month"]
          expr (c/compile compile-ctx elm)
          eval-ctx {:parameters {"month" 3}}]
      (is (= #system/date-time"2019-03" (core/-eval expr eval-ctx nil nil)))))

  (testing "Dynamic Null month and day"
    (let [compile-ctx {:library
                       {:parameters {:def [{:name "month"} {:name "day"}]}}}
          elm #elm/date-time[#elm/integer "2020"
                             #elm/parameter-ref "month"
                             #elm/parameter-ref "day"]
          expr (c/compile compile-ctx elm)
          eval-ctx {:parameters {"month" nil "day" nil}}]
      (is (= #system/date-time"2020" (core/-eval expr eval-ctx nil nil)))))

  (testing "Dynamic Null day"
    (let [compile-ctx {:library {:parameters {:def [{:name "day"}]}}}
          elm #elm/date-time[#elm/integer "2018"
                             #elm/integer "5"
                             #elm/parameter-ref "day"]
          expr (c/compile compile-ctx elm)
          eval-ctx {:parameters {"day" nil}}]
      (is (= #system/date-time"2018-05" (core/-eval expr eval-ctx nil nil)))))

  (testing "Static date"
    (are [elm res] (= res (c/compile {} elm))
      #elm/date-time"2019-03-23"
      #system/date-time"2019-03-23"))

  (testing "Dynamic date"
    (let [compile-ctx {:library {:parameters {:def [{:name "day"}]}}}
          elm #elm/date-time[#elm/integer "2019"
                             #elm/integer "3"
                             #elm/parameter-ref "day"]
          expr (c/compile compile-ctx elm)
          eval-ctx {:parameters {"day" 23}}]
      (is (= #system/date-time"2019-03-23" (core/-eval expr eval-ctx nil nil)))))

  (testing "Static hour"
    (are [elm res] (= res (c/compile {} elm))
      #elm/date-time"2019-03-23T12"
      (system/date-time 2019 3 23 12 0 0)))

  (testing "Dynamic hour"
    (let [compile-ctx {:library {:parameters {:def [{:name "hour"}]}}}
          elm #elm/date-time[#elm/integer "2019"
                             #elm/integer "3"
                             #elm/integer "23"
                             #elm/parameter-ref "hour"]
          expr (c/compile compile-ctx elm)
          eval-ctx {:parameters {"hour" 12}}]
      (is (= (system/date-time 2019 3 23 12 0 0)
             (core/-eval expr eval-ctx nil nil)))))

  (testing "minute"
    (are [elm res] (= res (c/compile {} elm))
      #elm/date-time"2019-03-23T12:13"
      (system/date-time 2019 3 23 12 13 0)))

  (testing "second"
    (are [elm res] (= res (c/compile {} elm))
      #elm/date-time"2019-03-23T12:13:14"
      (system/date-time 2019 3 23 12 13 14)))

  (testing "millisecond"
    (are [elm res] (= res (c/compile {} elm))
      #elm/date-time"2019-03-23T12:13:14.1"
      (system/date-time 2019 3 23 12 13 14 1)))

  (testing "Invalid DateTime above max value"
    (are [elm] (thrown? Exception (c/compile {} elm))
      #elm/date-time"10000-12-31T23:59:59.999"))

  (testing "with offset"
    (are [elm res] (= res (core/-eval (c/compile {} elm) {:now tu/now} nil nil))
      #elm/date-time[#elm/integer "2019" #elm/integer "3" #elm/integer "23"
                     #elm/integer "12" #elm/integer "13" #elm/integer "14" #elm/integer "0"
                     #elm/decimal "-2"]
      (system/date-time 2019 3 23 14 13 14)

      #elm/date-time[#elm/integer "2019" #elm/integer "3" #elm/integer "23"
                     #elm/integer "12" #elm/integer "13" #elm/integer "14" #elm/integer "0"
                     #elm/decimal "-1"]
      (system/date-time 2019 3 23 13 13 14)

      #elm/date-time[#elm/integer "2019" #elm/integer "3" #elm/integer "23"
                     #elm/integer "12" #elm/integer "13" #elm/integer "14" #elm/integer "0"
                     #elm/decimal "0"]
      (system/date-time 2019 3 23 12 13 14)

      #elm/date-time[#elm/integer "2019" #elm/integer "3" #elm/integer "23"
                     #elm/integer "12" #elm/integer "13" #elm/integer "14" #elm/integer "0"
                     #elm/decimal "1"]
      (system/date-time 2019 3 23 11 13 14)

      #elm/date-time[#elm/integer "2019" #elm/integer "3" #elm/integer "23"
                     #elm/integer "12" #elm/integer "13" #elm/integer "14" #elm/integer "0"
                     #elm/decimal "2"]
      (system/date-time 2019 3 23 10 13 14)

      #elm/date-time[#elm/integer "2012" #elm/integer "3" #elm/integer "10"
                     #elm/integer "10" #elm/integer "20" #elm/integer "0" #elm/integer "999"
                     #elm/decimal "7"]
      (system/date-time 2012 3 10 3 20 0 999)))

  (testing "with decimal offset"
    (are [elm res] (= res (core/-eval (c/compile {} elm) {:now tu/now} nil nil))
      #elm/date-time[#elm/integer "2019" #elm/integer "3" #elm/integer "23"
                     #elm/integer "12" #elm/integer "13" #elm/integer "14" #elm/integer "0"
                     #elm/decimal "1.5"]
      (system/date-time 2019 3 23 10 43 14)))

  (testing "an ELM date-time (only literals) always evaluates to something implementing Temporal"
    (satisfies-prop 100
      (prop/for-all [date-time (s/gen :elm/literal-date-time)]
        (instance? Temporal (core/-eval (c/compile {} date-time) {:now tu/now} nil nil))))))


;; 18.9. DateTimeComponentFrom
;;
;; The DateTimeComponentFrom operator returns the specified component of the
;; argument.
;;
;; If the argument is null, the result is null.
;
;; The precision must be one of Year, Month, Day, Hour, Minute, Second, or
;; Millisecond. Note specifically that since there is variability how weeks are
;; counted, Week precision is not supported, and will result in an error.
(deftest compile-date-time-component-from-test
  (let [compile (partial tu/compile-unop-precision elm/date-time-component-from)
        eval #(core/-eval % {:now tu/now} nil nil)]

    (doseq [op-ctor [elm/date elm/date-time]]
      (are [x precision res] (= res (eval (compile op-ctor x precision)))
        "2019" "Year" 2019
        "2019-04" "Year" 2019
        "2019-04-17" "Year" 2019
        "2019" "Month" nil
        "2019-04" "Month" 4
        "2019-04-17" "Month" 4
        "2019" "Day" nil
        "2019-04" "Day" nil
        "2019-04-17" "Day" 17))

    (are [x precision res] (= res (eval (compile elm/date-time x precision)))
      "2019-04-17T12:48" "Hour" 12))

  (tu/testing-unary-precision-form elm/date-time-component-from "Year" "Month"
                                   "Day" "Hour" "Minute" "Second" "Millisecond"))


;; 18.10. DifferenceBetween
;;
;; The DifferenceBetween operator returns the number of boundaries crossed for
;; the specified precision between the first and second arguments. If the first
;; argument is after the second argument, the result is negative. Because this
;; operation is only counting boundaries crossed, the result is always an
;; integer.
;;
;; For Date values, precision must be one of Year, Month, Week, or Day.
;;
;; For Time values, precision must be one of Hour, Minute, Second, or
;; Millisecond.
;;
;; For calculations involving weeks, Sunday is considered to be the first day of
;; the week for the purposes of determining boundaries.
;;
;; When calculating the difference between DateTime values with different
;; timezone offsets, implementations should normalize to the timezone offset of
;; the evaluation request timestamp, but only when the comparison precision is
;; hours, minutes, seconds, or milliseconds.
;;
;; If either argument is null, the result is null.
;;
;; Note that this operator can be implemented using Uncertainty as described in
;; the CQL specification, Chapter 5, Precision-Based Timing.
(deftest compile-difference-between-test
  (let [compile (partial tu/compile-binop-precision elm/difference-between)]

    (testing "Year precision"
      (doseq [op-xtor [elm/date elm/date-time]]
        (are [x y res] (= res (compile op-xtor x y "Year"))
          "2018" "2019" 1
          "2018" "2017" -1
          "2018" "2018" 0)))

    (testing "Month precision"
      (doseq [op-ctor [elm/date elm/date-time]]
        (are [x y res] (= res (compile op-ctor x y "Month"))
          "2018-01" "2018-02" 1
          "2018-01" "2017-12" -1
          "2018-01" "2018-01" 0)))

    (testing "Day precision"
      (doseq [op-ctor [elm/date elm/date-time]]
        (are [x y res] (= res (compile op-ctor x y "Day"))
          "2018-01-01" "2018-01-02" 1
          "2018-01-01" "2017-12-31" -1
          "2018-01-01" "2018-01-01" 0)))

    (testing "Hour precision"
      (are [x y res] (= res (compile elm/date-time x y "Hour"))
        "2018-01-01T00" "2018-01-01T01" 2
        "2018-01-01T00" "2017-12-31T23" -2
        "2018-01-01T00" "2018-01-01T00" 0))

    (testing "Calculating the difference between temporals with insufficient precision results in null."
      (doseq [op-ctor [elm/date elm/date-time]]
        (are [x y p] (nil? (compile op-ctor x y p))
          "2018" "2018" "Month"
          "2018-01" "2018-01" "Day"
          "2018-01-01" "2018-01-01" "Hour"

          "2018" "2018" "Month"
          "2018-01" "2018-01" "Day"
          "2018-01-01" "2018-01-01" "Hour"))))

  (tu/testing-binary-precision-form elm/difference-between "Year" "Month" "Day"))


;; 18.11. DurationBetween
;;
;; The DurationBetween operator returns the number of whole calendar periods for
;; the specified precision between the first and second arguments. If the first
;; argument is after the second argument, the result is negative. The result of
;; this operation is always an integer; any fractional periods are dropped.
;;
;; For Date values, precision must be one of Year, Month, Week, or Day.
;;
;; For Time values, precision must be one of Hour, Minute, Second, or
;; Millisecond.
;;
;; For calculations involving weeks, the duration of a week is equivalent to
;; 7 days.
;;
;; When calculating duration between DateTime values with different timezone
;; offsets, implementations should normalize to the timezone offset of the
;; evaluation request timestamp, but only when the comparison precision is
;; hours, minutes, seconds, or milliseconds.
;;
;; If either argument is null, the result is null.
;;
;; Note that this operator can be implemented using Uncertainty as described in
;; the CQL specification, Chapter 5, Precision-Based Timing.
(deftest compile-duration-between-test
  (let [compile (partial tu/compile-binop-precision elm/duration-between)]

    (testing "Year precision"
      (doseq [op-xtor [elm/date elm/date-time]]
        (are [x y res] (= res (compile op-xtor x y "Year"))
          "2018" "2019" 1
          "2018" "2017" -1
          "2018" "2018" 0)))

    (testing "Month precision"
      (doseq [op-ctor [elm/date elm/date-time]]
        (are [x y res] (= res (compile op-ctor x y "Month"))
          "2018-01" "2018-02" 1
          "2018-01" "2017-12" -1
          "2018-01" "2018-01" 0)))

    (testing "Day precision"
      (doseq [op-ctor [elm/date elm/date-time]]
        (are [x y res] (= res (compile op-ctor x y "Day"))
          "2018-01-01" "2018-01-02" 1
          "2018-01-01" "2017-12-31" -1
          "2018-01-01" "2018-01-01" 0)))

    (testing "Hour precision"
      (are [x y res] (= res (compile elm/date-time x y "Hour"))
        "2018-01-01T00" "2018-01-01T01" 1
        "2018-01-01T00" "2017-12-31T23" -1
        "2018-01-01T00" "2018-01-01T00" 0))

    (testing "Calculating the duration between temporals with insufficient precision results in null."
      (doseq [op-ctor [elm/date elm/date-time]]
        (are [x y p] (nil? (compile op-ctor x y p))
          "2018" "2018" "Month"
          "2018-01" "2018-01" "Day"
          "2018-01-01" "2018-01-01" "Hour"

          "2018" "2018" "Month"
          "2018-01" "2018-01" "Day"
          "2018-01-01" "2018-01-01" "Hour"))))

  (tu/testing-binary-precision-form elm/duration-between "Year" "Month" "Day"))


;; 18.12. Not Equal
;;
;; See 12.7. NotEqual


;; 18.13. Now
;;
;; The Now operator returns the date and time of the start timestamp associated
;; with the evaluation request. Now is defined in this way for two reasons:
;;
;; 1) The operation will always return the same value within any given
;; evaluation, ensuring that the result of an expression containing Now will
;; always return the same result.
;;
;; 2) The operation will return the timestamp associated with the evaluation
;; request, allowing the evaluation to be performed with the same timezone
;; offset information as the data delivered with the evaluation request.
(deftest compile-now-test
  (are [elm res] (= res (core/-eval (c/compile {} elm) {:now tu/now} nil nil))
    {:type "Now"}
    tu/now))


;; 18.14. SameAs
;;
;; The SameAs operator is defined for Date, DateTime, and Time values, as well
;; as intervals.
;;
;; For the Interval overloads, the SameAs operator returns true if the intervals
;; start and end at the same value, using the semantics described in the Start
;; and End operator to determine interval boundaries.
;;
;; The SameAs operator compares two Date, DateTime, or Time values to the
;; specified precision for equality. Individual component values are compared
;; starting from the year component down to the specified precision. If all
;; values are specified and have the same value for each component, then the
;; result is true. If a compared component is specified in both dates, but the
;; values are not the same, then the result is false. Otherwise the result is
;; null, as there is not enough information to make a determination.
;;
;; If no precision is specified, the comparison is performed beginning with
;; years (or hours for time values) and proceeding to the finest precision
;; specified in either input.
;;
;; For Date values, precision must be one of year, month, or day.
;;
;; For DateTime values, precision must be one of year, month, day, hour, minute,
;; second, or millisecond.
;;
;; For Time values, precision must be one of hour, minute, second, or
;; millisecond.
;;
;; Note specifically that due to variability in the way week numbers are
;; determined, comparisons involving weeks are not supported.
;;
;; As with all date and time calculations, comparisons are performed respecting
;; the timezone offset.
;;
;; If either argument is null, the result is null.
(deftest compile-same-as-test
  (testing "Date"
    (are [x y res] (= res (tu/compile-binop elm/same-as elm/date x y))
      "2019" "2019" true
      "2019" "2020" false
      "2019-04" "2019-04" true
      "2019-04" "2019-05" false
      "2019-04-17" "2019-04-17" true
      "2019-04-17" "2019-04-18" false)

    (tu/testing-binary-null elm/same-as #elm/date"2019")
    (tu/testing-binary-null elm/same-as #elm/date"2019-04")
    (tu/testing-binary-null elm/same-as #elm/date"2019-04-17")

    (testing "with year precision"
      (are [x y res] (= res (tu/compile-binop-precision elm/same-as elm/date x y "year"))
        "2019" "2019" true
        "2019" "2020" false
        "2019-04" "2019-04" true
        "2019-04" "2019-05" true
        "2019-04-17" "2019-04-17" true
        "2019-04-17" "2019-04-18" true)))

  (testing "DateTime"
    (are [x y res] (= res (tu/compile-binop elm/same-as elm/date-time x y))
      "2019" "2019" true
      "2019" "2020" false
      "2019-04" "2019-04" true
      "2019-04" "2019-05" false
      "2019-04-17" "2019-04-17" true
      "2019-04-17" "2019-04-18" false)

    (tu/testing-binary-null elm/same-as #elm/date-time"2019")
    (tu/testing-binary-null elm/same-as #elm/date-time"2019-04")
    (tu/testing-binary-null elm/same-as #elm/date-time"2019-04-17")

    (testing "with year precision"
      (are [x y res] (= res (tu/compile-binop-precision elm/same-as elm/date-time x y "year"))
        "2019" "2019" true
        "2019" "2020" false
        "2019-04" "2019-04" true
        "2019-04" "2019-05" true
        "2019-04-17" "2019-04-17" true
        "2019-04-17" "2019-04-18" true)))

  (tu/testing-binary-precision-form elm/same-as))


;; 18.15. SameOrBefore
;;
;; The SameOrBefore operator is defined for Date, DateTime, and Time values, as
;; well as intervals.
;;
;; For the Interval overload, the SameOrBefore operator returns true if the
;; first interval ends on or before the second one starts. In other words, if
;; the ending point of the first interval is less than or equal to the starting
;; point of the second interval, using the semantics described in the Start and
;; End operators to determine interval boundaries.
;;
;; The SameOrBefore operator compares two Date, DateTime, or Time values to the
;; specified precision to determine whether the first argument is the same or
;; before the second argument. The comparison is performed by considering each
;; precision in order, beginning with years (or hours for time values). If the
;; values are the same, comparison proceeds to the next precision; if the first
;; value is less than the second, the result is true; if the first value is
;; greater than the second, the result is false; if either input has no value
;; for the precision, the comparison stops and the result is null; if the
;; specified precision has been reached, the comparison stops and the result is
;; true.
;;
;; If no precision is specified, the comparison is performed beginning with
;; years (or hours for time values) and proceeding to the finest precision
;; specified in either input.
;;
;; For Date values, precision must be one of year, month, or day.
;;
;; For DateTime values, precision must be one of year, month, day, hour, minute,
;; second, or millisecond.
;;
;; For Time values, precision must be one of hour, minute, second, or
;; millisecond.
;;
;; Note specifically that due to variability in the way week numbers are
;; determined, comparisons involving weeks are not supported.
;;
;; When comparing DateTime values with different timezone offsets,
;; implementations should normalize to the timezone offset of the evaluation
;; request timestamp, but only when the comparison precision is hours, minutes,
;; seconds, or milliseconds.
;;
;; If either argument is null, the result is null.
(deftest compile-same-or-before-test
  (testing "Interval"
    (are [x y res] (= res (tu/compile-binop elm/same-or-before elm/interval x y))
      [#elm/integer "1" #elm/integer "2"]
      [#elm/integer "2" #elm/integer "3"] true))

  (testing "Date"
    (are [x y res] (= res (tu/compile-binop elm/same-or-before elm/date x y))
      "2019" "2020" true
      "2019" "2019" true
      "2019" "2018" false
      "2019-04" "2019-05" true
      "2019-04" "2019-04" true
      "2019-04" "2019-03" false
      "2019-04-17" "2019-04-18" true
      "2019-04-17" "2019-04-17" true
      "2019-04-17" "2019-04-16" false)

    (tu/testing-binary-null elm/same-or-before #elm/date"2019")
    (tu/testing-binary-null elm/same-or-before #elm/date"2019-04")
    (tu/testing-binary-null elm/same-or-before #elm/date"2019-04-17")

    (testing "with year precision"
      (are [x y res] (= res (tu/compile-binop-precision elm/same-or-before elm/date x y "year"))
        "2019" "2020" true
        "2019" "2019" true
        "2019" "2018" false
        "2019-04" "2019-05" true
        "2019-04" "2019-04" true
        "2019-04" "2019-03" true)))

  (testing "DateTime"
    (are [x y res] (= res (tu/compile-binop elm/same-or-before elm/date-time x y))
      "2019" "2020" true
      "2019" "2019" true
      "2019" "2018" false
      "2019-04" "2019-05" true
      "2019-04" "2019-04" true
      "2019-04" "2019-03" false
      "2019-04-17" "2019-04-18" true
      "2019-04-17" "2019-04-17" true
      "2019-04-17" "2019-04-16" false)

    (tu/testing-binary-null elm/same-or-before #elm/date-time"2019")
    (tu/testing-binary-null elm/same-or-before #elm/date-time"2019-04")
    (tu/testing-binary-null elm/same-or-before #elm/date-time"2019-04-17")

    (testing "with year precision"
      (are [x y res] (= res (tu/compile-binop-precision elm/same-or-before elm/date-time x y "year"))
        "2019" "2020" true
        "2019" "2019" true
        "2019" "2018" false
        "2019-04" "2019-05" true
        "2019-04" "2019-04" true
        "2019-04" "2019-03" true)))

  (tu/testing-binary-precision-form elm/same-or-before))


;; 18.15. SameOrAfter
;;
;; The SameOrAfter operator is defined for Date, DateTime, and Time values, as
;; well as intervals.
;;
;; For the Interval overload, the SameOrAfter operator returns true if the first
;; interval starts on or after the second one ends. In other words, if the
;; starting point of the first interval is greater than or equal to the ending
;; point of the second interval, using the semantics described in the Start and
;; End operators to determine interval boundaries.
;;
;; For the Date, DateTime, and Time overloads, this operator compares two Date,
;; DateTime, or Time values to the specified precision to determine whether the
;; first argument is the same or after the second argument. The comparison is
;; performed by considering each precision in order, beginning with years (or
;; hours for time values). If the values are the same, comparison proceeds to
;; the next precision; if the first value is greater than the second, the result
;; is true; if the first value is less than the second, the result is false; if
;; either input has no value for the precision, the comparison stops and the
;; result is null; if the specified precision has been reached, the comparison
;; stops and the result is true.
;;
;; If no precision is specified, the comparison is performed beginning with
;; years (or hours for time values) and proceeding to the finest precision
;; specified in either input.
;;
;; For Date values, precision must be one of year, month, or day.
;;
;; For DateTime values, precision must be one of year, month, day, hour, minute,
;; second, or millisecond.
;;
;; For Time values, precision must be one of hour, minute, second, or
;; millisecond.
;;
;; Note specifically that due to variability in the way week numbers are
;; determined, comparisons involving weeks are not supported.
;;
;; When comparing DateTime values with different timezone offsets,
;; implementations should normalize to the timezone offset of the evaluation
;; request timestamp, but only when the comparison precision is hours, minutes,
;; seconds, or milliseconds.
;;
;; If either argument is null, the result is null.
(deftest compile-same-or-after-test
  (testing "Interval"
    (are [x y res] (= res (tu/compile-binop elm/same-or-after elm/interval x y))
      [#elm/integer "2" #elm/integer "3"]
      [#elm/integer "1" #elm/integer "2"] true))

  (testing "Date"
    (are [x y res] (= res (tu/compile-binop elm/same-or-after elm/date x y))
      "2019" "2018" true
      "2019" "2019" true
      "2019" "2020" false
      "2019-04" "2019-03" true
      "2019-04" "2019-04" true
      "2019-04" "2019-05" false
      "2019-04-17" "2019-04-16" true
      "2019-04-17" "2019-04-17" true
      "2019-04-17" "2019-04-18" false)

    (tu/testing-binary-null elm/same-or-after #elm/date"2019")
    (tu/testing-binary-null elm/same-or-after #elm/date"2019-04")
    (tu/testing-binary-null elm/same-or-after #elm/date"2019-04-17")

    (testing "with year precision"
      (are [x y res] (= res (tu/compile-binop-precision elm/same-or-after elm/date x y "year"))
        "2019" "2018" true
        "2019" "2019" true
        "2019" "2020" false
        "2019-04" "2019-03" true
        "2019-04" "2019-04" true
        "2019-04" "2019-05" true)))

  (testing "DateTime"
    (are [x y res] (= res (tu/compile-binop elm/same-or-after elm/date-time x y))
      "2019" "2018" true
      "2019" "2019" true
      "2019" "2020" false
      "2019-04" "2019-03" true
      "2019-04" "2019-04" true
      "2019-04" "2019-05" false
      "2019-04-17" "2019-04-16" true
      "2019-04-17" "2019-04-17" true
      "2019-04-17" "2019-04-18" false)

    (tu/testing-binary-null elm/same-or-after #elm/date-time"2019")
    (tu/testing-binary-null elm/same-or-after #elm/date-time"2019-04")
    (tu/testing-binary-null elm/same-or-after #elm/date-time"2019-04-17")

    (testing "with year precision"
      (are [x y res] (= res (tu/compile-binop-precision elm/same-or-after elm/date-time x y "year"))
        "2019" "2018" true
        "2019" "2019" true
        "2019" "2020" false
        "2019-04" "2019-03" true
        "2019-04" "2019-04" true
        "2019-04" "2019-05" true)))

  (tu/testing-binary-precision-form elm/same-or-after))


;; 18.18. Time
;;
;; The Time operator constructs a time value from the given components.
;;
;; At least one component other than timezoneOffset must be specified, and no
;; component may be specified at a precision below an unspecified precision.
;; For example, minute may be null, but if it is, second, and millisecond must
;; all be null as well.
;;
;; Although the milliseconds are specified with a separate component, seconds
;; and milliseconds are combined and represented as a Decimal for the purposes
;; of comparison.
(deftest compile-time-test
  (testing "Static hour"
    (are [elm res] (= res (c/compile {} elm))
      #elm/time [#elm/integer "12"]
      (date-time/local-time 12)))

  (testing "Dynamic hour"
    (let [compile-ctx {:library {:parameters {:def [{:name "hour"}]}}}
          elm #elm/time [#elm/parameter-ref "hour"]
          expr (c/compile compile-ctx elm)
          eval-ctx {:parameters {"hour" 12}}]
      (is (= (date-time/local-time 12) (core/-eval expr eval-ctx nil nil)))))

  (testing "Static hour-minute"
    (are [elm res] (= res (c/compile {} elm))
      #elm/time [#elm/integer "12" #elm/integer "13"]
      (date-time/local-time 12 13)))

  (testing "Dynamic hour-minute"
    (let [compile-ctx {:library {:parameters {:def [{:name "minute"}]}}}
          elm #elm/time [#elm/integer "12" #elm/parameter-ref "minute"]
          expr (c/compile compile-ctx elm)
          eval-ctx {:parameters {"minute" 13}}]
      (is (= (date-time/local-time 12 13) (core/-eval expr eval-ctx nil nil)))))

  (testing "Static hour-minute-second"
    (are [elm res] (= res (c/compile {} elm))
      #elm/time [#elm/integer "12" #elm/integer "13" #elm/integer "14"]
      (date-time/local-time 12 13 14)))

  (testing "Dynamic hour-minute-second"
    (let [compile-ctx {:library {:parameters {:def [{:name "second"}]}}}
          elm #elm/time [#elm/integer "12"
                         #elm/integer "13"
                         #elm/parameter-ref "second"]
          expr (c/compile compile-ctx elm)
          eval-ctx {:parameters {"second" 14}}]
      (is (= (date-time/local-time 12 13 14) (core/-eval expr eval-ctx nil nil)))))

  (testing "Static hour-minute-second-millisecond"
    (are [elm res] (= res (c/compile {} elm))
      #elm/time [#elm/integer "12" #elm/integer "13" #elm/integer "14"
                 #elm/integer "15"]
      (date-time/local-time 12 13 14 15)))

  (testing "Dynamic hour-minute-second-millisecond"
    (let [compile-ctx {:library {:parameters {:def [{:name "millisecond"}]}}}
          elm #elm/time [#elm/integer "12"
                         #elm/integer "13"
                         #elm/integer "14"
                         #elm/parameter-ref "millisecond"]
          expr (c/compile compile-ctx elm)
          eval-ctx {:parameters {"millisecond" 15}}]
      (is (= (date-time/local-time 12 13 14 15) (core/-eval expr eval-ctx nil nil)))))

  (testing "an ELM time (only literals) always compiles to a LocalTime"
    (satisfies-prop 100
      (prop/for-all [time (s/gen :elm/time)]
        (date-time/local-time? (c/compile {} time))))))


;; 18.21. TimeOfDay
;;
;; The TimeOfDay operator returns the time-of-day of the start timestamp
;; associated with the evaluation request. See the Now operator for more
;; information on the rationale for defining the TimeOfDay operator in this way.
(deftest compile-time-of-day-test
  (are [res] (= res (core/-eval (c/compile {} {:type "TimeOfDay"}) {:now tu/now} nil nil))
    (time/local-time tu/now)))


;; 18.22. Today
;;
;; The Today operator returns the date (with no time component) of the start
;; timestamp associated with the evaluation request. See the Now operator for
;; more information on the rationale for defining the Today operator in this
;; way.
(deftest compile-today-test
  (are [res] (= res (core/-eval (c/compile {} elm/today) {:now tu/now} nil nil))
    (DateDate/fromLocalDate (.toLocalDate ^OffsetDateTime tu/now)))

  (tu/testing-constant-form elm/today))
