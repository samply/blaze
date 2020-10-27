(ns blaze.db.node.transaction-test
  (:require
    [blaze.db.node.transaction :as tx]
    [blaze.db.node.transaction-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest prepare-ops
  (testing "no ops"
    (given (tx/prepare-ops [])
      0 := []
      1 := {}))

  (testing "one create with references"
    (given (tx/prepare-ops
             [[:create {:fhir/type :fhir/Observation :id "0"
                        :subject
                        {:fhir/type :fhir/Reference
                         :reference "Patient/0"}}]])
      [0 0 :op] := "create"
      [0 0 :type] := "Observation"
      [0 0 :id] := "0"
      [0 0 :hash str] := "188c7598c8ab1dbdcf94acd7b60f3e324fbe7535cbb6a56a89c2977f4a30f9ce"
      [0 0 :refs] := [["Patient" "0"]]
      [1 0 0 str] := "188c7598c8ab1dbdcf94acd7b60f3e324fbe7535cbb6a56a89c2977f4a30f9ce"
      [1 0 1] := {:fhir/type :fhir/Observation :id "0"
                  :subject
                  {:fhir/type :fhir/Reference
                   :reference "Patient/0"}}))

  (testing "one put"
    (given (tx/prepare-ops [[:put {:fhir/type :fhir/Patient :id "0"}]])
      [0 0 :op] := "put"
      [0 0 :type] := "Patient"
      [0 0 :id] := "0"
      [0 0 :hash str] := "6f04185daea9350f2e9d1d80ddfcd1890b0da1300cdd83a3aeaf21d637442e9a"
      [1 0 0 str] := "6f04185daea9350f2e9d1d80ddfcd1890b0da1300cdd83a3aeaf21d637442e9a"
      [1 0 1] := {:fhir/type :fhir/Patient :id "0"}))

  (testing "one put with matches"
    (given (tx/prepare-ops [[:put {:fhir/type :fhir/Patient :id "0"} 4]])
      [0 0 :if-match] := 4))

  (testing "one delete"
    (given (tx/prepare-ops [[:delete "Patient" "0"]])
      [0 0 :op] := "delete"
      [0 0 :type] := "Patient"
      [0 0 :id] := "0")))
