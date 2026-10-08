(ns blaze.luid-test
  (:require
   [blaze.luid :as luid]
   [blaze.luid-spec]
   [blaze.luid.impl :as impl]
   [blaze.test-util :as tu]
   [clojure.math :as math]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [java-time.api :as time])
  (:import
   [java.time Clock]
   [java.util Random]
   [java.util.concurrent ThreadLocalRandom]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def clock (time/fixed-clock (time/instant 0) "UTC"))

(def max-clock (time/fixed-clock (time/instant impl/timestamp-mask) "UTC"))

(def overflow-clock (time/fixed-clock (time/instant (inc impl/timestamp-mask)) "UTC"))

(def negative-clock (time/fixed-clock (time/instant -1) "UTC"))

(defn fixed-random [n]
  (proxy [Random] []
    (nextLong []
      n)))

(deftest luid-test
  (testing "length is 16 chars"
    (dotimes [_ 1000]
      (is (s/valid? :blaze/luid (luid/luid (time/system-clock) (ThreadLocalRandom/current))))))

  (testing "maximum timestamp"
    (is (= "7777777777777777" (luid/luid max-clock (fixed-random -1)))))

  (testing "timestamp overflow"
    (doseq [clock [overflow-clock negative-clock]]
      (is (thrown-with-msg? ArithmeticException #"LUID timestamp overflow\."
                            (luid/luid clock (fixed-random 0)))))))

(defn p [k bit]
  (/ (math/pow k 2.0) (* 2.0 (math/pow 2.0 bit))))

(defn n [a p]
  (/ (math/log (- 1 a))
     (math/log (- 1 p))))

(deftest collision-test
  (testing "when generating 1,000 LUIDs per millisecond"
    (testing "it takes between 310,000 and 320,000 occasions to reach"
      (testing "a 90% probability of a collision"
        (is (< 310000 (n 0.9 (p 1000 36)) 320000)))))

  (testing "when generating 10,000 LUIDs per millisecond"
    (testing "it takes between 3100 and 3200 occasions to reach"
      (testing "a 90% probability of a collision"
        (is (< 3100 (n 0.9 (p 10000 36)) 3200))))))

(deftest generator-test
  (testing "first 2 LUIDs"
    (let [gen (luid/generator clock (fixed-random 0))]
      (is (= (luid/head gen) (luid/luid clock (fixed-random 0))))
      (is (= (luid/head (luid/next gen)) (luid/luid clock (fixed-random 1))))))

  (testing "increments timestamp on entropy exhaustion"
    (let [gen (luid/generator clock (fixed-random impl/entropy-mask))]
      (is (= (luid/head gen) (luid/luid clock (fixed-random impl/entropy-mask))))
      (is (= (luid/head (luid/next gen)) (luid/luid (Clock/offset clock (time/millis 1)) (fixed-random 0)))))

    (testing "with a random long having bits set above the entropy"
      (doseq [n [-1 Long/MAX_VALUE]]
        (let [gen (luid/generator clock (fixed-random n))]
          (is (= (luid/head gen) (luid/luid clock (fixed-random n))))
          (is (= (luid/head (luid/next gen)) (luid/luid (Clock/offset clock (time/millis 1)) (fixed-random 0))))))))

  (testing "maximum timestamp"
    (let [gen (luid/generator max-clock (fixed-random (dec impl/entropy-mask)))]
      (is (= "7777777777777776" (luid/head gen)))
      (is (= "7777777777777777" (luid/head (luid/next gen))))

      (testing "timestamp overflow on entropy exhaustion"
        (is (thrown-with-msg? ArithmeticException #"LUID timestamp overflow\."
                              (luid/next (luid/next gen)))))))

  (testing "timestamp overflow"
    (doseq [clock [overflow-clock negative-clock]]
      (is (thrown-with-msg? ArithmeticException #"LUID timestamp overflow\."
                            (luid/generator clock (fixed-random 0)))))))
