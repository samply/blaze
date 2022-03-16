(ns blaze.interaction.transaction.bundle-test
  (:require
    [blaze.fhir.structure-definition-repo]
    [blaze.interaction.transaction.bundle :as bundle]
    [blaze.interaction.transaction.bundle-spec]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]]))


(st/instrument)
(tu/init-fhir-specs)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest tx-ops-test
  (testing "create"
    (given
      (bundle/tx-ops
        [{:fhir/type :fhir.Bundle/entry
          :resource
          {:fhir/type :fhir/Patient
           :id "id-220129"}
          :request
          {:fhir/type :fhir.Bundle.entry/request
           :method #fhir/code "POST"
           :url #fhir/uri "Patient"}}])
      [0 count] := 2
      [0 0] := :create
      [0 1 :fhir/type] := :fhir/Patient
      [0 1 :id] := "id-220129"))

  (testing "conditional create"
    (given
      (bundle/tx-ops
        [{:fhir/type :fhir.Bundle/entry
          :resource
          {:fhir/type :fhir/Patient
           :id "id-220200"}
          :request
          {:fhir/type :fhir.Bundle.entry/request
           :method #fhir/code "POST"
           :url #fhir/uri "Patient"
           :ifNoneExist "birthdate=2020"}}])
      [0 0] := :create
      [0 1 :fhir/type] := :fhir/Patient
      [0 1 :id] := "id-220200"
      [0 2 count] := 1
      [0 2 0] := ["birthdate" "2020"]))

  (testing "update"
    (given
      (bundle/tx-ops
        [{:fhir/type :fhir.Bundle/entry
          :resource
          {:fhir/type :fhir/Patient
           :id "id-214728"}
          :request
          {:fhir/type :fhir.Bundle.entry/request
           :method #fhir/code "PUT"
           :url #fhir/uri "Patient/id-214728"}}])
      [0 count] := 2
      [0 0] := :put
      [0 1 :fhir/type] := :fhir/Patient
      [0 1 :id] := "id-214728"))

  (testing "version aware update"
    (given
      (bundle/tx-ops
        [{:fhir/type :fhir.Bundle/entry
          :resource
          {:fhir/type :fhir/Patient
           :id "id-214728"}
          :request
          {:fhir/type :fhir.Bundle.entry/request
           :method #fhir/code "PUT"
           :url #fhir/uri "Patient/id-214728"
           :ifMatch "W/\"215150\""}}])
      [0 count] := 3
      [0 0] := :put
      [0 1 :fhir/type] := :fhir/Patient
      [0 1 :id] := "id-214728"
      [0 2] := 215150))

  (testing "delete"
    (given
      (bundle/tx-ops
        [{:fhir/type :fhir.Bundle/entry
          :request
          {:fhir/type :fhir.Bundle.entry/request
           :method #fhir/code "DELETE"
           :url #fhir/uri "Patient/id-215232"}}])
      [0 count] := 3
      [0 0] := :delete
      [0 1] := "Patient"
      [0 2] := "id-215232")))
