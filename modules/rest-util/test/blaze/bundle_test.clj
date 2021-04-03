(ns blaze.bundle-test
  (:require
    [blaze.bundle :as bundle]
    [blaze.bundle-spec]
    [blaze.fhir.spec.type :as type]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/with-level :trace (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest resolve-entry-links-test
  (testing "Observation.subject reference"
    (let [entries
          [{:fullUrl #fhir/uri"urn:uuid:9ef14708-5695-4aad-8623-8c8ebd4f48ee"
            :resource
            {:fhir/type :fhir/Observation
             :id "0"
             :subject
             (type/map->Reference
               {:reference "urn:uuid:d7bd0ece-fe3c-4755-b7c9-5b86f42e304a"})}
            :request
            {:method #fhir/code"POST"
             :url #fhir/uri"Observation"}}
           {:fullUrl #fhir/uri"urn:uuid:d7bd0ece-fe3c-4755-b7c9-5b86f42e304a"
            :resource
            {:fhir/type :fhir/Patient
             :id "0"}
            :request
            {:method #fhir/code"POST"
             :url #fhir/uri"Patient"}}]]
      (given (bundle/resolve-entry-links entries)
        [0 :resource :subject :reference] := "Patient/0")))

  (testing "Patient.generalPractitioner reference"
    (let [entries
          [{:fullUrl #fhir/uri"urn:uuid:44dded80-aaf1-4988-ace4-5f3a2c9935a7"
            :resource
            {:fhir/type :fhir/Organization
             :id "0"}
            :request
            {:method #fhir/code"POST"
             :url #fhir/uri"Organization"}}
           {:fullUrl #fhir/uri"urn:uuid:61f73804-78da-4865-8c28-73bdf6f05a2e"
            :resource
            {:fhir/type :fhir/Patient
             :id "0"
             :generalPractitioner
             [(type/map->Reference
                {:reference "urn:uuid:44dded80-aaf1-4988-ace4-5f3a2c9935a7"})]}
            :request
            {:method #fhir/code"POST"
             :url #fhir/uri"Patient"}}]]
      (given (bundle/resolve-entry-links entries)
        [1 :resource :generalPractitioner 0 :reference] := "Organization/0")))

  (testing "Claim.diagnosis.diagnosisReference reference"
    (let [entries
          [{:fullUrl #fhir/uri"urn:uuid:69857788-8691-45b9-bc97-654fb93ba615"
            :resource
            {:fhir/type :fhir/Condition
             :id "0"}
            :request
            {:method #fhir/code"POST"
             :url "Condition"}}
           {:fullUrl #fhir/uri"urn:uuid:44cf9905-f381-4849-8a35-79a6b29ae1b5"
            :resource
            {:fhir/type :fhir/Claim
             :id "0"
             :diagnosis
             [{:diagnosisReference
               (type/map->Reference
                 {:reference "urn:uuid:69857788-8691-45b9-bc97-654fb93ba615"})}]}
            :request
            {:method #fhir/code"POST"
             :url #fhir/uri"Claim"}}]]
      (given (bundle/resolve-entry-links entries)
        [1 :resource :diagnosis 0 :diagnosisReference :reference] := "Condition/0")))

  (testing "preserves complex-type records"
    (let [entries
          [{:resource
            {:fhir/type :fhir/Observation
             :id "0"
             :code (type/map->CodeableConcept {})}}]]
      (given (bundle/resolve-entry-links entries)
        [0 :resource :code type/type] := :fhir/CodeableConcept))))


(deftest resolve-entry-links-in-contained-resources-test
  (let [entries
        [{:fullUrl #fhir/uri"urn:uuid:48aacf48-ba32-4aa8-ac0d-b095ac54201b"
          :resource
          {:fhir/type :fhir/Patient
           :id "0"}
          :request
          {:method #fhir/code"POST"
           :url #fhir/uri"Patient"}}
         {:fullUrl #fhir/uri"urn:uuid:d0f40d1f-2f95-4990-a994-8182cfe71bc2"
          :resource
          {:fhir/type :fhir/ExplanationOfBenefit
           :id "0"
           :contained
           [{:fhir/type :fhir/ServiceRequest
             :id "0"
             :subject
             (type/map->Reference
               {:reference "urn:uuid:48aacf48-ba32-4aa8-ac0d-b095ac54201b"})}]}
          :request
          {:method #fhir/code"POST"
           :url #fhir/uri"ExplanationOfBenefit"}}]]
    (given (bundle/resolve-entry-links entries)
      [1 :resource :contained 0 :subject :reference] := "Patient/0")))


(deftest tx-ops-text
  (testing "conditional create"
    (given
      (bundle/tx-ops
        [{:fhir/type :fhir.Bundle/entry
          :resource
          {:fhir/type :fhir/Patient
           :id "id-162512"}
          :request
          {:fhir/type :fhir.Bundle.entry/request
           :method #fhir/code"POST"
           :url #fhir/uri"Patient"
           :ifNoneExist "birthdate=2020"}}])
      [0 0] := :create
      [0 1 :fhir/type] := :fhir/Patient
      [0 1 :id] := "id-162512"
      [0 2 0] := ["birthdate" "2020"])))
