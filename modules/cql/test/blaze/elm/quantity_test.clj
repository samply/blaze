(ns blaze.elm.quantity-test
  (:require
   [blaze.elm.compiler.test-util :refer [has-form]]
   [blaze.elm.protocols :as p]
   [blaze.elm.quantity :refer [quantity]]
   [blaze.test-util :as tu]
   [clojure.java.io :as io]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [are deftest is testing]]
   [cognitect.anomalies :as anom]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest quantity-test
  (testing "Commonly Used UCUM Codes for Healthcare Units"
    (testing "special units"
      (are [unit] (quantity 1 unit)
        "U/L"
        "10*3/uL"
        "mm[Hg]"))

    (testing "we can't parse 20 of this units"
      (->> (str/split (slurp (io/resource "blaze/elm/fhir-ucum-units.tsv")) #"\n")
           (drop 1)
           (map #(str/split % #"\t"))
           (map first)
           (map #(try (quantity 1 %) (catch Exception e (ex-data e))))
           (filter ::anom/category)
           (map :unit)
           (count)
           (= 20)
           (is))))

  (testing "form"
    (has-form (quantity 1M "m") '(quantity 1M "m"))))

;; 2.3. Property
(deftest property-test
  (testing "the value of a quantity is always a BigDecimal"
    (are [quantity] (= BigDecimal (class (p/get quantity :value)))
      (quantity 1M "m")
      (quantity 1 "m")
      (quantity (int 1) "m")
      (p/divide (quantity 1M "m") (quantity 1M "s"))
      (p/divide (quantity 1M "m") (quantity 2M "s"))))

  (testing "get on unknown key returns nil"
    (is (nil? (p/get (quantity 1M "m") ::unknown)))))
