(ns blaze.time-test
  (:require
   [blaze.test-util :as tu]
   [blaze.time :as bt]
   [blaze.time-spec]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]])
  (:import
   [java.time Clock Duration Instant LocalDate LocalDateTime OffsetDateTime ZoneOffset]
   [java.time.temporal ChronoUnit]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private ^Instant instant-165407 (Instant/parse "2024-05-16T16:54:07Z"))

(def ^:private ^OffsetDateTime date-time-165407
  (OffsetDateTime/ofInstant instant-165407 ZoneOffset/UTC))

(def ^:private clock (Clock/fixed instant-165407 ZoneOffset/UTC))

(deftest instant-test
  (testing "without clock"
    (let [before (Instant/now)
          x (bt/instant)
          after (Instant/now)]

      (is (instance? Instant x))
      (is (not (.isBefore x before)))
      (is (not (.isAfter x after)))))

  (testing "with clock"
    (is (= instant-165407 (bt/instant clock)))))

(deftest offset-date-time-test
  (testing "without clock"
    (let [before (OffsetDateTime/now)
          x (bt/offset-date-time)
          after (OffsetDateTime/now)]

      (is (instance? OffsetDateTime x))
      (is (not (.isBefore x before)))
      (is (not (.isAfter x after)))))

  (testing "with clock"
    (is (= (OffsetDateTime/ofInstant instant-165407 ZoneOffset/UTC)
           (bt/offset-date-time clock))))

  (testing "the offset is taken from the clock"
    (is (= (ZoneOffset/ofHours 2)
           (.getOffset (bt/offset-date-time
                        (Clock/fixed instant-165407 (ZoneOffset/ofHours 2))))))))

(deftest to-instant-test
  (is (= instant-165407 (bt/to-instant date-time-165407))))

(deftest duration-test
  (testing "positive duration"
    (is (= (Duration/ofSeconds 10)
           (bt/duration instant-165407 (.plusSeconds instant-165407 10)))))

  (testing "negative duration"
    (is (= (Duration/ofSeconds -10)
           (bt/duration instant-165407 (.minusSeconds instant-165407 10)))))

  (testing "between date-times"
    (is (= (Duration/ofSeconds 10)
           (bt/duration date-time-165407 (.plusSeconds date-time-165407 10))))))

(deftest as-seconds-test
  (is (= 10 (bt/as-seconds (Duration/ofMillis 10500)))))

(deftest as-millis-test
  (is (= 10500 (bt/as-millis (Duration/ofMillis 10500)))))

(deftest plus-test
  (is (= (.plusSeconds date-time-165407 10)
         (bt/plus date-time-165407 (Duration/ofSeconds 10)))))

(def ^:private ^LocalDate date-2024-05-16 (LocalDate/of 2024 5 16))

(def ^:private ^LocalDateTime date-time-2024-05-16
  (LocalDateTime/of 2024 5 16 16 54 7))

(deftest plus-unit-test
  (testing "months"
    (is (= (LocalDate/of 2024 6 16)
           (bt/plus-unit date-2024-05-16 1 ChronoUnit/MONTHS))))

  (testing "days"
    (is (= (LocalDate/of 2024 5 18)
           (bt/plus-unit date-2024-05-16 2 ChronoUnit/DAYS))))

  (testing "nanos"
    (is (= (LocalDateTime/of 2024 5 16 16 54 8)
           (bt/plus-unit date-time-2024-05-16 1000000000 ChronoUnit/NANOS))))

  (testing "negative amount"
    (is (= (LocalDate/of 2024 4 16)
           (bt/plus-unit date-2024-05-16 -1 ChronoUnit/MONTHS))))

  (testing "a zero amount returns the temporal itself"
    (is (identical? date-2024-05-16
                    (bt/plus-unit date-2024-05-16 0 ChronoUnit/MONTHS))))

  (testing "two calls compose"
    ;; 2024-01-30 plus one month is 2024-02-29, because 2024-02-30 doesn't exist
    ;; and the day-of-month is clamped. Plus one day that is 2024-03-01.
    (is (= (LocalDate/of 2024 3 1)
           (-> (bt/plus-unit (LocalDate/of 2024 1 30) 1 ChronoUnit/MONTHS)
               (bt/plus-unit 1 ChronoUnit/DAYS)))))

  (testing "the order of two calls matters"
    ;; the finest unit first gives 2024-01-31, plus one month 2024-02-29
    (is (= (LocalDate/of 2024 2 29)
           (-> (bt/plus-unit (LocalDate/of 2024 1 30) 1 ChronoUnit/DAYS)
               (bt/plus-unit 1 ChronoUnit/MONTHS))))))
