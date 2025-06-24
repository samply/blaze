(ns blaze.elm.compiler.core-test
  (:require
   [blaze.coll.core :as coll]
   [blaze.elm.compiler-spec]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.core-spec]
   [clojure.spec.test.alpha :as st]
   [clojure.test :refer [deftest is testing]]))

(set! *warn-on-reflection* true)
(st/instrument)

(deftest attach-cache-test
  (testing "nil expressions will be returned unchanged"
    (is (nil? (first ((first (core/-attach-cache nil nil)))))))

  (testing "expressions of type Object will be returned unchanged"
    (let [expression (Object.)]
      (is (identical? expression (first ((first (core/-attach-cache expression nil))))))))

  (testing "expressions of type IReduceInit will be returned unchanged"
    (let [expression (coll/eduction identity [])]
      (is (identical? expression (first ((first (core/-attach-cache expression nil)))))))))

(deftest resolve-refs-test
  (testing "nil expressions will be returned unchanged"
    (is (nil? (core/-resolve-refs nil {}))))

  (testing "expressions of type Object will be returned unchanged"
    (let [expression (Object.)]
      (is (identical? expression (core/-resolve-refs expression {})))))

  (testing "expressions of type IReduceInit will be returned unchanged"
    (let [expression (coll/eduction identity [])]
      (is (identical? expression (core/-resolve-refs expression {}))))))

(deftest resolve-params-test
  (testing "nil expressions will be returned unchanged"
    (is (nil? (core/-resolve-params nil {}))))

  (testing "expressions of type Object will be returned unchanged"
    (let [expression (Object.)]
      (is (identical? expression (core/-resolve-params expression {})))))

  (testing "expressions of type IReduceInit will be returned unchanged"
    (let [expression (coll/eduction identity [])]
      (is (identical? expression (core/-resolve-params expression {}))))))

(deftest optimize-test
  (testing "nil expressions will be returned unchanged"
    (is (nil? (core/-optimize nil nil))))

  (testing "expressions of type Object will be returned unchanged"
    (let [expression (Object.)]
      (is (identical? expression (core/-optimize expression nil)))))

  (testing "expressions of type IReduceInit will be returned unchanged"
    (let [expression (coll/eduction identity [])]
      (is (identical? expression (core/-optimize expression nil))))))
