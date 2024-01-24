(ns blaze.fhir.spec.references-test
  (:require
   [blaze.fhir.spec.references :as fsr]
   [blaze.fhir.spec.references-spec]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest split-literal-ref-test
  (testing "valid refs"
    (are [ref type id] (= [type id] (fsr/split-literal-ref ref))
      "Patient/0" "Patient" "0"
      "/Library/0" "Library" "0"
      "//Measure/1" "Measure" "1"))

  (testing "invalid refs"
    (are [ref] (nil? (fsr/split-literal-ref ref))
      "http://localhost:8080/fhir/Patient/0"))

  (testing "works on arbitrary strings"
    (satisfies-prop 10000
      (prop/for-all [s gen/string]
        (any? (fsr/split-literal-ref s))))))
