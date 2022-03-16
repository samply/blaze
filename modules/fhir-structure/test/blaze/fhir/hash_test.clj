(ns blaze.fhir.hash-test
  (:require
    [blaze.byte-string :as bs]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.structure-definition-repo]
    [blaze.test-util :as tu]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]))


(st/instrument)
(tu/init-fhir-specs)


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


(deftest spec-generation-test
  (is (s/exercise :blaze.resource/hash)))
