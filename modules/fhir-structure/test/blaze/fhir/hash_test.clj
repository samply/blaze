(ns blaze.fhir.hash-test
  (:require
    [blaze.fhir.hash :as hash]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]))


(set! *warn-on-reflection* true)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest generate-test
  (testing "bit length is 256"
    (is (= 256 (.bits (hash/generate {:fhir/type :fhir/Patient :id "0"})))))

  (testing "hashes are stable"
    (is (= (hash/generate {:fhir/type :fhir/Patient :id "0"})
           (hash/generate {:fhir/type :fhir/Patient :id "0"}))))

  (testing "hashes from different resource types are different"
    (is (not= (hash/generate {:fhir/type :fhir/Patient :id "0"})
              (hash/generate {:fhir/type :fhir/Observation :id "0"})))))

