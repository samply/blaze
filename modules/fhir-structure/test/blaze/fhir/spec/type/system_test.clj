(ns blaze.fhir.spec.type.system-test
  (:require
    [blaze.fhir.spec.type.system :as system]
    [blaze.fhir.spec.type.system-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]
    [java-time :as time])
  (:import
    [com.google.common.hash Hashing]
    [java.time LocalDate LocalDateTime Year YearMonth OffsetDateTime
               ZoneOffset LocalTime]))


(set! *warn-on-reflection* true)
(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn murmur3 [x]
  (let [hasher (.newHasher (Hashing/murmur3_32_fixed))]
    (system/-hash-into x hasher)
    (Integer/toHexString (.asInt (.hash hasher)))))


(deftest value-test
  (are [x] (system/value? x)
    true
    false
    ""
    0M
    (Year/of 2020))

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

  (testing "hash-into"
    (are [b hex] (= hex (murmur3 b))
      true "70e1a2c0"
      false "30f4c306"))

  (testing "system equals"
    (are [a b res] (= res (system/equals a b))
      true true true
      true false false
      true nil nil
      false true false
      false false true
      false nil nil
      nil true nil
      nil false nil
      nil nil nil)))


(deftest integer-test
  (testing "long?"
    (is (true? (system/integer? (int 0))))
    (is (false? (system/integer? 0))))

  (testing "type"
    (is (= :system/integer (system/type (int 0)))))

  (testing "hash-into"
    (are [i hex] (= hex (murmur3 i))
      (int 0) "60ebe6c8"
      (int 1) "bec48b04"))

  (testing "system equals"
    (are [a b res] (= res (system/equals a b))
      (int 0) (int 0) true
      (int 0) (int 1) false
      (int 0) nil nil
      (int 1) (int 0) false
      (int 1) (int 1) true
      (int 1) nil nil
      nil (int 0) nil
      nil (int 1) nil
      nil nil nil)))


(deftest long-test
  (testing "long?"
    (is (true? (system/long? 0)))
    (is (false? (system/long? (int 0)))))

  (testing "hash-into"
    (are [l hex] (= hex (murmur3 l))
      0 "f395bb28"
      1 "aec1a7fd"))

  (testing "system equals"
    (are [a b res] (= res (system/equals a b))
      0 0 true
      0 1 false
      0 nil nil
      1 0 false
      1 1 true
      1 nil nil
      nil 0 nil
      nil 1 nil
      nil nil nil)))


(deftest string-test
  (testing "string?"
    (is (true? (system/string? "")))
    (is (false? (system/string? nil))))

  (testing "hash-into"
    (are [s hex] (= hex (murmur3 s))
      "" "e45ad1ab"
      "a" "e7da87f3"
      "b" "5edf6843"))

  (testing "system equals"
    (are [a b res] (= res (system/equals a b))
      "a" "a" true
      "a" "b" false
      "a" nil nil
      "b" "a" false
      "b" "b" true
      "b" nil nil
      nil "a" nil
      nil "b" nil
      nil nil nil)))


(deftest decimal-test
  (testing "decimal?"
    (is (true? (system/decimal? 1M)))
    (is (false? (system/decimal? 1))))

  (testing "hash-into"
    (are [d hex] (= hex (murmur3 d))
      0M "bdef11d6"
      1M "8979c5c4"))

  (testing "system equals"
    (are [a b res] (= res (system/equals a b))
      0M 0M true
      0M 1M false
      0M nil nil
      1M 0M false
      1M 1M true
      1M nil nil
      nil 0M nil
      nil 1M nil
      nil nil nil)))


(deftest parse-decimal-test
  (testing "valid"
    (are [s d] (= d (system/parse-decimal s))
      "1" 1M
      "1.1" 1.1M))

  (testing "invalid"
    (are [s] (= ::anom/incorrect (::anom/category (system/parse-decimal s)))
      "a"
      "")))


(deftest date-test
  (testing "date?"
    (are [date] (true? (system/date? date))
      (Year/of 2020)
      (YearMonth/of 2020 1)
      (LocalDate/of 2020 1 1))

    (are [x] (false? (system/date? x))
      nil
      (system/date-time 2020)))

  (testing "system equals"
    (testing "same precision"
      (testing "within date"
        (are [a b res] (= res (system/equals a b))
          (Year/of 2020) (Year/of 2020) true
          (Year/of 2020) (Year/of 2021) false
          (Year/of 2020) nil nil
          (Year/of 2021) (Year/of 2020) false
          (Year/of 2021) (Year/of 2021) true
          (Year/of 2021) nil nil
          nil (Year/of 2020) nil
          nil (Year/of 2021) nil
          nil nil nil

          (YearMonth/of 2020 1) (YearMonth/of 2020 1) true
          (YearMonth/of 2020 1) (YearMonth/of 2020 2) false
          (YearMonth/of 2020 1) nil nil
          (YearMonth/of 2020 2) (YearMonth/of 2020 1) false
          (YearMonth/of 2020 2) (YearMonth/of 2020 2) true
          (YearMonth/of 2020 2) nil nil
          nil (YearMonth/of 2020 1) nil
          nil (YearMonth/of 2020 2) nil
          nil nil nil

          (LocalDate/of 2020 1 1) (LocalDate/of 2020 1 1) true
          (LocalDate/of 2020 1 1) (LocalDate/of 2020 1 2) false
          (LocalDate/of 2020 1 1) nil nil
          (LocalDate/of 2020 1 2) (LocalDate/of 2020 1 1) false
          (LocalDate/of 2020 1 2) (LocalDate/of 2020 1 2) true
          (LocalDate/of 2020 1 2) nil nil
          nil (LocalDate/of 2020 1 1) nil
          nil (LocalDate/of 2020 1 2) nil
          nil nil nil))

      (testing "with date-time"
        (are [a b res] (= res (system/equals a b))
          (Year/of 2020) (system/date-time 2020) true
          (Year/of 2020) (system/date-time 2021) false
          (YearMonth/of 2020 1) (system/date-time 2020 1) true
          (YearMonth/of 2020 1) (system/date-time 2020 2) false
          (LocalDate/of 2020 1 1) (system/date-time 2020 1 1) true
          (LocalDate/of 2020 1 1) (system/date-time 2020 1 2) false)))

    (testing "different precision"
      (testing "within date"
        (are [a b res] (= res (system/equals a b))
          (Year/of 2020) (YearMonth/of 2020 1) nil
          (YearMonth/of 2020 1) (Year/of 2020) nil
          (YearMonth/of 2020 1) (LocalDate/of 2020 1 1) nil
          (LocalDate/of 2020 1 1) (YearMonth/of 2020 1) nil))

      (testing "with date-time"
        (are [a b res] (= res (system/equals a b))
          (Year/of 2020) (system/date-time 2020 1) nil
          (YearMonth/of 2020 1) (system/date-time 2020) nil
          (YearMonth/of 2020 1) (system/date-time 2020 1 1) nil
          (LocalDate/of 2020 1 1) (system/date-time 2020 1) nil)))))


(deftest parse-date-test
  (testing "valid"
    (are [s d] (= d (system/parse-date s))
      "2020" (system/date 2020)
      "2020-01" (system/date 2020 1)
      "2020-01-02" (system/date 2020 1 2)))

  (testing "invalid"
    (are [s] (= ::anom/incorrect (::anom/category (system/parse-date s)))
      "a"
      ""
      "2019-13"
      "2019-02-29")))


(deftest date-time-test
  (testing "date-time?"
    (are [date-time] (system/date-time? date-time)
      (system/date-time 2020)
      (system/date-time 2020 1)
      (system/date-time 2020 1 1)
      (LocalDateTime/of 2020 1 1 0 0 0 0)
      (OffsetDateTime/of 2020 1 1 0 0 0 0 (ZoneOffset/UTC)))

    (are [x] (not (system/date-time? x))
      nil
      (Year/of 2020)))

  (testing "equals"
    (are [a b] (= a b)
      (system/date-time 2020) (system/date-time 2020)
      (system/date-time 2020 1) (system/date-time 2020 1))

    (are [a b] (not= a b)
      (system/date-time 2020) (system/date-time 2021)
      (system/date-time 2020) (Year/of 2020)

      (system/date-time 2020 1) (system/date-time 2020 2)
      (system/date-time 2020 1) (YearMonth/of 2020 1)

      (system/date-time 2020 1 1)
      (system/date-time 2020 1 2)

      (system/date-time 2020 1 1)
      (LocalDate/of 2020 1 1)))

  (testing "comparable"
    (are [a b] (pos? (.compareTo a b))
      (system/date-time 2021) (system/date-time 2020)
      (system/date-time 2020 2) (system/date-time 2020 1)
      (system/date-time 2020 1 2) (system/date-time 2020 1 1)))

  (testing "system equals"
    (testing "same precision"
      (testing "within date-time"
        (are [a b res] (= res (system/equals a b))
          (system/date-time 2020) (system/date-time 2020) true
          (system/date-time 2020) (system/date-time 2021) false
          (system/date-time 2020) nil nil
          (system/date-time 2021) (system/date-time 2020) false
          (system/date-time 2021) (system/date-time 2021) true
          (system/date-time 2021) nil nil
          nil (system/date-time 2020) nil
          nil (system/date-time 2021) nil
          nil nil nil

          (system/date-time 2020 1) (system/date-time 2020 1) true
          (system/date-time 2020 1) (system/date-time 2020 2) false
          (system/date-time 2020 1) nil nil
          (system/date-time 2020 2) (system/date-time 2020 1) false
          (system/date-time 2020 2) (system/date-time 2020 2) true
          (system/date-time 2020 2) nil nil
          nil (system/date-time 2020 1) nil
          nil (system/date-time 2020 2) nil
          nil nil nil

          (system/date-time 2020 1 1) (system/date-time 2020 1 1) true
          (system/date-time 2020 1 1) (system/date-time 2020 1 2) false
          (system/date-time 2020 1 1) nil nil
          (system/date-time 2020 1 2) (system/date-time 2020 1 1) false
          (system/date-time 2020 1 2) (system/date-time 2020 1 2) true
          (system/date-time 2020 1 2) nil nil
          nil (system/date-time 2020 1 1) nil
          nil (system/date-time 2020 1 2) nil
          nil nil nil

          (LocalDateTime/of 2020 1 1 0 0 0 0) (LocalDateTime/of 2020 1 1 0 0 0 0) true
          (LocalDateTime/of 2020 1 1 0 0 0 0) (LocalDateTime/of 2020 1 1 0 0 1 0) false
          (LocalDateTime/of 2020 1 1 0 0 0 0) nil nil
          (LocalDateTime/of 2020 1 1 0 0 1 0) (LocalDateTime/of 2020 1 1 0 0 0 0) false
          (LocalDateTime/of 2020 1 1 0 0 1 0) (LocalDateTime/of 2020 1 1 0 0 1 0) true
          (LocalDateTime/of 2020 1 1 0 0 1 0) nil nil
          nil (LocalDateTime/of 2020 1 1 0 0 0 0) nil
          nil (LocalDateTime/of 2020 1 1 0 0 1 0) nil
          nil nil nil

          (OffsetDateTime/of 2020 1 1 0 0 0 0 (ZoneOffset/UTC)) (OffsetDateTime/of 2020 1 1 0 0 0 0 (ZoneOffset/UTC)) true
          (OffsetDateTime/of 2020 1 1 0 0 0 0 (ZoneOffset/UTC)) (OffsetDateTime/of 2020 1 1 0 0 1 0 (ZoneOffset/UTC)) false
          (OffsetDateTime/of 2020 1 1 0 0 0 0 (ZoneOffset/UTC)) nil nil
          (OffsetDateTime/of 2020 1 1 0 0 1 0 (ZoneOffset/UTC)) (OffsetDateTime/of 2020 1 1 0 0 0 0 (ZoneOffset/UTC)) false
          (OffsetDateTime/of 2020 1 1 0 0 1 0 (ZoneOffset/UTC)) (OffsetDateTime/of 2020 1 1 0 0 1 0 (ZoneOffset/UTC)) true
          (OffsetDateTime/of 2020 1 1 0 0 1 0 (ZoneOffset/UTC)) nil nil
          nil (OffsetDateTime/of 2020 1 1 0 0 0 0 (ZoneOffset/UTC)) nil
          nil (OffsetDateTime/of 2020 1 1 0 0 1 0 (ZoneOffset/UTC)) nil
          nil nil nil))

      (testing "with date"
        (are [a b res] (= res (system/equals a b))
          (system/date-time 2020) (Year/of 2020) true
          (system/date-time 2020) (Year/of 2021) false
          (system/date-time 2020 1) (YearMonth/of 2020 1) true
          (system/date-time 2020 2) (YearMonth/of 2020 1) false
          (system/date-time 2020 1 1) (LocalDate/of 2020 1 1) true
          (system/date-time 2020 1 2) (LocalDate/of 2020 1 1) false)))

    (testing "different precision"
      (testing "within date-time"
        (are [a b res] (= res (system/equals a b))
          (system/date-time 2020) (system/date-time 2020 1) nil
          (system/date-time 2020 1) (system/date-time 2020) nil
          (system/date-time 2020 1) (system/date-time 2020 1 1) nil
          (system/date-time 2020 1 1) (system/date-time 2020 1) nil
          (LocalDateTime/of 2020 1 1 0 0 0 0) (OffsetDateTime/of 2020 1 1 0 0 0 0 (ZoneOffset/UTC)) nil
          (OffsetDateTime/of 2020 1 1 0 0 0 0 (ZoneOffset/UTC)) (LocalDateTime/of 2020 1 1 0 0 0 0) nil))

      (testing "with date"
        (are [a b res] (= res (system/equals a b))
          (system/date-time 2020 1) (Year/of 2020) nil
          (system/date-time 2020) (YearMonth/of 2020 1) nil
          (system/date-time 2020 1 1) (YearMonth/of 2020 1) nil
          (system/date-time 2020 1) (LocalDate/of 2020 1 1) nil))))

  (testing "hash-code"
    (testing "DateTimeYear hash-code equals that of Year"
      (is (= (.hashCode ^Object (system/date-time 2020))
             (.hashCode (Year/of 2020)))))

    (testing "DateTimeYearMonth hash-code equals that of YearMonth"
      (is (= (.hashCode ^Object (system/date-time 2020 1))
             (.hashCode (YearMonth/of 2020 1)))))

    (testing "DateTimeYearMonthDay hash-code equals that of LocalDate"
      (is (= (.hashCode ^Object (system/date-time 2020 1 1))
             (.hashCode (LocalDate/of 2020 1 1))))))

  (testing "Temporal"
    (testing "after?"
      (testing "year"
        (are [a b] (time/after? a b)
          (system/date-time 2021) (system/date-time 2020)))

      (testing "year-month"
        (are [a b] (time/after? a b)
          (system/date-time 2020 2) (system/date-time 2020 1)))

      (testing "year-month-day"
        (are [a b] (time/after? a b)
          (system/date-time 2020 1 2) (system/date-time 2020 1 1))))

    (testing "plus"
      (testing "year"
        (are [o amount res] (= res (time/plus o amount))
          (system/date-time 2020) (time/years 0) (system/date-time 2020)
          (system/date-time 2020) (time/years 1) (system/date-time 2021)))

      (testing "year-month"
        (are [o amount res] (= res (time/plus o amount))
          (system/date-time 2020 1) (time/months 0) (system/date-time 2020 1)
          (system/date-time 2020 1) (time/months 1) (system/date-time 2020 2)))

      (testing "year-month-day"
        (are [o amount res] (= res (time/plus o amount))
          (system/date-time 2020 1 1) (time/days 0) (system/date-time 2020 1 1)
          (system/date-time 2020 1 1) (time/days 1) (system/date-time 2020 1 2))))

    (testing "time-between"
      (testing "year"
        (are [o e n] (= n (time/time-between o e :years))
          (system/date-time 2020) (system/date-time 2020) 0
          (system/date-time 2020) (system/date-time 2021) 1
          (system/date-time 2020) (Year/of 2020) 0
          (system/date-time 2020) (Year/of 2021) 1
          (Year/of 2020) (system/date-time 2020) 0
          (Year/of 2020) (system/date-time 2021) 1))

      (testing "year-month"
        (are [o e n] (= n (time/time-between o e :months))
          (system/date-time 2020 1)
          (system/date-time 2020 1) 0
          (system/date-time 2020 1)
          (system/date-time 2020 2) 1
          (system/date-time 2020 1) (YearMonth/of 2020 1) 0
          (system/date-time 2020 1) (YearMonth/of 2020 2) 1
          (YearMonth/of 2020 1) (system/date-time 2020 1) 0
          (YearMonth/of 2020 1) (system/date-time 2020 2) 1))

      (testing "year-month-day"
        (are [o e n] (= n (time/time-between o e :days))
          (system/date-time 2020 1 1)
          (system/date-time 2020 1 1) 0
          (system/date-time 2020 1 1)
          (system/date-time 2020 1 2) 1
          (system/date-time 2020 1 1) (LocalDate/of 2020 1 1) 0
          (system/date-time 2020 1 1) (LocalDate/of 2020 1 2) 1
          (LocalDate/of 2020 1 1) (system/date-time 2020 1 1) 0
          (LocalDate/of 2020 1 1) (system/date-time 2020 1 2) 1))))

  (testing "TemporalAccessor"
    (are [o ps] (every? #(time/supports? o %) ps)
      (system/date-time 2020)
      [:year]

      (system/date-time 2020 1)
      [:year :month-of-year]

      (system/date-time 2020 1 1)
      [:year :month-of-year :day-of-month])

    (are [o p v] (= v (time/value (time/property o p)))
      (system/date-time 2020) :year 2020
      (system/date-time 2020 1) :year 2020
      (system/date-time 2020 1) :month-of-year 1
      (system/date-time 2020 1 2) :year 2020
      (system/date-time 2020 1 2) :month-of-year 1
      (system/date-time 2020 1 2) :day-of-month 2)))


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
      "2020-01-02T03:04:05Z"
      (system/date-time 2020 1 2 3 4 5 0 ZoneOffset/UTC)
      "2020-01-02T03:04:05-01:00"
      (system/date-time 2020 1 2 3 4 5 0 (ZoneOffset/ofHours -1))
      "2020-01-02T03:04:05+01:00"
      (system/date-time 2020 1 2 3 4 5 0 (ZoneOffset/ofHours 1))
      "2020-01-02T03:04:05.006Z"
      (system/date-time 2020 1 2 3 4 5 6 ZoneOffset/UTC)
      "2020-01-02T03:04:05.006-01:00"
      (system/date-time 2020 1 2 3 4 5 6 (ZoneOffset/ofHours -1))
      "2020-01-02T03:04:05.006+01:00"
      (system/date-time 2020 1 2 3 4 5 6 (ZoneOffset/ofHours 1))))

  (testing "invalid"
    (are [s] (= ::anom/incorrect (::anom/category (system/parse-date-time s)))
      "a"
      ""
      "2019-13"
      "2019-02-29")))


(deftest time-test
  (testing "type"
    (is (= :system/time (system/type (LocalTime/of 0 0 0)))))

  (testing "time"
    (is (= (system/time 3 4) (LocalTime/of 3 4))))

  (testing "system equals"
    (are [a b res] (= res (system/equals a b))
      (LocalTime/of 0 0 0) (LocalTime/of 0 0 0) true
      (LocalTime/of 0 0 0) (LocalTime/of 0 0 1) false
      (LocalTime/of 0 0 0) nil nil
      (LocalTime/of 0 0 1) (LocalTime/of 0 0 0) false
      (LocalTime/of 0 0 1) (LocalTime/of 0 0 1) true
      (LocalTime/of 0 0 1) nil nil
      nil (LocalTime/of 0 0 0) nil
      nil (LocalTime/of 0 0 1) nil
      nil nil nil

      (LocalTime/of 0 0 0) (Object.) false
      (Object.) (LocalTime/of 0 0 0) false)))


(deftest parse-time-test
  (testing "valid"
    (are [s d] (= d (system/parse-time s))
      "03:04:05" (system/time 3 4 5)
      "03:04:05.1" (system/time 3 4 5 100)
      "03:04:05.01" (system/time 3 4 5 10)
      "03:04:05.006" (system/time 3 4 5 6)))

  (testing "invalid"
    (are [s] (= ::anom/incorrect (::anom/category (system/parse-time s)))
      "a"
      ""
      "25:00:00"
      "12:60:00"
      "12:12:60")))
