(ns blaze.util-test
  (:refer-clojure :exclude [str])
  (:require
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [blaze.util :as u :refer [str]]
   [blaze.util-spec]
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
  (satisfies-prop 10000
    (prop/for-all [s gen/string]
      (not (str/starts-with? (u/strip-leading-slashes s) "/")))))

(deftest available-processors-test
  (is (pos-int? (u/available-processors))))

(deftest str-test
  (is (= "" (str) (str nil) (apply str [nil])))
  (is (= "a" (str "a") (apply str ["a"])))
  (is (= "1" (str 1) (apply str [1])))
  (is (= "ab" (str "a" "b") (apply str "a" ["b"])))
  (is (= "12" (str 1 2) (apply str 1 [2])))
  (is (= "abc" (str "a" "b" "c") (apply str "a" "b" ["c"])))
  (is (= "123" (str 1 2 3) (apply str 1 2 [3])))
  (is (= "abcd" (str "a" "b" "c" "d") (apply str "a" "b" "c" ["d"])))
  (is (= "1234" (str 1 2 3 4) (apply str 1 2 3 [4])))
  (is (= "abcde" (str "a" "b" "c" "d" "e") (apply str "a" "b" "c" "d" ["e"])))
  (is (= "12345" (str 1 2 3 4 5) (apply str 1 2 3 4 [5])))
  (is (= "abcdef" (str "a" "b" "c" "d" "e" "f") (apply str "a" "b" "c" "d" "e" ["f"])))
  (is (= "123456" (str 1 2 3 4 5 6) (apply str 1 2 3 4 5 [6])))
  (is (= "abcdefg" (str "a" "b" "c" "d" "e" "f" "g") (apply str "a" "b" "c" "d" "e" "f" ["g"])))
  (is (= "1234567" (str 1 2 3 4 5 6 7) (apply str 1 2 3 4 5 6 [7])))

  (testing "str vs. clojure.core/str"
    (satisfies-prop 1000
      (prop/for-all [x1 gen/any]
        (= (str x1) (clojure.core/str x1))))

    (satisfies-prop 1000
      (prop/for-all [x1 gen/any x2 gen/any]
        (= (str x1 x2) (clojure.core/str x1 x2))))

    (satisfies-prop 1000
      (prop/for-all [x1 gen/any x2 gen/any x3 gen/any]
        (= (str x1 x2 x3) (clojure.core/str x1 x2 x3))))

    (satisfies-prop 1000
      (prop/for-all [x1 gen/any x2 gen/any x3 gen/any x4 gen/any]
        (= (str x1 x2 x3 x4) (clojure.core/str x1 x2 x3 x4))))

    (satisfies-prop 1000
      (prop/for-all [x1 gen/any x2 gen/any x3 gen/any x4 gen/any x5 gen/any]
        (= (str x1 x2 x3 x4 x5) (clojure.core/str x1 x2 x3 x4 x5))))

    (satisfies-prop 1000
      (prop/for-all [x1 gen/any x2 gen/any x3 gen/any x4 gen/any x5 gen/any
                     x6 gen/any]
        (= (str x1 x2 x3 x4 x5 x6) (clojure.core/str x1 x2 x3 x4 x5 x6))))

    (satisfies-prop 1000
      (prop/for-all [x1 gen/any x2 gen/any x3 gen/any x4 gen/any x5 gen/any
                     x6 gen/any x7 gen/any]
        (= (str x1 x2 x3 x4 x5 x6 x7) (clojure.core/str x1 x2 x3 x4 x5 x6 x7))))

    (satisfies-prop 100
      (prop/for-all [xs (gen/vector gen/any)]
        (= (apply str xs) (apply clojure.core/str xs))))))
