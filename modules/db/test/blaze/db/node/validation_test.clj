(ns blaze.db.node.validation-test
  (:require
    [blaze.db.node.validation :as validation]
    [blaze.db.node.validation-spec]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


(st/instrument)


(test/use-fixtures :each tu/fixture)


(deftest validate-ops-test
  (testing "single delete"
    (is (nil? (validation/validate-ops [[:delete "Patient" "0"]]))))

  (testing "duplicate delete"
    (given (validation/validate-ops [[:delete "Patient" "0"]
                                     [:delete "Patient" "0"]])
      ::anom/category := ::anom/incorrect
      :cognitect.anomalies/message := "Duplicate resource `Patient/0`.",
      :fhir/issue := "invariant")))
