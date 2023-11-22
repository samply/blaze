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
    (is (nil? (validation/validate-ops [[:keep "Patient" "0" #blaze/hash"7B3980C2BFCF43A8CDD61662E1AABDA9CA6431964820BC8D52958AEC9A270378"]]))))

  (testing "duplicate keep"
    (given (validation/validate-ops [[:keep "Patient" "0" #blaze/hash"7B3980C2BFCF43A8CDD61662E1AABDA9CA6431964820BC8D52958AEC9A270378"]
                                     [:keep "Patient" "0" #blaze/hash"C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F"]])
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
    (given (validation/validate-ops [[:keep "Patient" "0" #blaze/hash"C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F"]
                                     [:delete "Patient" "0"]])
      ::anom/category := ::anom/incorrect
      :cognitect.anomalies/message := "Duplicate resource `Patient/0`.",
      :fhir/issue := "invariant")))
