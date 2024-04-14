(ns blaze.handler.fhir.util-test
  (:require
   [blaze.fhir.spec.generators :as fg]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.handler.fhir.util-spec]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit])
  (:import [java.time Instant]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

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

(def comma-with-spaces-gen
  (let [spaces #(apply str (repeat % " "))]
    (gen/let [pre (gen/choose 0 5)
              post (gen/choose 0 5)]
      (str (spaces pre) "," (spaces post)))))

(def fields-gen
  (gen/let [fields (gen/vector (gen/such-that (comp not empty?) gen/string-alphanumeric) 1 10)
            separators (gen/vector comma-with-spaces-gen (dec (count fields)))]
    {:vector (mapv keyword fields)
     :string (str/join (cons (first fields) (interleave separators (rest fields))))}))

(deftest elements-test
  (testing "_elements is not present"
    (are [x] (empty? (fhir-util/elements x))
      nil
      {}))

  (testing "_elements is present"
    (tu/satisfies-prop 1000
      (prop/for-all [fields fields-gen]
        (let [query-params {"_elements" (fields :string)}]
          (= (set (fhir-util/elements query-params))
             (set (fields :vector))))))))

(deftest date-test
  (testing "missing"
    (are [query-params] (nil? (fhir-util/date query-params "start"))
      nil
      {}
      {"end" "2024"}))

  (testing "invalid"
    (given (fhir-util/date {"start" "invalid"} "start")
      ::anom/category := ::anom/incorrect
      ::anom/message := "The value `invalid` of the query param `start` is no valid date."))

  (testing "valid"
    (tu/satisfies-prop 1000
      (prop/for-all [name gen/string-alphanumeric
                     value fg/date-value]
        (let [query-params {name value}]
          (= (type/date value) (fhir-util/date query-params name)))))))

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

(deftest etag-test
  (tu/satisfies-prop 1000
    (prop/for-all [t (s/gen :blaze.db/t)]
      (= (format "W/\"%d\"" t) (fhir-util/etag {:blaze.db/t t :blaze.db.tx/instant Instant/EPOCH})))))
