(ns blaze.time-test
  (:require
   [blaze.test-util :as tu]
   [blaze.time :as bt]
   [blaze.time-spec]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]])
  (:import
   [java.time Clock Instant OffsetDateTime ZoneOffset]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private instant-165407 (Instant/parse "2024-05-16T16:54:07Z"))

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
