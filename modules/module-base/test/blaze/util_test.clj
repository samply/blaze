(ns blaze.util-test
  (:require
   [blaze.test-util :as tu]
   [blaze.util :as u]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest duration-s-test
  (is (pos? (u/duration-s (System/nanoTime)))))

(deftest to-seq-test
  (testing "nil"
    (is (nil? (u/to-seq nil))))

  (testing "non-sequential value"
    (is (= [1] (u/to-seq 1))))

  (testing "sequential value"
    (is (= [1] (u/to-seq [1])))))
