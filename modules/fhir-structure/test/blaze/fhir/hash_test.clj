(ns blaze.fhir.hash-test
  (:require
    [blaze.byte-string :as bs]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest generate-test
  (testing "a hash has a length of 32 bytes"
    (is (= 32 (bs/size (hash/generate {:fhir/type :fhir/Patient :id "0"})))))

  (testing "hashes are stable"
    (is (= (hash/generate {:fhir/type :fhir/Patient :id "0"})
           (hash/generate {:fhir/type :fhir/Patient :id "0"}))))

  (testing "hashes from different resource types are different"
    (is (not= (hash/generate {:fhir/type :fhir/Patient :id "0"})
              (hash/generate {:fhir/type :fhir/Observation :id "0"})))))
