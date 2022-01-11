(ns blaze.elm.quantity-test
  (:require
    [blaze.elm.protocols :as p]
    [blaze.elm.quantity :as q]
    [clojure.java.io :as io]
    [clojure.spec.test.alpha :as st]
    [clojure.string :as str]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest quantity-test
  (testing "Commonly Used UCUM Codes for Healthcare Units"
    (testing "special units"
      (are [unit] (q/quantity 1 unit)
        "U/L"
        "10*3/uL"
        "mm[Hg]"))

    (testing "we can't parse 20 of this units"
      (->> (str/split (slurp (io/resource "blaze/elm/fhir-ucum-units.tsv")) #"\n")
           (drop 1)
           (map #(str/split % #"\t"))
           (map first)
           (map #(try (q/quantity 1 %) (catch Exception e (ex-data e))))
           (filter ::anom/category)
           (map :unit)
           (count)
           (= 20)
           (is)))))


;; 2.3. Property
(deftest property-test
  (testing "the value of a quantity is always a BigDecimal"
    (are [quantity] (= BigDecimal (class (p/get quantity :value)))
      (q/quantity 1M "m")
      (q/quantity 1 "m")
      (q/quantity (int 1) "m")
      (p/divide (q/quantity 1M "m") (q/quantity 1M "s"))
      (p/divide (q/quantity 1M "m") (q/quantity 2M "s"))))

  (testing "get on unknown key returns nil"
    (is (nil? (p/get (q/quantity 1M "m") ::unknown)))))
