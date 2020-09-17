(ns blaze.fhir.spec.type.system-test
  (:require
    [blaze.fhir.spec.type.system :as system]
    [blaze.fhir.spec.type.system-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]])
  (:import
    [com.google.common.hash Hashing]
    [java.time LocalDate Year YearMonth]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn murmur3 [x]
  (let [hasher (.newHasher (Hashing/murmur3_32))]
    (system/-hash-into x hasher)
    (Integer/toHexString (.asInt (.hash hasher)))))


(deftest type-test
  (are [x type] (= type (system/type x))
    nil nil
    (Object.) nil))


(deftest boolean-test
  (testing "hash-into"
    (are [b hex] (= hex (murmur3 b))
      true "70e1a2c0"
      false "30f4c306"))
  (testing "equals"
    (are [a b res] (= res (system/equals a b))
      true true true
      true false false)))


(deftest string-test
  (testing "equals"
    (are [a b res] (= res (system/equals a b))
      "a" "a" true
      "a" "b" false)))


(deftest date-test
  (testing "equals"
    (testing "same precision"
      (testing "within date"
        (are [a b res] (= res (system/equals a b))
          (Year/of 2020) (Year/of 2020) true
          (Year/of 2020) (Year/of 2021) false
          (YearMonth/of 2020 1) (YearMonth/of 2020 1) true
          (YearMonth/of 2020 1) (YearMonth/of 2020 2) false
          (LocalDate/of 2020 1 1) (LocalDate/of 2020 1 1) true
          (LocalDate/of 2020 1 1) (LocalDate/of 2020 1 2) false))
      (testing "with date-time"
        (are [a b res] (= res (system/equals a b))
          (Year/of 2020) (system/->DateTimeYear 2020) true
          (Year/of 2020) (system/->DateTimeYear 2021) false
          (YearMonth/of 2020 1) (system/->DateTimeYearMonth 2020 1) true
          (YearMonth/of 2020 1) (system/->DateTimeYearMonth 2020 2) false
          (LocalDate/of 2020 1 1) (system/->DateTimeYearMonthDay 2020 1 1) true
          (LocalDate/of 2020 1 1) (system/->DateTimeYearMonthDay 2020 1 2) false)))
    (testing "different precision"
      (testing "within date"
        (are [a b res] (= res (system/equals a b))
          (Year/of 2020) (YearMonth/of 2020 1) nil
          (YearMonth/of 2020 1) (Year/of 2020) nil
          (YearMonth/of 2020 1) (LocalDate/of 2020 1 1) nil
          (LocalDate/of 2020 1 1) (YearMonth/of 2020 1) nil))
      (testing "with date-time"
        (are [a b res] (= res (system/equals a b))
          (Year/of 2020) (system/->DateTimeYearMonth 2020 1) nil
          (YearMonth/of 2020 1) (system/->DateTimeYear 2020) nil
          (YearMonth/of 2020 1) (system/->DateTimeYearMonthDay 2020 1 1) nil
          (LocalDate/of 2020 1 1) (system/->DateTimeYearMonth 2020 1) nil)))))


(deftest date-time-test
  (testing "equals"
    (testing "same precision"
      (testing "within date-time"
        (are [a b res] (= res (system/equals a b))
          (system/->DateTimeYear 2020) (system/->DateTimeYear 2020) true
          (system/->DateTimeYear 2020) (system/->DateTimeYear 2021) false
          (system/->DateTimeYearMonth 2020 1) (system/->DateTimeYearMonth 2020 1) true
          (system/->DateTimeYearMonth 2020 1) (system/->DateTimeYearMonth 2020 2) false
          (system/->DateTimeYearMonthDay 2020 1 1) (system/->DateTimeYearMonthDay 2020 1 1) true
          (system/->DateTimeYearMonthDay 2020 1 1) (system/->DateTimeYearMonthDay 2020 1 2) false))
      (testing "with date"
        (are [a b res] (= res (system/equals a b))
          (system/->DateTimeYear 2020) (Year/of 2020) true
          (system/->DateTimeYear 2020) (Year/of 2021) false
          (system/->DateTimeYearMonth 2020 1) (YearMonth/of 2020 1) true
          (system/->DateTimeYearMonth 2020 2) (YearMonth/of 2020 1) false
          (system/->DateTimeYearMonthDay 2020 1 1) (LocalDate/of 2020 1 1) true
          (system/->DateTimeYearMonthDay 2020 1 2) (LocalDate/of 2020 1 1) false)))
    (testing "different precision"
      (testing "within date-time"
        (are [a b res] (= res (system/equals a b))
          (system/->DateTimeYear 2020) (system/->DateTimeYearMonth 2020 1) nil
          (system/->DateTimeYearMonth 2020 1) (system/->DateTimeYear 2020) nil
          (system/->DateTimeYearMonth 2020 1) (system/->DateTimeYearMonthDay 2020 1 1) nil
          (system/->DateTimeYearMonthDay 2020 1 1) (system/->DateTimeYearMonth 2020 1) nil))
      (testing "with date"
        (are [a b res] (= res (system/equals a b))
          (system/->DateTimeYearMonth 2020 1) (Year/of 2020) nil
          (system/->DateTimeYear 2020) (YearMonth/of 2020 1) nil
          (system/->DateTimeYearMonthDay 2020 1 1) (YearMonth/of 2020 1) nil
          (system/->DateTimeYearMonth 2020 1) (LocalDate/of 2020 1 1) nil))))

  (testing "hash-code"
    (testing "DateTimeYear hash-code equals that of Year"
      (is (= (.hashCode (system/->DateTimeYear 2020))
             (.hashCode (Year/of 2020)))))
    (testing "DateTimeYearMonth hash-code equals that of YearMonth"
      (is (= (.hashCode (system/->DateTimeYearMonth 2020 1))
             (.hashCode (YearMonth/of 2020 1)))))
    (testing "DateTimeYearMonthDay hash-code equals that of LocalDate"
      (is (= (.hashCode (system/->DateTimeYearMonthDay 2020 1 1))
             (.hashCode (LocalDate/of 2020 1 1)))))))
