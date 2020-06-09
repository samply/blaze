(ns blaze.handler.fhir.util-test
  (:require
    [blaze.handler.fhir.util :as fhir-util]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [reitit.core :as reitit]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest t
  (testing "no query param"
    (is (nil? (fhir-util/t {}))))

  (testing "invalid query param"
    (are [t] (nil? (fhir-util/t {"__t" t}))
      "<invalid>"
      "-1"
      ""))

  (testing "valid query param"
    (is (= 1 (fhir-util/t {"__t" "1"})))))


(deftest page-size
  (testing "no query param"
    (is (= 50 (fhir-util/page-size {}))))

  (testing "invalid query param"
    (are [size] (= 50 (fhir-util/page-size {"_count" size}))
      "<invalid>"
      "-1"
      ""))

  (testing "valid query param"
    (are [size] (= size (fhir-util/page-size {"_count" (str size)}))
      0
      1
      50
      500))

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
    (are [offset] (= offset (fhir-util/page-offset {"__page-offset" (str offset)}))
      0
      1
      50
      500)))


(deftest page-id
  (testing "no query param"
    (is (nil? (fhir-util/page-id {}))))

  (testing "invalid query param"
    (are [id] (nil? (fhir-util/page-id {"__page-id" id}))
      "<invalid>"
      ""))

  (testing "valid query param"
    (is (= "0" (fhir-util/page-id {"__page-id" "0"})))))


(def ^:private router
  (reitit/router
    [[""
      {:blaze/base-url "base-url"}
      ["/Patient" {:name :Patient/type}]
      ["/Patient/{id}" {:name :Patient/instance}]
      ["/Patient/{id}/{vid}" {:name :Patient/versioned-instance}]]]
    {:syntax :bracket}))


(deftest type-url-test
  (is (= "base-url/Patient" (fhir-util/type-url router "Patient"))))


(deftest instance-url-test
  (is (= "base-url/Patient/0" (fhir-util/instance-url router "Patient" "0"))))


(deftest versioned-instance-url-test
  (is (= "base-url/Patient/0/1"
         (fhir-util/versioned-instance-url router "Patient" "0" "1"))))
