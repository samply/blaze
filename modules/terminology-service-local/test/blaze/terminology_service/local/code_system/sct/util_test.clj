(ns blaze.terminology-service.local.code-system.sct.util-test
  (:require
   [blaze.terminology-service.local.code-system.sct.util :as sct-u]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest module-version-test
  (testing "invalid"
    (given (sct-u/module-version "http://snomed.info/sct/900000000000207008/version")
      ::anom/category := ::anom/incorrect
      ::anom/message := "Incorrectly formatted SNOMED CT version `http://snomed.info/sct/900000000000207008/version`."))

  (testing "valid"
    (is (= (sct-u/module-version "http://snomed.info/sct/900000000000207008/version/20220228")
           [900000000000207008 20220228]))))

(deftest create-code-system-test
  (given (sct-u/create-code-system "900000000000207008" 20220228 nil)
    :fhir/type := :fhir/CodeSystem
    :url := #fhir/uri "http://snomed.info/sct"
    :version := #fhir/string "http://snomed.info/sct/900000000000207008/version/20220228"
    :title := nil))
