(ns blaze.coll.core-test
  (:require
    [blaze.coll.core :as coll]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest first-test
  (testing "nil"
    (is (nil? (coll/first nil))))

  (testing "empty vector"
    (is (nil? (coll/first []))))

  (testing "vector with one element"
    (is (= 1 (coll/first [1]))))

  (testing "vector with two elements"
    (is (= 1 (coll/first [1 2])))))


(deftest empty-test
  (testing "nil"
    (is (true? (coll/empty? nil))))

  (testing "empty vector"
    (is (true? (coll/empty? []))))

  (testing "vector with one element"
    (is (false? (coll/empty? [1])))))


(deftest eduction-test
  (testing "eductions are sequential"
    (is (sequential? (coll/eduction (map identity) [1]))))

  (testing "eductions can be reduced"
    (is (= [2 3] (reduce conj [] (coll/eduction (map inc) [1 2])))))

  (testing "eductions are seqable"
    (is (= (list 2 3) (seq (coll/eduction (map inc) [1 2])))))

  (testing "eductions are counted"
    (is (= 2 (count (coll/eduction (map inc) [1 2]))))))
