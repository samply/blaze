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
    (given (tx/prepare-ops [[:create {:resourceType "Observation" :id "0"
                                      :subject {:reference "Patient/0"}}]])
      [0 0 :op] := "create"
      [0 0 :type] := "Observation"
      [0 0 :id] := "0"
      [0 0 :hash str] := "83d07fee602bf2b8ecd978f75b1a0a9306d39af76198f091bf614663b7b17008"
      [0 0 :refs] := [["Patient" "0"]]
      [1 0 0 str] := "83d07fee602bf2b8ecd978f75b1a0a9306d39af76198f091bf614663b7b17008"
      [1 0 1] := {:resourceType "Observation" :id "0"
                  :subject {:reference "Patient/0"}}))

  (testing "one put"
    (given (tx/prepare-ops [[:put {:resourceType "Patient" :id "0"}]])
      [0 0 :op] := "put"
      [0 0 :type] := "Patient"
      [0 0 :id] := "0"
      [0 0 :hash str] := "f51f0ad0ef664870197108ff4c463235461eed5556615f770dda9416218c7512"
      [1 0 0 str] := "f51f0ad0ef664870197108ff4c463235461eed5556615f770dda9416218c7512"
      [1 0 1] := {:resourceType "Patient" :id "0"}))

  (testing "one put with matches"
    (given (tx/prepare-ops [[:put {:resourceType "Patient" :id "0"} 4]])
      [0 0 :if-match] := 4))

  (testing "one delete"
    (given (tx/prepare-ops [[:delete "Patient" "0"]])
      [0 0 :op] := "delete"
      [0 0 :type] := "Patient"
      [0 0 :id] := "0")))
