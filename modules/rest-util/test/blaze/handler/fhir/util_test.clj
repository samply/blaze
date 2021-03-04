(ns blaze.handler.fhir.util-test
  (:require
    [blaze.handler.fhir.util :as fhir-util]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [reitit.core :as reitit]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest to-seq
  (testing "nil"
    (is (nil? (fhir-util/to-seq nil))))

  (testing "non-sequential value"
    (is (= [1] (fhir-util/to-seq 1))))

  (testing "sequential value"
    (is (= [1] (fhir-util/to-seq [1])))))


(deftest t
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


(deftest page-size
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
      ["<invalid>" "2"] 2
      ["3" "4"] 3))

  (testing "500 is the maximum"
    (is (= 500 (fhir-util/page-size {"_count" "501"})))))


(deftest page-offset
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
      ["3" "4"] 3)))


(deftest page-type
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


(deftest page-id
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


(def ^:private router
  (reitit/router
    [[""
      {:blaze/base-url "base-url"}
      ["/Patient" {:name :Patient/type}]
      ["/Patient/{id}" {:name :Patient/instance}]
      ["/Patient/{id}/_history/{vid}" {:name :Patient/versioned-instance}]]]
    {:syntax :bracket}))


(deftest type-url
  (is (= "base-url/Patient" (fhir-util/type-url router "Patient"))))


(deftest instance-url
  (is (= "base-url/Patient/0" (fhir-util/instance-url router "Patient" "0"))))


(deftest versioned-instance-url
  (is (= "base-url/Patient/0/_history/1"
         (fhir-util/versioned-instance-url router "Patient" "0" "1"))))


(deftest etag->t
  (testing "accepts nil"
    (is (nil? (fhir-util/etag->t nil))))

  (testing "valid ETag"
    (is (= 1 (fhir-util/etag->t "W/\"1\""))))

  (testing "invalid ETag"
    (are [s] (nil? (fhir-util/etag->t s))
      "foo"
      "W/1"
      "W/\"a\"")))
