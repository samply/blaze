(ns blaze.elm.date-time-test
  (:require
   [blaze.elm.compiler :as c]
   [blaze.elm.date-time :as date-time]
   [blaze.elm.date-time-spec]
   [blaze.elm.protocols :as p]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]])
  (:import
   [java.time.temporal ChronoUnit]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest print-form-test
  (are [date res] (= res (pr-str (c/form date)))
    #system/date"2023" "#system/date \"2023\""
    #system/date"2023-11" "#system/date \"2023-11\""
    #system/date"2023-11-02" "#system/date \"2023-11-02\""
    #system/date-time"2023" "#system/date-time \"2023\""
    #system/date-time"2023-11" "#system/date-time \"2023-11\""
    #system/date-time"2023-11-02" "#system/date-time \"2023-11-02\""
    #system/date-time"2023-11-02T14:49" "#system/date-time \"2023-11-02T14:49:00\""))

(def ^:private now-utc #system/date-time "2025-01-01T00:00:00Z")

;; 2025-02-17T23:30:00 in the offset of now
(def ^:private x #system/date-time "2025-02-18T00:30:00+01:00")

(deftest temporal?-test
  (is (date-time/temporal? x)))

(deftest equal-test
  (testing "is compared in the offset of now"
    (are [a b pred] (pred (p/equal a b now-utc))
      x #system/date-time "2025-02-17T23:30:00" true?
      x #system/date-time "2025-02-18T00:30:00" false?
      #system/date-time "2025-02-17T23:30:00" x true?
      #system/date-time "2025-02-18T00:30:00" x false?
      x #system/date-time "2025-02-17T23:30:00Z" true?
      x #system/date-time "2025-02-17T23:31:00Z" false?
      x nil nil?
      #system/date-time "2025-02-17T23:30:00" nil nil?))

  (testing "values with different offsets, none of them the offset of now, are
           compared in the offset of now"
    (are [a b pred] (pred (p/equal a b now-utc))
      x #system/date-time "2025-02-18T01:30:00+02:00" true?
      #system/date-time "2025-02-18T01:30:00+02:00" x true?
      x #system/date-time "2025-02-18T01:31:00+02:00" false?
      #system/date-time "2025-02-18T01:31:00+02:00" x false?))

  (testing "values with the same offset, not the offset of now, are compared as
           written"
    (are [a b pred] (pred (p/equal a b now-utc))
      x #system/date-time "2025-02-18T00:30:00+01:00" true?
      x #system/date-time "2025-02-18T00:31:00+01:00" false?)))

(deftest equivalent-test
  (testing "is compared in the offset of now"
    (are [a b pred] (pred (p/equivalent a b now-utc))
      x #system/date-time "2025-02-17T23:30:00" true?
      x #system/date-time "2025-02-18T00:30:00" false?
      #system/date-time "2025-02-17T23:30:00" x true?
      #system/date-time "2025-02-18T00:30:00" x false?
      x #system/date-time "2025-02-17T23:30:00Z" true?
      x nil false?
      #system/date-time "2025-02-17T23:30:00" nil false?))

  (testing "values with different offsets are compared in the offset of now"
    (are [a b pred] (pred (p/equivalent a b now-utc))
      x #system/date-time "2025-02-17T23:31:00Z" false?
      x #system/date-time "2025-02-18T01:30:00+02:00" true?
      #system/date-time "2025-02-18T01:30:00+02:00" x true?
      x #system/date-time "2025-02-18T01:31:00+02:00" false?
      #system/date-time "2025-02-18T01:31:00+02:00" x false?))

  (testing "values with the same offset, not the offset of now, are compared as
           written"
    (are [a b pred] (pred (p/equivalent a b now-utc))
      x #system/date-time "2025-02-18T00:30:00+01:00" true?
      x #system/date-time "2025-02-18T00:31:00+01:00" false?)))

(deftest comparison-test
  (testing "with full precision the values are compared in the offset of now"
    (are [a b] (and (true? (p/less a b now-utc))
                    (true? (p/less-or-equal a b now-utc))
                    (true? (p/greater b a now-utc))
                    (true? (p/greater-or-equal b a now-utc)))
      x #system/date-time "2025-02-17T23:31:00"
      #system/date-time "2025-02-17T23:29:00" x
      x #system/date-time "2025-02-17T23:31:00Z"))

  (testing "with day precision the values are compared as written"
    (are [a b] (and (true? (p/less a b now-utc))
                    (true? (p/less-or-equal a b now-utc))
                    (true? (p/greater b a now-utc))
                    (true? (p/greater-or-equal b a now-utc)))
      #system/date-time "2025-02-17" x
      x #system/date-time "2025-02-19"))

  (testing "nil"
    (are [op] (nil? (op x nil now-utc))
      p/less p/less-or-equal p/greater p/greater-or-equal)))

(deftest arithmetic-test
  (testing "keeps the offset"
    (are [res a] (= res a)
      #system/date-time "2025-03-18T00:30:00+01:00" (p/add x (date-time/period 0 1 0))
      #system/date-time "2025-02-18T01:30:00+01:00" (p/add x (date-time/period 0 0 3600000))
      #system/date-time "2025-01-18T00:30:00+01:00" (p/subtract x (date-time/period 0 1 0))
      #system/date-time "2025-02-18T00:29:59.999+01:00" (p/predecessor x)
      #system/date-time "2025-02-18T00:30:00.001+01:00" (p/successor x)))

  (testing "out of range"
    (are [a] (nil? a)
      (p/subtract x (date-time/period 2025 0 0))
      (p/predecessor #system/date-time "0001-01-01T00:00:00+01:00")
      (p/successor #system/date-time "9999-12-31T23:59:59.999+01:00")))

  (testing "with invalid RHS"
    (given-thrown (p/add x 1)
      :message := "Invalid RHS adding to LocalDateTime. Expected Period but was `class java.lang.Long`.")))

(deftest date-from-test
  (testing "is taken as written"
    (is (= #system/date "2025-02-18" (p/date-from x)))))

(deftest date-time-component-from-test
  (testing "is taken as written"
    (are [precision res] (= res (p/date-time-component-from x precision))
      ChronoUnit/DAYS 18
      ChronoUnit/HOURS 0)))

(deftest difference-between-test
  (testing "the values are normalized to the offset of now at all precisions"
    (are [a b precision res] (= res (p/difference-between a b precision now-utc))
      x #system/date-time "2025-02-18T02:00:00" ChronoUnit/HOURS 3
      #system/date-time "2025-02-18T02:00:00" x ChronoUnit/HOURS -3
      x #system/date-time "2025-02-18T02:00:00Z" ChronoUnit/HOURS 3
      x #system/date-time "2025-02-20T00:00:00" ChronoUnit/DAYS 3
      #system/date-time "2025-02-20T00:00:00" x ChronoUnit/DAYS -3
      #system/date-time "2025-02-20" x ChronoUnit/DAYS -3)))

(deftest duration-between-test
  (testing "the values are normalized to the offset of now at all precisions"
    (are [a b precision res] (= res (p/duration-between a b precision now-utc))
      x #system/date-time "2025-02-18T02:00:00" ChronoUnit/HOURS 2
      #system/date-time "2025-02-18T02:00:00" x ChronoUnit/HOURS -2
      x #system/date-time "2025-02-18T02:00:00Z" ChronoUnit/HOURS 2
      x #system/date-time "2025-02-20T00:00:00" ChronoUnit/DAYS 2
      #system/date-time "2025-02-20T00:00:00" x ChronoUnit/DAYS -2
      #system/date-time "2025-02-20" x ChronoUnit/DAYS -3)))

(deftest same-as-test
  (testing "at day precision the values are taken as written"
    (are [a b pred] (pred (p/same-as a b ChronoUnit/DAYS now-utc))
      x #system/date-time "2025-02-18T12:00:00" true?
      #system/date-time "2025-02-18T12:00:00" x true?
      x #system/date-time "2025-02-18" true?
      #system/date-time "2025-02-18" x true?
      x #system/date-time "2025-02-17T23:30:00" false?))

  (testing "at hour precision the values are compared in the offset of now"
    (are [a b pred] (pred (p/same-as a b ChronoUnit/HOURS now-utc))
      x #system/date-time "2025-02-17T23:00:00" true?
      #system/date-time "2025-02-17T23:00:00" x true?
      x #system/date-time "2025-02-18T00:00:00" false?))

  (testing "at hour precision values with the same offset are compared as written"
    (are [a b pred] (pred (p/same-as a b ChronoUnit/HOURS
                                     #system/date-time "2025-01-01T00:00:00+05:30"))
      #system/date-time "2025-02-18T10:50:00+01:00"
      #system/date-time "2025-02-18T11:10:00+01:00" false?

      #system/date-time "2025-02-18T10:50:00+01:00"
      #system/date-time "2025-02-18T12:10:00+02:00" true?))

  (testing "without precision the values are compared in the offset of now"
    (are [a b pred] (pred (p/same-as a b nil now-utc))
      x #system/date-time "2025-02-17T23:30:00" true?
      #system/date-time "2025-02-17T23:30:00" x true?))

  (testing "nil"
    (is (nil? (p/same-as x nil ChronoUnit/DAYS now-utc)))))

(deftest same-or-before-test
  (are [a b precision pred] (pred (p/same-or-before a b precision now-utc))
    x #system/date-time "2025-02-17T23:45:00" ChronoUnit/DAYS false?
    #system/date-time "2025-02-17T23:45:00" x ChronoUnit/DAYS true?
    x #system/date-time "2025-02-17T23:45:00" ChronoUnit/HOURS true?
    x #system/date-time "2025-02-17T23:29:00" nil false?
    #system/date-time "2025-02-17T23:29:00" x nil true?))

(deftest same-or-after-test
  (are [a b precision pred] (pred (p/same-or-after a b precision now-utc))
    x #system/date-time "2025-02-18T00:15:00" ChronoUnit/DAYS true?
    #system/date-time "2025-02-18T00:15:00" x ChronoUnit/DAYS true?
    x #system/date-time "2025-02-18T00:15:00" ChronoUnit/HOURS false?
    x #system/date-time "2025-02-17T23:29:00" nil true?
    #system/date-time "2025-02-17T23:31:00" x nil true?))

(deftest after-test
  (are [a b precision pred] (pred (p/after a b precision now-utc))
    x #system/date-time "2025-02-17T23:59:00" ChronoUnit/DAYS true?
    #system/date-time "2025-02-19T00:00:00" x ChronoUnit/DAYS true?
    x #system/date-time "2025-02-17T23:59:00" ChronoUnit/HOURS false?
    x #system/date-time "2025-02-17T23:29:00" nil true?
    x #system/date-time "2025-02-17" ChronoUnit/HOURS true?))

(deftest before-test
  (are [a b precision pred] (pred (p/before a b precision now-utc))
    x #system/date-time "2025-02-18T12:00:00" ChronoUnit/DAYS false?
    #system/date-time "2025-02-17T23:59:00" x ChronoUnit/DAYS true?
    x #system/date-time "2025-02-18T00:00:00" ChronoUnit/HOURS true?
    x #system/date-time "2025-02-17T23:31:00" nil true?))

(deftest type-conversion-test
  (testing "ToDate takes the date as written"
    (is (= #system/date "2025-02-18" (p/to-date x now-utc))))

  (testing "ToDateTime keeps the offset"
    (is (identical? x (p/to-date-time x now-utc))))

  (testing "ToString keeps the offset"
    (is (= "2025-02-18T00:30+01:00" (p/to-string x))))

  (testing "ToTime takes the time in the offset of now"
    (is (= #system/time "23:30:00" (p/to-time x now-utc)))))
