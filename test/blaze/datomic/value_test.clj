(ns blaze.datomic.value-test
  (:require
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [blaze.datomic.quantity :refer [quantity]]
    [blaze.datomic.value :refer [read write]]
    [blaze.test-util :refer [satisfies-prop]])
  (:refer-clojure :exclude [read]))


(st/instrument)


(def decimal-gen
  (->> (gen/tuple gen/large-integer (gen/large-integer* {:min -100 :max 100}))
       (gen/fmap
         (fn [[unscaled scale]]
           (BigDecimal/valueOf ^long unscaled scale)))))


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


(deftest quantity-test
  (are [x] (= x (read (write x)))
    (quantity 1M "kg")
    (quantity 170M "cm")
    (quantity 1.1M "pl")
    (quantity 25M "kg/m2")))


(deftest bytes-test
  (are [x] (= (vec x) (vec (read (write x))))
    (byte-array [1 2 3])))
