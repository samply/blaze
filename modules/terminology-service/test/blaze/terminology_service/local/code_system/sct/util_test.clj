(ns blaze.terminology-service.local.code-system.sct.util-test
  (:require
   [blaze.terminology-service.local.code-system.sct.util :as u]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest module-version-test
  (testing "invalid"
    (given (u/module-version "http://snomed.info/sct/900000000000207008/version")
      ::anom/category := ::anom/incorrect
      ::anom/message := "Incorrectly formatted Snomed CT version `http://snomed.info/sct/900000000000207008/version`."))

  (testing "valid"
    (is (= (u/module-version "http://snomed.info/sct/900000000000207008/version/20220228")
           [900000000000207008 20220228]))))
