(ns blaze.elm.list-test
  (:require
   [blaze.coll.core :as coll]
   [blaze.elm.compiler]
   [blaze.elm.list]
   [blaze.elm.protocols :as p]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]])
  (:import
   [clojure.lang IReduceInit]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- reducible
  "Returns a collection of `items` that is only reducible, so not seqable, and
  counts how often it's reduced in the volatile `reductions`."
  [reductions items]
  (reify IReduceInit
    (reduce [_ rf init]
      (vswap! reductions inc)
      (reduce rf init items))))

;; 12.1. Equal
(deftest equal-test
  (testing "works on eductions"
    (is (true? (p/equal (coll/eduction (map identity) [1 2]) [1 2]))))

  (testing "works on eductions as second operand"
    (is (true? (p/equal [1 2] (coll/eduction (map identity) [1 2])))))

  (testing "reduces a reducible second operand only once"
    (let [reductions (volatile! 0)]
      (is (true? (p/equal [1 2] (reducible reductions [1 2]))))
      (is (= 1 @reductions)))))

;; 12.2. Equivalent
(deftest equivalent-test
  (testing "works on eductions"
    (is (true? (p/equivalent (coll/eduction (map identity) [1 2]) [1 2]))))

  (testing "works on eductions as second operand"
    (is (true? (p/equivalent [1 2] (coll/eduction (map identity) [1 2])))))

  (testing "reduces a reducible second operand only once"
    (let [reductions (volatile! 0)]
      (is (true? (p/equivalent [1 2] (reducible reductions [1 2]))))
      (is (= 1 @reductions)))))

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
    (is (true? (p/includes (coll/eduction (map identity) [1 2]) [1] nil))))

  (testing "reduces a reducible second operand only once"
    (let [reductions (volatile! 0)]
      (is (true? (p/includes [1 2] (reducible reductions [1]) nil)))
      (is (= 1 @reductions)))))

;; 19.15. Intersect
(deftest intersect-test
  (testing "works on eductions"
    (is (= [2] (p/intersect (coll/eduction (map identity) [1 2]) [2 3]))))

  (testing "works on eductions as second operand"
    (is (= [2] (p/intersect [1 2] (coll/eduction (map identity) [2 3])))))

  (testing "reduces a reducible second operand only once"
    (let [reductions (volatile! 0)]
      (is (= [2] (p/intersect [1 2] (reducible reductions [2 3]))))
      (is (= 1 @reductions)))))

;; 19.24. ProperContains
(deftest proper-contains-test
  (testing "works on eductions"
    (is (true? (p/proper-contains (coll/eduction (map identity) [1 2]) 1 nil)))))

;; 19.26. ProperIncludes
(deftest proper-includes-test
  (testing "returns nil on nil second operand"
    (is (nil? (p/proper-includes [1 2] nil nil))))

  (testing "works on eductions"
    (is (true? (p/proper-includes (coll/eduction (map identity) [1 2]) [1] nil))))

  (testing "works on eductions as second operand"
    (is (true? (p/proper-includes [1 2] (coll/eduction (map identity) [1]) nil))))

  (testing "reduces a reducible second operand only once"
    (let [reductions (volatile! 0)]
      (is (true? (p/proper-includes [1 2] (reducible reductions [1]) nil)))
      (is (= 1 @reductions)))))

;; 19.31. Union
(deftest union-test
  (testing "works on eductions"
    (is (= [1 2 3] (p/union (coll/eduction (map identity) [1 2]) [3])))))

;; 20.25. SingletonFrom
(deftest singleton-from-test
  (testing "works on eductions"
    (is (= 1 (p/singleton-from (coll/eduction (map identity) [1]))))))
