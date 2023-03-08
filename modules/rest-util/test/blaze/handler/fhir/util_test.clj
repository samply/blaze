(ns blaze.handler.fhir.util-test
  (:require
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [reitit.core :as reitit]))


(st/instrument)


(test/use-fixtures :each tu/fixture)


(deftest to-seq-test
  (testing "nil"
    (is (nil? (fhir-util/to-seq nil))))

  (testing "non-sequential value"
    (is (= [1] (fhir-util/to-seq 1))))

  (testing "sequential value"
    (is (= [1] (fhir-util/to-seq [1])))))


(deftest t-test
  (testing "no query param"
    (is (nil? (fhir-util/t {}))))

  (testing "invalid query param"
    (are [t] (nil? (fhir-util/t {"__t" t}))
      "<invalid>"
      "-1"
      ""))

  (testing "valid query param"
    (are [v t] (= t (fhir-util/t {"__t" v}))
      "1" 1
      ["<invalid>" "2"] 2
      ["3" "4"] 3)))


(deftest page-size-test
  (testing "no query param"
    (is (= 50 (fhir-util/page-size {}))))

  (testing "invalid query param"
    (are [size] (= 50 (fhir-util/page-size {"_count" size}))
      "<invalid>"
      "-1"
      ""))

  (testing "valid query param"
    (are [v size] (= size (fhir-util/page-size {"_count" v}))
      "0" 0
      "1" 1
      "50" 50
      "500" 500
      "1000" 1000
      "10000" 10000
      ["<invalid>" "2"] 2
      ["0" "1"] 0
      ["3" "4"] 3))

  (testing "10000 is the maximum"
    (is (= 10000 (fhir-util/page-size {"_count" "10001"})))))


(deftest page-offset-test
  (testing "no query param"
    (is (zero? (fhir-util/page-offset {}))))

  (testing "invalid query param"
    (are [offset] (zero? (fhir-util/page-offset {"__page-offset" offset}))
      "<invalid>"
      "-1"
      ""))

  (testing "valid query param"
    (are [v offset] (= offset (fhir-util/page-offset {"__page-offset" v}))
      "0" 0
      "1" 1
      "10" 10
      "100" 100
      "1000" 1000
      ["<invalid>" "2"] 2
      ["0" "1"] 0
      ["3" "4"] 3)))


(deftest page-type-test
  (testing "no query param"
    (is (nil? (fhir-util/page-type {}))))

  (testing "invalid query param"
    (are [type] (nil? (fhir-util/page-type {"__page-type" type}))
      "<invalid>"
      ""))

  (testing "valid query param"
    (are [v type] (= type (fhir-util/page-type {"__page-type" v}))
      "A" "A"
      ["<invalid>" "A"] "A"
      ["A" "B"] "A")))


(deftest page-id-test
  (testing "no query param"
    (is (nil? (fhir-util/page-id {}))))

  (testing "invalid query param"
    (are [id] (nil? (fhir-util/page-id {"__page-id" id}))
      "<invalid>"
      ""))

  (testing "valid query param"
    (are [v id] (= id (fhir-util/page-id {"__page-id" v}))
      "0" "0"
      ["<invalid>" "a"] "a"
      ["A" "b"] "A")))


(def router
  (reitit/router
    [[""
      {}
      ["/Patient" {:name :Patient/type}]
      ["/Patient/{id}" {:name :Patient/instance}]
      ["/Patient/{id}/_history/{vid}" {:name :Patient/versioned-instance}]]]
    {:syntax :bracket
     :path "/fhir"}))


(def context
  {:blaze/base-url "http://localhost:8080"
   ::reitit/router router})


(deftest type-url-test
  (is (= "http://localhost:8080/fhir/Patient"
         (fhir-util/type-url context "Patient"))))


(deftest instance-url-test
  (is (= "http://localhost:8080/fhir/Patient/0"
         (fhir-util/instance-url context "Patient" "0"))))


(deftest versioned-instance-url-test
  (is (= "http://localhost:8080/fhir/Patient/0/_history/1"
         (fhir-util/versioned-instance-url context "Patient" "0" "1"))))
