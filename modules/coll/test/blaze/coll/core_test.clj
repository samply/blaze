(ns blaze.coll.core-test
  (:require
    [blaze.coll.core :as coll]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]])
  (:refer-clojure :exclude [eduction empty? first]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest first
  (testing "nil"
    (is (nil? (coll/first nil))))

  (testing "empty vector"
    (is (nil? (coll/first []))))

  (testing "vector with one element"
    (is (= 1 (coll/first [1]))))

  (testing "vector with two elements"
    (is (= 1 (coll/first [1 2])))))


(deftest empty?
  (testing "nil"
    (is (true? (coll/empty? nil))))

  (testing "empty vector"
    (is (true? (coll/empty? []))))

  (testing "vector with one element"
    (is (false? (coll/empty? [1])))))


(deftest eduction
  (testing "eductions are sequential"
    (is (sequential? (coll/eduction (map identity) [1])))))
