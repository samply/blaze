(ns blaze.luid.impl-test
  (:require
    [blaze.luid.impl :as impl]
    [clojure.test :refer [deftest is testing]])
  (:import 
    [java.time Instant]))


(def millis-2020
  (.toEpochMilli (Instant/parse "2020-01-01T00:00:00Z")))


(deftest internal-luid-test
  (testing "maximum time"
    (testing "maximum entropy"
      (is (= (apply str (repeat 16 \7))
             (impl/luid 0xFFFFFFFFFFF 0xFFFFFFFFF))))

    (testing "zero entropy"
      (is (= "777777776AAAAAAA"
             (impl/luid 0xFFFFFFFFFFF 0)))))

  (testing "zero time"
    (testing "maximum entropy"
      (is (= "AAAAAAAAB7777777"
             (impl/luid 0 0xFFFFFFFFF))))

    (testing "zero entropy"
      (is (= (apply str (repeat 16 \A))
             (impl/luid 0 0)))))

  (testing "start of 2020"
    (testing "half entropy"
      (is (= "C326M3UAB3777777"
             (impl/luid millis-2020 0xEFFFFFFFF))))))
