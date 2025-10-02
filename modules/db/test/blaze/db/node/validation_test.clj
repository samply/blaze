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
  (testing "single keep"
    (is (nil? (validation/validate-ops [[:keep "Patient" "0" #blaze/hash"37F2FC44C33CBF31C23E78F12A58D42985E86B39767C020F811212FD07946850"]]))))

  (testing "duplicate keep"
    (given (validation/validate-ops [[:keep "Patient" "0" #blaze/hash"37F2FC44C33CBF31C23E78F12A58D42985E86B39767C020F811212FD07946850"]
                                     [:keep "Patient" "0" #blaze/hash"5EE37C94FB1626111B5C2D37F7C2ECAF21B50B9D0FB45FA189889F38D0F9A470"]])
      ::anom/category := ::anom/incorrect
      :cognitect.anomalies/message := "Duplicate resource `Patient/0`.",
      :fhir/issue := "invariant"))

  (testing "single delete"
    (is (nil? (validation/validate-ops [[:delete "Patient" "0"]]))))

  (testing "duplicate delete"
    (given (validation/validate-ops [[:delete "Patient" "0"]
                                     [:delete "Patient" "0"]])
      ::anom/category := ::anom/incorrect
      :cognitect.anomalies/message := "Duplicate resource `Patient/0`.",
      :fhir/issue := "invariant"))

  (testing "duplicate keep/delete"
    (given (validation/validate-ops [[:keep "Patient" "0" #blaze/hash"5EE37C94FB1626111B5C2D37F7C2ECAF21B50B9D0FB45FA189889F38D0F9A470"]
                                     [:delete "Patient" "0"]])
      ::anom/category := ::anom/incorrect
      :cognitect.anomalies/message := "Duplicate resource `Patient/0`.",
      :fhir/issue := "invariant")))
