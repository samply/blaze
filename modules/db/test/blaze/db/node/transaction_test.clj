(ns blaze.db.node.transaction-test
  (:require
    [blaze.db.node.transaction :as tx]
    [blaze.db.node.transaction-spec]
    [blaze.fhir.spec.type :as type]
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
                        (type/map->Reference
                          {:reference "Patient/0"})}]])
      [0 0 :op] := "create"
      [0 0 :type] := "Observation"
      [0 0 :id] := "0"
      [0 0 :hash] := #blaze/byte-string"7B3980C2BFCF43A8CDD61662E1AABDA9CA6431964820BC8D52958AEC9A270378"
      [0 0 :refs] := [["Patient" "0"]]
      [1 0 0] := #blaze/byte-string"7B3980C2BFCF43A8CDD61662E1AABDA9CA6431964820BC8D52958AEC9A270378"
      [1 0 1] := {:fhir/type :fhir/Observation :id "0"
                  :subject
                  (type/map->Reference
                    {:reference "Patient/0"})}))

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
