(ns blaze.db.node.transaction-test
  (:require
   [blaze.db.impl.index.tx-error :as tx-error]
   [blaze.db.impl.index.tx-success :as tx-success]
   [blaze.db.node.transaction :as tx]
   [blaze.db.node.transaction-spec]
   [blaze.fhir.test-util]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def context
  {})

(deftest prepare-ops-test
  (testing "create"
    (testing "with references"
      (given (tx/prepare-ops
              context
              [[:create {:fhir/type :fhir/Observation :id "0"
                         :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]])
        [0 0 :op] := "create"
        [0 0 :type] := "Observation"
        [0 0 :id] := "0"
        [0 0 :hash] := #blaze/hash"7B3980C2BFCF43A8CDD61662E1AABDA9CA6431964820BC8D52958AEC9A270378"
        [0 0 :refs] := [["Patient" "0"]]
        [1 0 0] := #blaze/hash"7B3980C2BFCF43A8CDD61662E1AABDA9CA6431964820BC8D52958AEC9A270378"
        [1 0 1] := {:fhir/type :fhir/Observation :id "0"
                    :subject #fhir/Reference{:reference #fhir/string "Patient/0"}})

      (testing "with extended reference.reference"
        (given (tx/prepare-ops
                context
                [[:create
                  {:fhir/type :fhir/Observation :id "0"
                   :subject #fhir/Reference
                             {:reference #fhir/string
                                          {:extension [#fhir/Extension{:url "foo"}]
                                           :value "Patient/190740"}}}]])
          [0 0 :refs] := [["Patient" "190740"]])

        (testing "without value"
          (given (tx/prepare-ops
                  context
                  [[:create
                    {:fhir/type :fhir/Observation :id "0"
                     :subject #fhir/Reference
                               {:reference #fhir/string
                                            {:extension [#fhir/Extension{:url "foo"}]}}}]])
            [0 0 :refs] :? empty?)))

      (testing "with disabled referential integrity check"
        (given (tx/prepare-ops
                {:blaze.db/enforce-referential-integrity false}
                [[:create {:fhir/type :fhir/Observation :id "0"
                           :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]])
          [0 0 :refs] :? empty?)))

    (testing "conditional"
      (given (tx/prepare-ops
              context
              [[:create {:fhir/type :fhir/Patient :id "id-220036"}
                [["identifier" "115508"]]]])
        [0 0 :op] := "create"
        [0 0 :type] := "Patient"
        [0 0 :id] := "id-220036"
        [0 0 :if-none-exist] := [["identifier" "115508"]])))

  (testing "put"
    (given (tx/prepare-ops context [[:put {:fhir/type :fhir/Patient :id "0"}]])
      [0 0 :op] := "put"
      [0 0 :type] := "Patient"
      [0 0 :id] := "0"
      [0 0 :hash] := #blaze/hash"C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F"
      [1 0 0] := #blaze/hash"C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F"
      [1 0 1] := {:fhir/type :fhir/Patient :id "0"})

    (testing "with references"
      (given (tx/prepare-ops
              context
              [[:put {:fhir/type :fhir/Observation :id "0"
                      :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]])
        [0 0 :op] := "put"
        [0 0 :type] := "Observation"
        [0 0 :id] := "0"
        [0 0 :hash] := #blaze/hash"7B3980C2BFCF43A8CDD61662E1AABDA9CA6431964820BC8D52958AEC9A270378"
        [0 0 :refs] := [["Patient" "0"]]
        [1 0 0] := #blaze/hash"7B3980C2BFCF43A8CDD61662E1AABDA9CA6431964820BC8D52958AEC9A270378"
        [1 0 1] := {:fhir/type :fhir/Observation :id "0"
                    :subject #fhir/Reference{:reference #fhir/string "Patient/0"}})

      (testing "with disabled referential integrity check"
        (given (tx/prepare-ops {:blaze.db/enforce-referential-integrity false}
                               [[:put {:fhir/type :fhir/Observation :id "0"
                                       :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]])
          [0 0 :refs] :? empty?)))

    (testing "with matches"
      (satisfies-prop 100
        (prop/for-all [if-match (gen/vector (s/gen :blaze.db/t) 1 10)]
          (let [tx-op [:put {:fhir/type :fhir/Patient :id "0"} (into [:if-match] if-match)]]
            (= if-match (:if-match (ffirst (tx/prepare-ops context [tx-op])))))))))

  (testing "keep"
    (let [hash #blaze/hash"7B3980C2BFCF43A8CDD61662E1AABDA9CA6431964820BC8D52958AEC9A270378"]
      (testing "without any if-match ts"
        (given (tx/prepare-ops context [[:keep "Patient" "0" hash]])
          [0 0 :op] := "keep"
          [0 0 :type] := "Patient"
          [0 0 :id] := "0"
          [0 0 :hash] := hash
          [1] := {}))

      (testing "with matches"
        (satisfies-prop 100
          (prop/for-all [if-match (gen/vector (s/gen :blaze.db/t) 1 10)]
            (let [tx-op [:keep "Patient" "0" hash if-match]]
              (= if-match (:if-match (ffirst (tx/prepare-ops context [tx-op]))))))))))

  (testing "delete"
    (testing "with referential integrity enabled (by default)"
      (given (tx/prepare-ops {} [[:delete "Patient" "0"]])
        [0 0 :op] := "delete"
        [0 0 :type] := "Patient"
        [0 0 :id] := "0"
        [0 0 :check-refs] := true
        [1] := {}))

    (testing "with referential integrity disabled"
      (given (tx/prepare-ops {:blaze.db/enforce-referential-integrity false} [[:delete "Patient" "0"]])
        [0 0 :op] := "delete"
        [0 0 :type] := "Patient"
        [0 0 :id] := "0"
        [0 0 :check-refs] := nil
        [1] := {})))

  (testing "conditional-delete"
    (testing "with referential integrity enabled (by default)"
      (testing "with clauses"
        (given (tx/prepare-ops {} [[:conditional-delete "Patient" [["identifier" "111033"]]]])
          [0 0 :op] := "conditional-delete"
          [0 0 :type] := "Patient"
          [0 0 :clauses] := [["identifier" "111033"]]
          [0 0 :check-refs] := true
          [1] := {}))

      (testing "without clauses"
        (given (tx/prepare-ops {} [[:conditional-delete "Patient"]])
          [0 0 :op] := "conditional-delete"
          [0 0 :type] := "Patient"
          [0 0 :clauses] := nil
          [0 0 :check-refs] := true
          [1] := {})))

    (testing "with referential integrity disabled"
      (given (tx/prepare-ops {:blaze.db/enforce-referential-integrity false} [[:conditional-delete "Patient" [["identifier" "111033"]]]])
        [0 0 :op] := "conditional-delete"
        [0 0 :type] := "Patient"
        [0 0 :clauses] := [["identifier" "111033"]]
        [0 0 :check-refs] := nil
        [1] := {}))))

(deftest load-tx-result-test
  (with-redefs [tx-success/tx (fn [_ _])
                tx-error/tx-error (fn [_ _])]
    (given (tx/load-tx-result ::node 214912)
      ::anom/category := ::anom/fault
      ::anom/message := "Can't find transaction result with point in time of 214912.")))
