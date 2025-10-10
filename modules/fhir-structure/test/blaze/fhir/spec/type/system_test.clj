(ns blaze.fhir.spec.type.system-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.type.system :as system]
   [blaze.fhir.spec.type.system-spec]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [java-time.api :as time])
  (:import
   [blaze.fhir.spec.type.system DateDate DateTimeDate DateTimeYear DateTimeYearMonth DateYear DateYearMonth]
   [java.time LocalTime ZoneOffset]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest value-test
  (are [x] (system/value? x)
    true
    false
    ""
    0M
    #system/date "2020")

  (are [x] (not (system/value? x))
    nil
    (Object.)))

(deftest type-test
  (are [x type] (= type (system/type x))
    nil nil
    (Object.) nil))

(deftest boolean-test
  (testing "boolean?"
    (is (true? (system/boolean? true)))
    (is (false? (system/boolean? nil))))

  (testing "system equals"
    (are [a b pred] (pred (system/equals a b))
      true true true?
      true false false?
      true nil nil?
      false true false?
      false false true?
      false nil nil?
      nil true nil?
      nil false nil?
      nil nil nil?)))

(deftest integer-test
  (testing "long?"
    (is (true? (system/integer? (int 0))))
    (is (false? (system/integer? 0))))

  (testing "type"
    (is (= :system/integer (system/type (int 0)))))

  (testing "system equals"
    (are [a b pred] (pred (system/equals a b))
      (int 0) (int 0) true?
      (int 0) (int 1) false?
      (int 0) nil nil?
      (int 1) (int 0) false?
      (int 1) (int 1) true?
      (int 1) nil nil?
      nil (int 0) nil?
      nil (int 1) nil?
      nil nil nil?)))

(deftest long-test
  (testing "long?"
    (is (true? (system/long? 0)))
    (is (false? (system/long? (int 0)))))

  (testing "system equals"
    (are [a b pred] (pred (system/equals a b))
      0 0 true?
      0 1 false?
      0 nil nil?
      1 0 false?
      1 1 true?
      1 nil nil?
      nil 0 nil?
      nil 1 nil?
      nil nil nil?)))

(deftest string-test
  (testing "string?"
    (is (true? (system/string? "")))
    (is (false? (system/string? nil))))

  (testing "system equals"
    (are [a b pred] (pred (system/equals a b))
      "a" "a" true?
      "a" "b" false?
      "a" nil nil?
      "b" "a" false?
      "b" "b" true?
      "b" nil nil?
      nil "a" nil?
      nil "b" nil?
      nil nil nil?)))

(deftest decimal-test
  (testing "decimal?"
    (is (true? (system/decimal? 1M)))
    (is (false? (system/decimal? 1))))

  (testing "system equals"
    (are [a b pred] (pred (system/equals a b))
      0M 0M true?
      0M 1M false?
      0M nil nil?
      1M 0M false?
      1M 1M true?
      1M nil nil?
      nil 0M nil?
      nil 1M nil?
      nil nil nil?)))

(deftest parse-decimal-test
  (testing "valid"
    (are [s d] (= d (system/parse-decimal s))
      "1" 1M
      "1.1" 1.1M))

  (testing "invalid"
    (are [s] (ba/incorrect? (system/parse-decimal s))
      "a"
      "")))

(deftest date-test
  (testing "date?"
    (are [date] (true? (system/date? date))
      #system/date "2020"
      #system/date "2020-01"
      #system/date "2020-01-01")

    (are [x] (false? (system/date? x))
      nil
      #system/date-time "2020"))

  (testing "date"
    (testing "year"
      (are [year date] (= date (system/date year))
        1000 #system/date "1000"
        2024 #system/date "2024"
        9999 #system/date "9999")

      (given-thrown (system/date -1)
        :message := "Invalid value for Year (valid values 1 - 9999): -1"))

    (testing "year-month"
      (are [year month date] (= date (system/date year month))
        1000 1 #system/date "1000-01"
        2024 6 #system/date "2024-06"
        9999 12 #system/date "9999-12")

      (given-thrown (system/date 2024 0)
        :message := "Invalid value for MonthOfYear (valid values 1 - 12): 0"))

    (testing "year-month-day"
      (are [year month day date] (= date (system/date year month day))
        1000 1 1 #system/date "1000-01-01"
        2024 6 15 #system/date "2024-06-15"
        9999 12 31 #system/date "9999-12-31")

      (given-thrown (system/date 2023 2 29)
        :message := "Invalid date 'February 29' as '2023' is not a leap year")))

  (testing "system equals"
    (testing "same precision"
      (testing "within date"
        (are [a b pred] (pred (system/equals a b))
          #system/date "2020" #system/date "2020" true?
          #system/date "2020" #system/date "2021" false?
          #system/date "2020" nil nil?
          #system/date "2021" #system/date "2020" false?
          #system/date "2021" #system/date "2021" true?
          #system/date "2021" nil nil?
          nil #system/date "2020" nil?
          nil #system/date "2021" nil?
          nil nil nil?

          #system/date "2020-01" #system/date "2020-01" true?
          #system/date "2020-01" #system/date "2020-02" false?
          #system/date "2020-01" nil nil?
          #system/date "2020-02" #system/date "2020-01" false?
          #system/date "2020-02" #system/date "2020-02" true?
          #system/date "2020-02" nil nil?
          nil #system/date "2020-01" nil?
          nil #system/date "2020-02" nil?
          nil nil nil?

          #system/date "2020-01-01" #system/date "2020-01-01" true?
          #system/date "2020-01-01" #system/date "2020-01-02" false?
          #system/date "2020-01-01" nil nil?
          #system/date "2020-01-02" #system/date "2020-01-01" false?
          #system/date "2020-01-02" #system/date "2020-01-02" true?
          #system/date "2020-01-02" nil nil?
          nil #system/date "2020-01-01" nil?
          nil #system/date "2020-01-02" nil?
          nil nil nil?))

      (testing "with date-time"
        (are [a b pred] (pred (system/equals a b))
          #system/date "2020" #system/date-time "2020" true?
          #system/date "2020" #system/date-time "2021" false?
          #system/date "2020-01" #system/date-time "2020-01" true?
          #system/date "2020-01" #system/date-time "2020-02" false?
          #system/date "2020-01-01" #system/date-time "2020-01-01" true?
          #system/date "2020-01-01" #system/date-time "2020-01-02" false?)))

    (testing "different precision"
      (testing "within date"
        (are [a b pred] (pred (system/equals a b))
          #system/date "2020" #system/date "2020-01" nil?
          #system/date "2020-01" #system/date "2020" nil?
          #system/date "2020-01" #system/date "2020-01-01" nil?
          #system/date "2020-01-01" #system/date "2020-01" nil?))

      (testing "with date-time"
        (are [a b pred] (pred (system/equals a b))
          #system/date "2020" #system/date-time "2020-01" nil?
          #system/date "2020-01" #system/date-time "2020" nil?
          #system/date "2020-01" #system/date-time "2020-01-01" nil?
          #system/date "2020-01-01" #system/date-time "2020-01" nil?))))

  (testing "print"
    (are [date s] (= (pr-str date) s)
      #system/date "2020" "#system/date \"2020\""
      #system/date "2020-01" "#system/date \"2020-01\""
      #system/date "2020-01-02" "#system/date \"2020-01-02\"")))

(deftest parse-date-test
  (testing "valid"
    (are [s d] (= d (system/parse-date s))
      "2020" #system/date "2020"
      "2020-01" #system/date "2020-01"
      "2020-01-02" #system/date "2020-01-02"))

  (testing "invalid"
    (are [s] (ba/incorrect? (st/with-instrument-disabled (system/parse-date s)))
      nil
      ""
      "a"
      "aaaa"
      "2020-aa"
      "2020-01-aa"
      "201"
      "20191"
      "2019-13"
      "2019-02-29")))

(deftest date-year-test
  (testing "plus years"
    (are [date amount res] (= res (.plusYears date amount))
      (DateYear/of 9998) 1 (system/date 9999)
      (DateYear/of 2) -1 (system/date 1))

    (given-thrown (.plusYears (DateYear/of 1) -1)
      :message := "Invalid value for Year (valid values 1 - 9999): 0")

    (given-thrown (.plusYears (DateYear/of 9999) 1)
      :message := "Invalid value for Year (valid values 1 - 9999): 10000")))

(deftest date-year-month-test
  (testing "plus months"
    (are [date amount res] (= res (.plusMonths date amount))
      (DateYearMonth/of 9998 12) 1 (system/date 9999 1)
      (DateYearMonth/of 9999 11) 1 (system/date 9999 12)
      (DateYearMonth/of 2 1) -1 (system/date 1 12)
      (DateYearMonth/of 1 2) -1 (system/date 1 1))

    (given-thrown (.plusMonths (DateYearMonth/of 1 1) -1)
      :message := "Invalid value for Year (valid values 1 - 9999): 0")

    (given-thrown (.plusMonths (DateYearMonth/of 9999 12) 1)
      :message := "Invalid value for Year (valid values 1 - 9999): 10000")))

(deftest date-date-test
  (testing "plus days"
    (are [date amount res] (= res (.plusDays date amount))
      (DateDate/of 9998 12 31) 1 (system/date 9999 1 1)
      (DateDate/of 9999 12 30) 1 #system/date "9999-12-31"
      (DateDate/of 2 1 1) -1 (system/date 1 12 31)
      (DateDate/of 1 1 2) -1 #system/date "0001-01-01")

    (given-thrown (.plusDays (DateDate/of 1 1 1) -1)
      :message := "Invalid value for Year (valid values 1 - 9999): 0")

    (given-thrown (.plusDays (DateDate/of 9999 12 31) 1)
      :message := "Invalid value for Year (valid values 1 - 9999): 10000")))

(deftest date-time-test
  (testing "date-time?"
    (are [date-time] (system/date-time? date-time)
      #system/date-time "2020"
      #system/date-time "2020-01"
      #system/date-time "2020-01-01"
      (system/date-time 2020 1 1 0 0 0 0)
      (system/date-time 2020 1 1 0 0 0 0 ZoneOffset/UTC))

    (are [x] (not (system/date-time? x))
      nil
      #system/date "2020"))

  (testing "equals"
    (are [a b] (= a b)
      #system/date-time "2020" #system/date-time "2020"
      #system/date-time "2020-01" #system/date-time "2020-01")

    (are [a b] (not= a b)
      #system/date-time "2020" #system/date-time "2021"
      #system/date-time "2020" #system/date "2020"

      #system/date-time "2020-01" #system/date-time "2020-02"
      #system/date-time "2020-01" #system/date "2020-01"

      #system/date-time "2020-01-01"
      #system/date-time "2020-01-02"

      #system/date-time "2020-01-01"
      #system/date "2020-01-01"))

  (testing "comparable"
    (are [a b] (pos? (compare a b))
      #system/date-time "2021" #system/date-time "2020"
      #system/date-time "2020-02" #system/date-time "2020-01"
      #system/date-time "2020-01-02" #system/date-time "2020-01-01"))

  (testing "system equals"
    (testing "same precision"
      (testing "within date-time"
        (are [a b pred] (pred (system/equals a b))
          #system/date-time "2020" #system/date-time "2020" true?
          #system/date-time "2020" #system/date-time "2021" false?
          #system/date-time "2020" nil nil?
          #system/date-time "2021" #system/date-time "2020" false?
          #system/date-time "2021" #system/date-time "2021" true?
          #system/date-time "2021" nil nil?
          nil #system/date-time "2020" nil?
          nil #system/date-time "2021" nil?
          nil nil nil?

          #system/date-time "2020-01" #system/date-time "2020-01" true?
          #system/date-time "2020-01" #system/date-time "2020-02" false?
          #system/date-time "2020-01" nil nil?
          #system/date-time "2020-02" #system/date-time "2020-01" false?
          #system/date-time "2020-02" #system/date-time "2020-02" true?
          #system/date-time "2020-02" nil nil?
          nil #system/date-time "2020-01" nil?
          nil #system/date-time "2020-02" nil?
          nil nil nil?

          #system/date-time "2020-01-01" #system/date-time "2020-01-01" true?
          #system/date-time "2020-01-01" #system/date-time "2020-01-02" false?
          #system/date-time "2020-01-01" nil nil?
          #system/date-time "2020-01-02" #system/date-time "2020-01-01" false?
          #system/date-time "2020-01-02" #system/date-time "2020-01-02" true?
          #system/date-time "2020-01-02" nil nil?
          nil #system/date-time "2020-01-01" nil?
          nil #system/date-time "2020-01-02" nil?
          nil nil nil?

          (system/date-time 2020 1 1 0 0 0 0) (system/date-time 2020 1 1 0 0 0 0) true?
          (system/date-time 2020 1 1 0 0 0 0) (system/date-time 2020 1 1 0 0 1 0) false?
          (system/date-time 2020 1 1 0 0 0 0) nil nil?
          (system/date-time 2020 1 1 0 0 1 0) (system/date-time 2020 1 1 0 0 0 0) false?
          (system/date-time 2020 1 1 0 0 1 0) (system/date-time 2020 1 1 0 0 1 0) true?
          (system/date-time 2020 1 1 0 0 1 0) nil nil?
          nil (system/date-time 2020 1 1 0 0 0 0) nil?
          nil (system/date-time 2020 1 1 0 0 1 0) nil?
          nil nil nil?

          (system/date-time 2020 1 1 0 0 0 0 ZoneOffset/UTC) (system/date-time 2020 1 1 0 0 0 0 ZoneOffset/UTC) true?
          (system/date-time 2020 1 1 0 0 0 0 ZoneOffset/UTC) (system/date-time 2020 1 1 0 0 1 0 ZoneOffset/UTC) false?
          (system/date-time 2020 1 1 0 0 0 0 ZoneOffset/UTC) nil nil?
          (system/date-time 2020 1 1 0 0 1 0 ZoneOffset/UTC) (system/date-time 2020 1 1 0 0 0 0 ZoneOffset/UTC) false?
          (system/date-time 2020 1 1 0 0 1 0 ZoneOffset/UTC) (system/date-time 2020 1 1 0 0 1 0 ZoneOffset/UTC) true?
          (system/date-time 2020 1 1 0 0 1 0 ZoneOffset/UTC) nil nil?
          nil (system/date-time 2020 1 1 0 0 0 0 ZoneOffset/UTC) nil?
          nil (system/date-time 2020 1 1 0 0 1 0 ZoneOffset/UTC) nil?
          nil nil nil?))

      (testing "with date"
        (are [a b pred] (pred (system/equals a b))
          #system/date-time "2020" #system/date "2020" true?
          #system/date-time "2020" #system/date "2021" false?
          #system/date-time "2020-01" #system/date "2020-01" true?
          #system/date-time "2020-02" #system/date "2020-01" false?
          #system/date-time "2020-01-01" #system/date "2020-01-01" true?
          #system/date-time "2020-01-02" #system/date "2020-01-01" false?)))

    (testing "different precision"
      (testing "within date-time"
        (are [a b pred] (pred (system/equals a b))
          #system/date-time "2020" #system/date-time "2020-01" nil?
          #system/date-time "2020-01" #system/date-time "2020" nil?
          #system/date-time "2020-01" #system/date-time "2020-01-01" nil?
          #system/date-time "2020-01-01" #system/date-time "2020-01" nil?
          (system/date-time 2020 1 1 0 0 0 0) (system/date-time 2020 1 1 0 0 0 0 ZoneOffset/UTC) nil?
          (system/date-time 2020 1 1 0 0 0 0 ZoneOffset/UTC) (system/date-time 2020 1 1 0 0 0 0) nil?))

      (testing "with date"
        (are [a b pred] (pred (system/equals a b))
          #system/date-time "2020-01" #system/date "2020" nil?
          #system/date-time "2020" #system/date "2020-01" nil?
          #system/date-time "2020-01-01" #system/date "2020-01" nil?
          #system/date-time "2020-01" #system/date "2020-01-01" nil?))))

  (testing "hash-code"
    (testing "DateTimeYear hash-code equals that of DateYear"
      (is (= (.hashCode #system/date-time "2020")
             (.hashCode #system/date "2020"))))

    (testing "DateTimeYearMonth hash-code equals that of DateYearMonth"
      (is (= (.hashCode #system/date-time "2020-01")
             (.hashCode #system/date "2020-01"))))

    (testing "DateTimeDate hash-code equals that of DateDate"
      (is (= (.hashCode #system/date-time "2020-01-01")
             (.hashCode #system/date "2020-01-01")))))

  (testing "Temporal"
    (testing "plus"
      (testing "year"
        (are [o amount res] (= res (time/plus o amount))
          #system/date-time "2020" (time/years 0) #system/date-time "2020"
          #system/date-time "2020" (time/years 1) #system/date-time "2021"))

      (testing "year-month"
        (are [o amount res] (= res (time/plus o amount))
          #system/date-time "2020-01" (time/months 0) #system/date-time "2020-01"
          #system/date-time "2020-01" (time/months 1) #system/date-time "2020-02"))

      (testing "year-month-day"
        (are [o amount res] (= res (time/plus o amount))
          #system/date-time "2020-01-01" (time/days 0) #system/date-time "2020-01-01"
          #system/date-time "2020-01-01" (time/days 1) #system/date-time "2020-01-02")))

    (testing "time-between"
      (testing "year"
        (are [o e n] (= n (time/time-between o e :years))
          #system/date-time "2020" #system/date-time "2020" 0
          #system/date-time "2020" #system/date-time "2021" 1
          #system/date-time "2020" #system/date "2020" 0
          #system/date-time "2020" #system/date "2021" 1
          #system/date "2020" #system/date-time "2020" 0
          #system/date "2020" #system/date-time "2021" 1))

      (testing "year-month"
        (are [o e n] (= n (time/time-between o e :months))
          #system/date-time "2020-01"
          #system/date-time "2020-01" 0
          #system/date-time "2020-01"
          #system/date-time "2020-02" 1
          #system/date-time "2020-01" #system/date "2020-01" 0
          #system/date-time "2020-01" #system/date "2020-02" 1
          #system/date "2020-01" #system/date-time "2020-01" 0
          #system/date "2020-01" #system/date-time "2020-02" 1))

      (testing "year-month-day"
        (are [o e n] (= n (time/time-between o e :days))
          #system/date-time "2020-01-01"
          #system/date-time "2020-01-01" 0
          #system/date-time "2020-01-01"
          #system/date-time "2020-01-02" 1
          #system/date-time "2020-01-01" #system/date "2020-01-01" 0
          #system/date-time "2020-01-01" #system/date "2020-01-02" 1
          #system/date "2020-01-01" #system/date-time "2020-01-01" 0
          #system/date "2020-01-01" #system/date-time "2020-01-02" 1))))

  (testing "TemporalAccessor"
    (are [o ps] (every? #(time/supports? o %) ps)
      #system/date-time "2020"
      [:year]

      #system/date-time "2020-01"
      [:year :month-of-year]

      #system/date-time "2020-01-01"
      [:year :month-of-year :day-of-month])

    (are [o p v] (= v (time/value (time/property o p)))
      #system/date-time "2020" :year 2020
      #system/date-time "2020-01" :year 2020
      #system/date-time "2020-01" :month-of-year 1
      #system/date-time "2020-01-02" :year 2020
      #system/date-time "2020-01-02" :month-of-year 1
      #system/date-time "2020-01-02" :day-of-month 2))

  (testing "print"
    (are [date s] (= (pr-str date) s)
      #system/date-time "2020" "#system/date-time \"2020\""
      #system/date-time "2020-01" "#system/date-time \"2020-01\""
      #system/date-time "2020-01-02" "#system/date-time \"2020-01-02\""
      #system/date-time "2020-01-02T00:00:00" "#system/date-time \"2020-01-02T00:00:00\""
      #system/date-time "2020-12-31T23:59:59" "#system/date-time \"2020-12-31T23:59:59\""
      #system/date-time "2020-12-31T23:59:59Z" "#system/date-time \"2020-12-31T23:59:59Z\""
      #system/date-time "2020-12-31T23:59:59.001" "#system/date-time \"2020-12-31T23:59:59.001\""
      #system/date-time "2020-12-31T23:59:59.001Z" "#system/date-time \"2020-12-31T23:59:59.001Z\"")))

(deftest parse-date-time-test
  (testing "valid"
    (are [s d] (= d (system/parse-date-time s))
      "2020" (system/date-time 2020)
      "2020-01" (system/date-time 2020 1)
      "2020-01-02" (system/date-time 2020 1 2)
      "2020-01-02T03" (system/date-time 2020 1 2 3)
      "2020-01-02T03:04" (system/date-time 2020 1 2 3 4)
      "2020-01-02T03:04:05" (system/date-time 2020 1 2 3 4 5)
      "2020-01-02T03:04:05.006" (system/date-time 2020 1 2 3 4 5 6)
      "2020-01-02T03:04Z" (system/date-time 2020 1 2 3 4 0 0 ZoneOffset/UTC)
      "2020-01-02T03:04:05Z" (system/date-time 2020 1 2 3 4 5 0 ZoneOffset/UTC)
      "2020-01-02T03:04-01:00" (system/date-time 2020 1 2 3 4 0 0 (ZoneOffset/ofHours -1))
      "2020-01-02T03:04:05-01:00" (system/date-time 2020 1 2 3 4 5 0 (ZoneOffset/ofHours -1))
      "2020-01-02T03:04:05+01:00" (system/date-time 2020 1 2 3 4 5 0 (ZoneOffset/ofHours 1))
      "0001-01-01T00:00:00Z" (system/date-time 1 1 1 0 0 0 0 ZoneOffset/UTC)
      "2020-01-02T03:04:05.006Z" (system/date-time 2020 1 2 3 4 5 6 ZoneOffset/UTC)
      "2020-01-02T03:04:05.006000Z" (system/date-time 2020 1 2 3 4 5 6 ZoneOffset/UTC)
      "2020-01-02T03:04:05.006-01:00" (system/date-time 2020 1 2 3 4 5 6 (ZoneOffset/ofHours -1))
      "2020-01-02T03:04:05.006+01:00" (system/date-time 2020 1 2 3 4 5 6 (ZoneOffset/ofHours 1))))

  (testing "invalid"
    (are [s] (ba/incorrect? (st/with-instrument-disabled (system/parse-date-time s)))
      nil
      ""
      "a"
      "aaaa"
      "2020-aa"
      "2020-01-aa"
      "201"
      "20191"
      "2019-13"
      "2019-02-29"
      "2019-02-28Taa"
      "2019-02-28T24"
      "2019-02-28T23:aa"
      "2019-02-28T23:60"
      "2019-02-28T23:59:"
      "2019-02-28T23:59:aa"
      "2019-02-28T23:59:60"
      "2019-02-28T23:59:59+aa"
      "2019-02-28T23:59:59+99"
      "2019-02-28T23:59:59+01:aa"
      "2020-01-02T03:04Za"
      "2020-01-02T03:04-01:00a"
      "2020-01-02T03:04:05.006Za"
      "2020-01-02T03:04:05.006+01:00a")))

(deftest date-time-lower-bound-test
  (testing "date-times with increasing precision have the same lower bound"
    (are [dt] (= 1577836800 (system/date-time-lower-bound dt))
      #system/date "2020"
      #system/date "2020-01"
      #system/date "2020-01-01"
      #system/date-time "2020"
      #system/date-time "2020-01"
      #system/date-time "2020-01-01"
      #system/date-time "2020-01-01T00:00:00"
      #system/date-time "2020-01-01T00:00:00Z"
      #system/date-time "2020-01-01T00:00:00.000"
      #system/date-time "2020-01-01T00:00:00.000Z")

    (testing "nil has the same lower bound as year 1"
      (is (= (system/date-time-lower-bound #system/date "0001")
             (system/date-time-lower-bound nil))))))

(deftest date-time-upper-bound-test
  (testing "date-times with increasing precision have the same upper bound"
    (are [dt] (= 1609459199 (system/date-time-upper-bound dt))
      #system/date "2020"
      #system/date "2020-12"
      #system/date "2020-12-31"
      #system/date-time "2020"
      #system/date-time "2020-12"
      #system/date-time "2020-12-31"
      #system/date-time "2020-12-31T23:59:59"
      #system/date-time "2020-12-31T23:59:59Z"
      #system/date-time "2020-12-31T23:59:59.000"
      #system/date-time "2020-12-31T23:59:59.000Z"))

  (testing "nil has the same upper bound as year 9999"
    (is (= (system/date-time-upper-bound #system/date "9999")
           (system/date-time-upper-bound nil)))))

(deftest date-time-year-test
  (testing "plus years"
    (are [date amount res] (= res (.plusYears date amount))
      (DateTimeYear/of 9998) 1 #system/date-time "9999"
      (DateTimeYear/of 2) -1 #system/date-time "0001")

    (given-thrown (.plusYears (DateTimeYear/of 1) -1)
      :message := "Invalid value for Year (valid values 1 - 9999): 0")

    (given-thrown (.plusYears (DateTimeYear/of 9999) 1)
      :message := "Invalid value for Year (valid values 1 - 9999): 10000")))

(deftest date-time-year-month-test
  (testing "plus months"
    (are [date amount res] (= res (.plusMonths date amount))
      (DateTimeYearMonth/of 9998 12) 1 #system/date-time "9999-01"
      (DateTimeYearMonth/of 9999 11) 1 #system/date-time "9999-12"
      (DateTimeYearMonth/of 2 1) -1 #system/date-time "0001-12"
      (DateTimeYearMonth/of 1 2) -1 #system/date-time "0001-01")

    (given-thrown (.plusMonths (DateTimeYearMonth/of 1 1) -1)
      :message := "Invalid value for Year (valid values 1 - 9999): 0")

    (given-thrown (.plusMonths (DateTimeYearMonth/of 9999 12) 1)
      :message := "Invalid value for Year (valid values 1 - 9999): 10000")))

(deftest date-time-date-test
  (testing "plus days"
    (are [date amount res] (= res (.plusDays date amount))
      (DateTimeDate/of 9998 12 31) 1 #system/date-time "9999-01-01"
      (DateTimeDate/of 9999 12 30) 1 #system/date-time "9999-12-31"
      (DateTimeDate/of 2 1 1) -1 #system/date-time "0001-12-31"
      (DateTimeDate/of 1 1 2) -1 #system/date-time "0001-01-01")

    (given-thrown (.plusDays (DateTimeDate/of 1 1 1) -1)
      :message := "Invalid value for Year (valid values 1 - 9999): 0")

    (given-thrown (.plusDays (DateTimeDate/of 9999 12 31) 1)
      :message := "Invalid value for Year (valid values 1 - 9999): 10000")))

(deftest time-test
  (testing "time?"
    (are [time] (true? (system/time? time))
      #system/time"03:04:05")

    (are [x] (false? (system/time? x))
      nil
      #system/date-time "2020"))

  (testing "type"
    (is (= :system/time (system/type (LocalTime/of 0 0 0)))))

  (testing "time"
    (is (= (system/time 3 4) (LocalTime/of 3 4))))

  (testing "system equals"
    (are [a b pred] (pred (system/equals a b))
      (LocalTime/of 0 0 0) (LocalTime/of 0 0 0) true?
      (LocalTime/of 0 0 0) (LocalTime/of 0 0 1) false?
      (LocalTime/of 0 0 0) nil nil?
      (LocalTime/of 0 0 1) (LocalTime/of 0 0 0) false?
      (LocalTime/of 0 0 1) (LocalTime/of 0 0 1) true?
      (LocalTime/of 0 0 1) nil nil?
      nil (LocalTime/of 0 0 0) nil?
      nil (LocalTime/of 0 0 1) nil?
      nil nil nil?

      (LocalTime/of 0 0 0) (Object.) false?
      (Object.) (LocalTime/of 0 0 0) false?))

  (testing "print"
    (are [date s] (= (pr-str date) s)
      #system/time "03:04:00" "#system/time \"03:04:00\""
      #system/time "03:04:05" "#system/time \"03:04:05\""
      #system/time "03:04:05.001" "#system/time \"03:04:05.001\"")))

(deftest parse-time-test
  (testing "valid"
    (are [s d] (= d (system/parse-time s))
      "03:04:05" (system/time 3 4 5)
      "03:04:05.1" (system/time 3 4 5 100)
      "03:04:05.01" (system/time 3 4 5 10)
      "03:04:05.006" (system/time 3 4 5 6)))

  (testing "invalid"
    (are [s] (ba/incorrect? (st/with-instrument-disabled (system/parse-time s)))
      nil
      "a"
      ""
      "25:00:00"
      "12:60:00"
      "12:12:60")))
