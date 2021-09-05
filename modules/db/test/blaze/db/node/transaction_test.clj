(ns blaze.db.node.transaction-test
  (:require
    [blaze.db.impl.index.tx-error :as tx-error]
    [blaze.db.impl.index.tx-success :as tx-success]
    [blaze.db.node.transaction :as tx]
    [blaze.db.node.transaction-spec]
    [blaze.fhir.spec.type]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest prepare-ops-test
  (testing "create"
    (testing "with references"
      (given (tx/prepare-ops
               [[:create {:fhir/type :fhir/Observation :id "0"
                          :subject #fhir/Reference{:reference "Patient/0"}}]])
        [0 0 :op] := "create"
        [0 0 :type] := "Observation"
        [0 0 :id] := "0"
        [0 0 :hash] := #blaze/byte-string"7B3980C2BFCF43A8CDD61662E1AABDA9CA6431964820BC8D52958AEC9A270378"
        [0 0 :refs] := [["Patient" "0"]]
        [1 0 0] := #blaze/byte-string"7B3980C2BFCF43A8CDD61662E1AABDA9CA6431964820BC8D52958AEC9A270378"
        [1 0 1] := {:fhir/type :fhir/Observation :id "0"
                    :subject #fhir/Reference{:reference "Patient/0"}}))

    (testing "conditional"
      (given (tx/prepare-ops
               [[:create {:fhir/type :fhir/Patient :id "id-220036"}
                 [["identifier" "115508"]]]])
        [0 0 :op] := "create"
        [0 0 :type] := "Patient"
        [0 0 :id] := "id-220036"
        [0 0 :if-none-exist] := [["identifier" "115508"]])))

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


(deftest load-tx-result-test
  (with-redefs [tx-success/tx (fn [_ _])
                tx-error/tx-error (fn [_ _])]
    (given (tx/load-tx-result ::node 214912)
      ::anom/category := ::anom/fault
      ::anom/message := "Can't find transaction result with point in time of 214912.")))
