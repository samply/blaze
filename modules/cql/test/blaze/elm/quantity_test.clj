(ns blaze.elm.quantity-test
  (:require
    [blaze.elm.protocols :as p]
    [blaze.elm.quantity :as quantity]
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
      (are [unit] (quantity/quantity 1 unit)
        "U/L"
        "10*3/uL"
        "mm[Hg]"
        "[arb'U]/mL"))

    (testing "we can't parse 20 of this units"
      (->> (str/split (slurp (io/resource "blaze/elm/fhir-ucum-units.tsv")) #"\n")
           (drop 1)
           (map #(str/split % #"\t"))
           (map first)
           (map #(try (quantity/quantity 1 %) (catch Exception e (ex-data e))))
           (filter ::anom/category)
           (map :unit)
           (count)
           (= 20)
           (is)))))


;; 2.3. Property
(deftest property-test
  (testing "the value of a quantity is always a BigDecimal"
    (are [quantity] (= BigDecimal (class (p/get quantity :value)))
      (quantity/quantity 1M "m")
      (quantity/quantity 1 "m")
      (quantity/quantity (int 1) "m")
      (p/divide (quantity/quantity 1M "m") (quantity/quantity 1M "s"))
      (p/divide (quantity/quantity 1M "m") (quantity/quantity 2M "s"))))

  (testing "get on unknown key returns nil"
    (is (nil? (p/get (quantity/quantity 1M "m") ::unknown)))))
