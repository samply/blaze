(ns blaze.elm.list-test
  (:require
    [blaze.coll.core :as coll]
    [blaze.elm.compiler]
    [blaze.elm.list]
    [blaze.elm.protocols :as p]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; 12.1. Equal
(deftest equal-test
  (testing "works on eductions"
    (is (true? (p/equal (coll/eduction (map identity) [1 2]) [1 2])))))


;; 12.2. Equivalent
(deftest equivalent-test
  (testing "works on eductions"
    (is (true? (p/equivalent (coll/eduction (map identity) [1 2]) [1 2])))))


;; 17.6. Indexer
(deftest indexer-test
  (testing "works on eductions"
    (is (= 1 (p/indexer (coll/eduction (map identity) [1 2]) 0)))))


;; 19.5. Contains
(deftest contains-test
  (testing "works on eductions"
    (is (true? (p/contains (coll/eduction (map identity) [1 2]) 1 nil)))))


;; 19.10. Except
(deftest except-test
  (testing "works on eductions"
    (is (= [2] (p/except (coll/eduction (map identity) [1 2]) [1])))))


;; 19.13. Includes
(deftest includes-test
  (testing "works on eductions"
    (is (true? (p/includes (coll/eduction (map identity) [1 2]) [1] nil)))))


;; 19.15. Intersect
(deftest intersect-test
  (testing "works on eductions"
    (is (= [2] (p/intersect (coll/eduction (map identity) [1 2]) [2 3])))))


;; 19.24. ProperContains
(deftest proper-contains-test
  (testing "works on eductions"
    (is (true? (p/proper-contains (coll/eduction (map identity) [1 2]) 1 nil)))))


;; 19.26. ProperIncludes
(deftest proper-includes-test
  (testing "works on eductions"
    (is (true? (p/proper-includes (coll/eduction (map identity) [1 2]) [1] nil)))))


;; 19.31. Union
(deftest union-test
  (testing "works on eductions"
    (is (= [1 2 3] (p/union (coll/eduction (map identity) [1 2]) [3])))))


;; 20.25. SingletonFrom
(deftest singleton-from-test
  (testing "works on eductions"
    (is (= 1 (p/singleton-from (coll/eduction (map identity) [1]))))))
