(ns blaze.db.node.transaction-test
  (:require
    [blaze.db.node.transaction :as tx]
    [blaze.db.node.transaction-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest prepare-ops
  (testing "one create with references"
    (given (tx/prepare-ops
             [[:create {:fhir/type :fhir/Observation :id "0"
                        :subject
                        {:fhir/type :fhir/Reference
                         :reference "Patient/0"}}]])
      [0 0 :op] := "create"
      [0 0 :type] := "Observation"
      [0 0 :id] := "0"
      [0 0 :hash] := #blaze/byte-string"3E2D4F15EFD656DE2EAB5237CB5EFDB452FF4A21F18DD808AC14BEB2D83DF2BB"
      [0 0 :refs] := [["Patient" "0"]]
      [1 0 0] := #blaze/byte-string"3E2D4F15EFD656DE2EAB5237CB5EFDB452FF4A21F18DD808AC14BEB2D83DF2BB"
      [1 0 1] := {:fhir/type :fhir/Observation :id "0"
                  :subject
                  {:fhir/type :fhir/Reference
                   :reference "Patient/0"}}))

  (testing "one put"
    (given (tx/prepare-ops [[:put {:fhir/type :fhir/Patient :id "0"}]])
      [0 0 :op] := "put"
      [0 0 :type] := "Patient"
      [0 0 :id] := "0"
      [0 0 :hash] := #blaze/byte-string"C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F"
      [1 0 0] := #blaze/byte-string"C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F"
      [1 0 1] := {:fhir/type :fhir/Patient :id "0"}))

  (testing "one put with matches"
    (given (tx/prepare-ops [[:put {:fhir/type :fhir/Patient :id "0"} 4]])
      [0 0 :if-match] := 4))

  (testing "one delete"
    (given (tx/prepare-ops [[:delete "Patient" "0"]])
      [0 0 :op] := "delete"
      [0 0 :type] := "Patient"
      [0 0 :id] := "0")))
