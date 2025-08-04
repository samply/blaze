(ns blaze.coll.core-test
  (:require
   [blaze.coll.core :as coll :refer [with-open-coll]]
   [blaze.coll.core-spec]
   [blaze.test-util :as tu]
   [clojure.set :as set]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop])
  (:import
   [java.lang AutoCloseable]
   [java.util Iterator]))

(set! *warn-on-reflection* true)
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
    (is (= 2 (count (coll/eduction identity [1 2])))))

  (testing "eductions can be converted into a sequence"
    (is (= nil (seq (coll/eduction (map inc) []))))
    (is (= [2] (seq (coll/eduction (map inc) [1]))))
    (is (= [2 3] (seq (coll/eduction (map inc) [1 2])))))

  (testing "filter works with sequence"
    (is (= [1] (seq (coll/eduction (filter odd?) [1]))))
    (is (= nil (seq (coll/eduction (filter odd?) [2]))))
    (is (= [1 3] (seq (coll/eduction (filter odd?) [1 2 3 4]))))
    (is (= [2 4] (seq (coll/eduction (filter even?) [1 2 3 4])))))

  (testing "mapcat works with sequence"
    (is (= [0 0 1] (seq (coll/eduction (mapcat range) [1 2])))))

  (testing "halt-when works with sequence"
    (is (= nil (seq (coll/eduction (halt-when odd?) [1 2]))))
    (is (= [1] (seq (coll/eduction (halt-when even?) [1 2])))))

  (testing "iterators can be closed"
    (let [closed (volatile! false)]
      (->> (reify Iterable
             (iterator [_]
               (reify Iterator AutoCloseable
                 (close [_] (vreset! closed true)))))
           ^Iterable (coll/eduction identity)
           ^AutoCloseable (.iterator)
           (.close))
      (is @closed))))

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

(deftest with-open-coll-test
  (let [state (volatile! false)
        coll (with-open-coll [_ (reify AutoCloseable (close [_] (vreset! state true)))]
               (coll/eduction (map inc) (range 10)))]
    (is (= 10 (count coll)))
    (is (= (range 1 11) (vec coll)))
    (is (true? @state))))

(def ^:private merge-first
  (fn [x _] x))

(def ^:private merge-second
  (fn [_ x] x))

(def ^:private merge-generator
  (gen/one-of [(gen/return merge-first) (gen/return merge-second)]))

(deftest intersection-test
  (testing "two collections"
    (are [c1 c2 r] (= r (vec (coll/intersection compare merge-first (sort c1) (sort c2))))
      [] [] []
      [1] [] []
      [] [1] []
      [1] [1] [1]
      [1 2] [1 2] [1 2]
      [1] [1 2] [1]
      [1 2] [1] [1]
      [1 2 3] [2 4 5] [2])

    (testing "counting"
      (is (= 1 (count (coll/intersection compare merge-first [1 2] [2 3])))))

    (testing "mergeable items"
      (are [c1 c2 r] (= r (coll/intersection
                           (fn [[n1] [n2]] (compare n1 n2))
                           (fn [[n ks1] [_ ks2]] [n (into ks1 ks2)])
                           c1 c2))
        [[1 [:a]]] [[1 [:b]]] [[1 [:a :b]]]
        [[1 [:a]]] [[2 [:b]]] []
        [[1 [:a]] [2 [:b]]] [[1 [:c]] [2 [:d]]] [[1 [:a :c]] [2 [:b :d]]]
        [[1 [:a]] [2 [:b]]] [[1 [:c]]] [[1 [:a :c]]]))

    (tu/satisfies-prop 1000
      (prop/for-all [c1 (gen/vector gen/small-integer)
                     c2 (gen/vector gen/small-integer)
                     merge merge-generator]
        (let [res (vec (coll/intersection compare merge (sort c1) (sort c2)))]
          (and (= res (sort res))
               (every? (set c1) res)
               (every? (set c2) res))))))

  (testing "three collections"
    (tu/satisfies-prop 1000
      (prop/for-all [c1 (gen/vector gen/small-integer)
                     c2 (gen/vector gen/small-integer)
                     c3 (gen/vector gen/small-integer)
                     merge merge-generator]
        (let [res (vec (coll/intersection compare merge (sort c1) (sort c2) (sort c3)))]
          (and (= res (sort res))
               (every? (set c1) res)
               (every? (set c2) res)
               (every? (set c3) res))))))

  (testing "many collections"
    (tu/satisfies-prop 1000
      (prop/for-all [colls (gen/vector (gen/vector gen/small-integer) 2 100)
                     merge merge-generator]
        (let [res (vec (apply coll/intersection compare merge (map sort colls)))]
          (and (= res (sort res))
               (= (set res) (apply set/intersection (map set colls)))))))))

(deftest union-test
  (testing "two collections"
    (are [c1 c2 r] (= r (vec (coll/union compare merge-first (sort c1) (sort c2))))
      [] [] []
      [1] [] [1]
      [] [1] [1]
      [1] [1] [1]
      [1 2] [1 2] [1 2]
      [1] [1 2] [1 2]
      [1 2] [1] [1 2]
      [1 2 3] [2 4 5] [1 2 3 4 5])

    (testing "counting"
      (is (= 3 (count (coll/union compare merge-first [1 2] [2 3])))))

    (testing "mergeable items"
      (are [c1 c2 r] (= r (coll/union
                           (fn [[n1] [n2]] (compare n1 n2))
                           (fn [[n ks1] [_ ks2]] [n (into ks1 ks2)])
                           c1 c2))
        [[1 [:a]]] [[1 [:b]]] [[1 [:a :b]]]
        [[1 [:a]]] [[2 [:b]]] [[1 [:a]] [2 [:b]]]
        [[1 [:a]] [2 [:b]]] [[1 [:c]] [2 [:d]]] [[1 [:a :c]] [2 [:b :d]]]
        [[1 [:a]] [2 [:b]]] [[1 [:c]]] [[1 [:a :c]] [2 [:b]]]))

    (tu/satisfies-prop 1000
      (prop/for-all [c1 (gen/vector gen/small-integer)
                     c2 (gen/vector gen/small-integer)
                     merge merge-generator]
        (let [res (vec (coll/union compare merge (sort c1) (sort c2)))]
          (and (= res (sort res))
               (= (set res) (set/union (set c1) (set c2))))))))

  (testing "three collections"
    (tu/satisfies-prop 1000
      (prop/for-all [c1 (gen/vector gen/small-integer)
                     c2 (gen/vector gen/small-integer)
                     c3 (gen/vector gen/small-integer)
                     merge merge-generator]
        (let [res (vec (coll/union compare merge (sort c1) (sort c2) (sort c3)))]
          (and (= res (sort res))
               (= (set res) (set/union (set c1) (set c2) (set c3))))))))

  (testing "many collections"
    (tu/satisfies-prop 1000
      (prop/for-all [colls (gen/vector (gen/vector gen/small-integer) 2 100)
                     merge merge-generator]
        (let [res (vec (apply coll/union compare merge (map sort colls)))]
          (and (= res (sort res))
               (= (set res) (apply set/union (map set colls)))))))))
