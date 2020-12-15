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
      [0 0 :hash] := #blaze/byte-string"188C7598C8AB1DBDCF94ACD7B60F3E324FBE7535CBB6A56A89C2977F4A30F9CE"
      [0 0 :refs] := [["Patient" "0"]]
      [1 0 0] := #blaze/byte-string"188C7598C8AB1DBDCF94ACD7B60F3E324FBE7535CBB6A56A89C2977F4A30F9CE"
      [1 0 1] := {:fhir/type :fhir/Observation :id "0"
                  :subject
                  {:fhir/type :fhir/Reference
                   :reference "Patient/0"}}))

  (testing "one put"
    (given (tx/prepare-ops [[:put {:fhir/type :fhir/Patient :id "0"}]])
      [0 0 :op] := "put"
      [0 0 :type] := "Patient"
      [0 0 :id] := "0"
      [0 0 :hash] := #blaze/byte-string"6F04185DAEA9350F2E9D1D80DDFCD1890B0DA1300CDD83A3AEAF21D637442E9A"
      [1 0 0] := #blaze/byte-string"6F04185DAEA9350F2E9D1D80DDFCD1890B0DA1300CDD83A3AEAF21D637442E9A"
      [1 0 1] := {:fhir/type :fhir/Patient :id "0"}))

  (testing "one put with matches"
    (given (tx/prepare-ops [[:put {:fhir/type :fhir/Patient :id "0"} 4]])
      [0 0 :if-match] := 4))

  (testing "one delete"
    (given (tx/prepare-ops [[:delete "Patient" "0"]])
      [0 0 :op] := "delete"
      [0 0 :type] := "Patient"
      [0 0 :id] := "0")))
