(ns blaze.luid-test
  (:require
    [blaze.luid :as luid]
    [clojure.test :refer [deftest is testing]])
  (:import
    [java.time Instant]))


(def millis-2020
  (.toEpochMilli (Instant/parse "2020-01-01T00:00:00Z")))


(deftest internal-luid-test
  (testing "maximum time"
    (testing "maximum entropy"
      (is (= (apply str (repeat 16 \7))
             (luid/internal-luid 0xFFFFFFFFFFF 0xFFFFFFFFF))))

    (testing "zero entropy"
      (is (= "777777776AAAAAAA"
             (luid/internal-luid 0xFFFFFFFFFFF 0)))))

  (testing "zero time"
    (testing "maximum entropy"
      (is (= "AAAAAAAAB7777777"
             (luid/internal-luid 0 0xFFFFFFFFF))))

    (testing "zero entropy"
      (is (= (apply str (repeat 16 \A))
             (luid/internal-luid 0 0)))))

  (testing "start of 2020"
    (testing "half entropy"
      (is (= "C326M3UAB3777777"
             (luid/internal-luid millis-2020 0xEFFFFFFFF))))))


(deftest luid-test
  (testing "length is 16 chars"
    (dotimes [_ 1000]
      (is (= 16 (count (luid/luid)))))))


(defn p [k bit]
  (/ (Math/pow k 2) (* 2 (Math/pow 2 bit))))


(defn n [a p]
  (/ (Math/log (- 1 a))
     (Math/log (- 1 p))))


(deftest collision-test
  (testing "when generating 1,000 LUID's per millisecond"
    (testing "it takes between 310,000 and 320,000 occasions to reach"
      (testing "a 90% probability of a collision"
        (is (< 310000 (n 0.9 (p 1000 36)) 320000)))))

  (testing "when generating 10,000 LUID's per millisecond"
    (testing "it takes between 3100 and 3200 occasions to reach"
      (testing "a 90% probability of a collision"
        (is (< 3100 (n 0.9 (p 10000 36)) 3200))))))
