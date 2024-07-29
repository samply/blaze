(ns blaze.coll.core-test
  (:require
   [blaze.coll.core :as coll]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest first-test
  (testing "nil"
    (is (nil? (coll/first nil))))

  (testing "empty vector"
    (is (nil? (coll/first []))))

  (testing "vector with one element"
    (is (= 1 (coll/first [1]))))

  (testing "vector with two elements"
    (is (= 1 (coll/first [1 2])))))

(deftest some-test
  (testing "nil"
    (is (nil? (coll/some any? nil))))

  (testing "empty vector"
    (is (nil? (coll/some any? []))))

  (testing "vector with one element"
    (testing "matching pred"
      (is (true? (coll/some int? [1]))))

    (testing "none matching pred"
      (is (nil? (coll/some string? [1])))))

  (testing "vector with two elements"
    (testing "matching pred on first"
      (is (true? (coll/some int? [1 "1"]))))

    (testing "matching pred on second"
      (is (true? (coll/some string? [1 "1"]))))

    (testing "none matching pred"
      (is (nil? (coll/some string? [1 2]))))))

(deftest empty-test
  (testing "nil"
    (is (true? (coll/empty? nil))))

  (testing "empty vector"
    (is (true? (coll/empty? []))))

  (testing "vector with one element"
    (is (false? (coll/empty? [1])))
    (is (false? (coll/empty? [nil])))))

(deftest eduction-test
  (testing "eductions are sequential"
    (is (sequential? (coll/eduction identity [1]))))

  (testing "eductions can be reduced"
    (is (= [2 3] (reduce conj [] (coll/eduction (map inc) [1 2])))))

  (testing "eductions are counted"
    (is (= 2 (count (coll/eduction identity [1 2]))))))

(deftest count-test
  (are [coll n] (and (= n (coll/count coll))
                     (= n (apply coll/count [coll])))
    [] 0
    [::x] 1
    [::x ::y] 2))

(deftest nth-test
  (are [coll n x] (and (= x (coll/nth coll n))
                       (= x (apply coll/nth coll [n])))
    [::x] 0 ::x
    [::x ::y] 0 ::x
    [::x ::y] 1 ::y)

  (testing "throws IndexOutOfBoundsException"
    (is (thrown? IndexOutOfBoundsException (coll/nth [] 0))))

  (testing "not-found"
    (are [coll n x] (and (= x (coll/nth coll n ::not-found))
                         (= x (apply coll/nth coll n [::not-found])))
      [] 0 ::not-found
      [::x] 0 ::x
      [::x] 1 ::not-found
      [::x ::y] 0 ::x
      [::x ::y] 1 ::y
      [::x ::y] 2 ::not-found)))
