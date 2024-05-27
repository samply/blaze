(ns blaze.util-test
  (:require
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [blaze.util :as u]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]))

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

(deftest strip-leading-slash-test
  (satisfies-prop 1000
    (prop/for-all [s gen/string]
      (not (str/starts-with? (u/strip-leading-slash s) "/")))))
