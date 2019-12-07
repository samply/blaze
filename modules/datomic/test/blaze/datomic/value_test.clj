(ns blaze.datomic.value-test
  (:require
    [blaze.datomic.quantity :as quantity]
    [blaze.datomic.value :refer [read write]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer [are deftest is testing]]
    [clojure.test.check :as tc]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop])
  (:refer-clojure :exclude [read]))


(st/instrument)


(def decimal-gen
  (->> (gen/tuple gen/large-integer (gen/large-integer* {:min -100 :max 100}))
       (gen/fmap
         (fn [[unscaled scale]]
           (BigDecimal/valueOf ^long unscaled scale)))))


(defmacro satisfies-prop [num-tests prop]
  `(let [result# (tc/quick-check ~num-tests ~prop)]
     (if (instance? Throwable (:result result#))
       (throw (:result result#))
       (if (true? (:result result#))
         (is :success)
         (is (clojure.pprint/pprint result#))))))


(deftest decimal-test
  (testing "Read/Write"
    (satisfies-prop 10000
      (prop/for-all [x decimal-gen]
        (= x (read (write x))))))

  (testing "Byte length"
    (are [x len] (= len (count (write x)))
      0M 3
      1M 3
      1.1M 3
      (BigDecimal/valueOf Byte/MAX_VALUE) 3
      (BigDecimal/valueOf Short/MAX_VALUE) 4
      (BigDecimal/valueOf Integer/MAX_VALUE) 6
      (BigDecimal/valueOf Long/MAX_VALUE) 10
      (inc (BigDecimal/valueOf Long/MAX_VALUE)) 11)
    (are [x len] (= len (count (write x)))
      (BigDecimal/valueOf Byte/MIN_VALUE) 3
      (BigDecimal/valueOf Short/MIN_VALUE) 4
      (BigDecimal/valueOf Integer/MIN_VALUE) 6
      (BigDecimal/valueOf Long/MIN_VALUE) 10
      (dec (BigDecimal/valueOf Long/MIN_VALUE)) 11)))


(deftest ucum-quantity-without-unit-test
  (are [x] (= x (read (write x)))
    (quantity/ucum-quantity-without-unit 1M "kg")
    (quantity/ucum-quantity-without-unit 170 "cm")
    (quantity/ucum-quantity-without-unit 1.1M "pl")
    (quantity/ucum-quantity-without-unit 25M "kg/m2")))


(deftest ucum-quantity-with-same-unit-test
  (are [x] (= x (read (write x)))
    (quantity/ucum-quantity-with-same-unit 1M "kg")))


(deftest ucum-quantity-with-different-unit-test
  (are [x] (= x (read (write x)))
    (quantity/ucum-quantity-with-different-unit 1M "kilogram" "kg")
    (quantity/ucum-quantity-with-different-unit 170M "centimeter" "cm")))


(deftest custom-quantity-test
  (are [x] (= x (read (write x)))
    (quantity/custom-quantity 1M "Foo" "system" "foo")))


(deftest bytes-test
  (are [x] (= (vec x) (vec (read (write x))))
    (byte-array [1 2 3])))
