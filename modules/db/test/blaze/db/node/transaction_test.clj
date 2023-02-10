(ns blaze.db.node.transaction-test
  (:require
    [blaze.db.impl.index.tx-error :as tx-error]
    [blaze.db.impl.index.tx-success :as tx-success]
    [blaze.db.node.transaction :as tx]
    [blaze.db.node.transaction-spec]
    [blaze.fhir.spec.type]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


(st/instrument)
(tu/init-fhir-specs)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def context
  {})


(deftest prepare-ops-test
  (testing "create"
    (testing "with references"
      (let [resource
            {:fhir/type :fhir/Observation :id "0"
             :subject #fhir/Reference{:reference #fhir/string"Patient/0"}}]
        (given (tx/prepare-ops context [[:create resource]])
          [0 :op] := "create"
          [0 :type] := "Observation"
          [0 :id] := "0"
          [0 :resource] := resource
          [0 :refs] := [["Patient" "0"]]))

      (testing "with extended reference.reference"
        (given (tx/prepare-ops
                 context
                 [[:create
                   {:fhir/type :fhir/Observation :id "0"
                    :subject #fhir/Reference
                           {:reference #fhir/string
                                   {:extension [#fhir/Extension{:url "foo"}]
                                    :value "Patient/190740"}}}]])
          [0 :refs] := [["Patient" "190740"]])

        (testing "without value"
          (given (tx/prepare-ops
                   context
                   [[:create
                     {:fhir/type :fhir/Observation :id "0"
                      :subject #fhir/Reference
                             {:reference #fhir/string
                                     {:extension [#fhir/Extension{:url "foo"}]}}}]])
            [0 :refs] :? empty?)))

      (testing "with disabled referential integrity check"
        (given (tx/prepare-ops
                 {:blaze.db/enforce-referential-integrity false}
                 [[:create {:fhir/type :fhir/Observation :id "0"
                            :subject #fhir/Reference{:reference #fhir/string"Patient/0"}}]])
          [0 :refs] :? empty?)))

    (testing "conditional"
      (given (tx/prepare-ops
               context
               [[:create {:fhir/type :fhir/Patient :id "id-220036"}
                 [["identifier" "115508"]]]])
        [0 :op] := "create"
        [0 :type] := "Patient"
        [0 :id] := "id-220036"
        [0 :if-none-exist] := [["identifier" "115508"]])))

  (testing "put"
    (let [resource {:fhir/type :fhir/Patient :id "0"}]
      (given (tx/prepare-ops context [[:put resource]])
        [0 :op] := "put"
        [0 :type] := "Patient"
        [0 :id] := "0"
        [0 :resource] := resource))

    (testing "with references"
      (let [resource
            {:fhir/type :fhir/Observation :id "0"
             :subject #fhir/Reference{:reference #fhir/string"Patient/0"}}]
        (given (tx/prepare-ops context [[:put resource]])
          [0 :op] := "put"
          [0 :type] := "Observation"
          [0 :id] := "0"
          [0 :resource] := resource
          [0 :refs] := [["Patient" "0"]]))

      (testing "with disabled referential integrity check"
        (given (tx/prepare-ops {:blaze.db/enforce-referential-integrity false}
                               [[:put {:fhir/type :fhir/Observation :id "0"
                                       :subject #fhir/Reference{:reference #fhir/string"Patient/0"}}]])
          [0 0 :refs] :? empty?)))

    (testing "with matches"
      (given (tx/prepare-ops context [[:put {:fhir/type :fhir/Patient :id "0"} [:if-match 4]]])
        [0 :if-match] := 4)))

  (testing "delete"
    (given (tx/prepare-ops context [[:delete "Patient" "0"]])
      [0 :op] := "delete"
      [0 :type] := "Patient"
      [0 :id] := "0")))


(deftest load-tx-result-test
  (with-redefs [tx-success/tx (fn [_ _])
                tx-error/tx-error (fn [_ _])]
    (given (tx/load-tx-result ::node 214912)
      ::anom/category := ::anom/fault
      ::anom/message := "Can't find transaction result with point in time of 214912.")))
