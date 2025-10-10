(ns blaze.interaction.transaction.bundle.links-test
  (:require
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util]
   [blaze.interaction.transaction.bundle.links :as links]
   [blaze.interaction.transaction.bundle.links-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest resolve-entry-links-test
  (testing "Observation.subject reference"
    (testing "using URNs"
      (let [entries
            [{:fhir/type :fhir.Bundle/entry
              :fullUrl #fhir/uri "urn:uuid:9ef14708-5695-4aad-8623-8c8ebd4f48ee"
              :resource
              {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "urn:uuid:d7bd0ece-fe3c-4755-b7c9-5b86f42e304a"}}
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "POST"
               :url #fhir/uri "Observation"}}
             {:fhir/type :fhir.Bundle/entry
              :fullUrl #fhir/uri "urn:uuid:d7bd0ece-fe3c-4755-b7c9-5b86f42e304a"
              :resource
              {:fhir/type :fhir/Patient
               :id "160210"}
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "POST"
               :url #fhir/uri "Patient"}}]]
        (given (links/resolve-entry-links entries)
          [0 :resource :subject :reference] := #fhir/string "Patient/160210")))

    (testing "using absolute URLs"
      (let [entries
            [{:fhir/type :fhir.Bundle/entry
              :fullUrl #fhir/uri "http://fhir.example.org/Observation/091048"
              :resource
              {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "http://fhir.example.org/Patient/091057"}}
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "POST"
               :url #fhir/uri "Observation"}}
             {:fhir/type :fhir.Bundle/entry
              :fullUrl #fhir/uri "http://fhir.example.org/Patient/091057"
              :resource
              {:fhir/type :fhir/Patient
               :id "160210"}
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "POST"
               :url #fhir/uri "Patient"}}]]
        (given (links/resolve-entry-links entries)
          [0 :resource :subject :reference] := #fhir/string "Patient/160210")))

    (testing "using relative RESTful URLs"
      (let [entries
            [{:fhir/type :fhir.Bundle/entry
              :fullUrl #fhir/uri "http://fhir.example.org/Observation/091048"
              :resource
              {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/091057"}}
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "POST"
               :url #fhir/uri "Observation"}}
             {:fhir/type :fhir.Bundle/entry
              :fullUrl #fhir/uri "http://fhir.example.org/Patient/091057"
              :resource
              {:fhir/type :fhir/Patient
               :id "160210"}
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "POST"
               :url #fhir/uri "Patient"}}]]
        (given (links/resolve-entry-links entries)
          [0 :resource :subject :reference] := #fhir/string "Patient/160210"))

      (testing "with two different base URLs"
        (let [entries
              (mapcat
               (fn [host]
                 [{:fhir/type :fhir.Bundle/entry
                   :fullUrl (type/uri (format "http://%s.org/Observation/091048" host))
                   :resource
                   {:fhir/type :fhir/Observation :id "0"
                    :subject #fhir/Reference{:reference #fhir/string "Patient/091057"}}
                   :request
                   {:fhir/type :fhir.Bundle.entry/request
                    :method #fhir/code "POST"
                    :url #fhir/uri "Observation"}}
                  {:fhir/type :fhir.Bundle/entry
                   :fullUrl (type/uri (format "http://%s.org/Patient/091057" host))
                   :resource
                   {:fhir/type :fhir/Patient
                    :id host}
                   :request
                   {:fhir/type :fhir.Bundle.entry/request
                    :method #fhir/code "POST"
                    :url #fhir/uri "Patient"}}])
               ["example-1" "example-2"])]
          (given (links/resolve-entry-links entries)
            [0 :resource :subject :reference] := #fhir/string "Patient/example-1"
            [2 :resource :subject :reference] := #fhir/string "Patient/example-2"))))

    (testing "preserving the id on the Reference.reference"
      (let [entries
            [{:fhir/type :fhir.Bundle/entry
              :fullUrl #fhir/uri "urn:uuid:9ef14708-5695-4aad-8623-8c8ebd4f48ee"
              :resource
              {:fhir/type :fhir/Observation :id "0"
               :subject
               #fhir/Reference
                {:reference #fhir/string{:id "id-211320"
                                         :value "urn:uuid:d7bd0ece-fe3c-4755-b7c9-5b86f42e304a"}}}
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "POST"
               :url #fhir/uri "Observation"}}
             {:fhir/type :fhir.Bundle/entry
              :fullUrl #fhir/uri "urn:uuid:d7bd0ece-fe3c-4755-b7c9-5b86f42e304a"
              :resource
              {:fhir/type :fhir/Patient
               :id "160210"}
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "POST"
               :url #fhir/uri "Patient"}}]]
        (given (links/resolve-entry-links entries)
          [0 :resource :subject :reference] := #fhir/string{:id "id-211320"
                                                            :value "Patient/160210"}))))

  (testing "Patient.generalPractitioner reference"
    (let [entries
          [{:fhir/type :fhir.Bundle/entry
            :fullUrl #fhir/uri "urn:uuid:61f73804-78da-4865-8c28-73bdf6f05a2e"
            :resource
            {:fhir/type :fhir/Patient
             :id "0"
             :generalPractitioner
             [#fhir/Reference
               {:reference #fhir/string "urn:uuid:44dded80-aaf1-4988-ace4-5f3a2c9935a7"}]}
            :request
            {:fhir/type :fhir.Bundle.entry/request
             :method #fhir/code "POST"
             :url #fhir/uri "Patient"}}
           {:fhir/type :fhir.Bundle/entry
            :fullUrl #fhir/uri "urn:uuid:44dded80-aaf1-4988-ace4-5f3a2c9935a7"
            :resource
            {:fhir/type :fhir/Organization
             :id "160200"}
            :request
            {:fhir/type :fhir.Bundle.entry/request
             :method #fhir/code "POST"
             :url #fhir/uri "Organization"}}]]
      (given (links/resolve-entry-links entries)
        [0 :resource :generalPractitioner 0 :reference] := #fhir/string "Organization/160200")))

  (testing "Claim.diagnosis.diagnosisReference reference"
    (let [entries
          [{:fhir/type :fhir.Bundle/entry
            :fullUrl #fhir/uri "urn:uuid:44cf9905-f381-4849-8a35-79a6b29ae1b5"
            :resource
            {:fhir/type :fhir/Claim
             :id "0"
             :diagnosis
             [{:fhir/type :fhir.Claim/diagnosis
               :diagnosisReference
               #fhir/Reference
                {:reference #fhir/string "urn:uuid:69857788-8691-45b9-bc97-654fb93ba615"}}]}
            :request
            {:fhir/type :fhir.Bundle.entry/request
             :method #fhir/code "POST"
             :url #fhir/uri "Claim"}}
           {:fhir/type :fhir.Bundle/entry
            :fullUrl #fhir/uri "urn:uuid:69857788-8691-45b9-bc97-654fb93ba615"
            :resource
            {:fhir/type :fhir/Condition
             :id "160146"}
            :request
            {:fhir/type :fhir.Bundle.entry/request
             :method #fhir/code "POST"
             :url #fhir/uri "Condition"}}]]
      (given (links/resolve-entry-links entries)
        [0 :resource :diagnosis 0 :diagnosisReference :reference] := #fhir/string "Condition/160146")))

  (testing "Attachment.url"
    (testing "resolves the Binary resource"
      (let [entries
            [{:fhir/type :fhir.Bundle/entry
              :fullUrl #fhir/uri "urn:uuid:44cf9905-f381-4849-8a35-79a6b29ae1b5"
              :resource
              {:fhir/type :fhir/DocumentReference
               :id "0"
               :content
               [{:fhir/type :fhir.DocumentReference/content
                 :attachment
                 #fhir/Attachment{:url #fhir/url "urn:uuid:5b016a4d-d393-48df-8d92-7ac4d1b8e56d"}}]}
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "POST"
               :url #fhir/uri "DocumentReference"}}
             {:fhir/type :fhir.Bundle/entry
              :fullUrl #fhir/uri "urn:uuid:5b016a4d-d393-48df-8d92-7ac4d1b8e56d"
              :resource
              {:fhir/type :fhir/Binary
               :id "160527"}
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "POST"
               :url #fhir/uri "Binary"}}]]
        (given (links/resolve-entry-links entries)
          [0 :resource :content 0 :attachment :url] := #fhir/url "Binary/160527"))

      (testing "preserving the id on the URL"
        (let [entries
              [{:fhir/type :fhir.Bundle/entry
                :fullUrl #fhir/uri "urn:uuid:44cf9905-f381-4849-8a35-79a6b29ae1b5"
                :resource
                {:fhir/type :fhir/DocumentReference
                 :id "0"
                 :content
                 [{:fhir/type :fhir.DocumentReference/content
                   :attachment
                   #fhir/Attachment
                    {:url #fhir/url
                           {:id "id-204917"
                            :value "urn:uuid:5b016a4d-d393-48df-8d92-7ac4d1b8e56d"}}}]}
                :request
                {:fhir/type :fhir.Bundle.entry/request
                 :method #fhir/code "POST"
                 :url #fhir/uri "DocumentReference"}}
               {:fhir/type :fhir.Bundle/entry
                :fullUrl #fhir/uri "urn:uuid:5b016a4d-d393-48df-8d92-7ac4d1b8e56d"
                :resource
                {:fhir/type :fhir/Binary
                 :id "160527"}
                :request
                {:fhir/type :fhir.Bundle.entry/request
                 :method #fhir/code "POST"
                 :url #fhir/uri "Binary"}}]]
          (given (links/resolve-entry-links entries)
            [0 :resource :content 0 :attachment :url] := #fhir/url{:id "id-204917"
                                                                   :value "Binary/160527"})))

      (testing "preserving the extension on the URL"
        (let [entries
              [{:fhir/type :fhir.Bundle/entry
                :fullUrl #fhir/uri "urn:uuid:44cf9905-f381-4849-8a35-79a6b29ae1b5"
                :resource
                {:fhir/type :fhir/DocumentReference
                 :id "0"
                 :content
                 [{:fhir/type :fhir.DocumentReference/content
                   :attachment
                   #fhir/Attachment
                    {:url #fhir/url
                           {:extension [#fhir/Extension{:url "foo"}]
                            :value "urn:uuid:5b016a4d-d393-48df-8d92-7ac4d1b8e56d"}}}]}
                :request
                {:fhir/type :fhir.Bundle.entry/request
                 :method #fhir/code "POST"
                 :url #fhir/uri "DocumentReference"}}
               {:fhir/type :fhir.Bundle/entry
                :fullUrl #fhir/uri "urn:uuid:5b016a4d-d393-48df-8d92-7ac4d1b8e56d"
                :resource
                {:fhir/type :fhir/Binary
                 :id "160527"}
                :request
                {:fhir/type :fhir.Bundle.entry/request
                 :method #fhir/code "POST"
                 :url #fhir/uri "Binary"}}]]
          (given (links/resolve-entry-links entries)
            [0 :resource :content 0 :attachment :url] := #fhir/url{:extension [#fhir/Extension{:url "foo"}]
                                                                   :value "Binary/160527"}))))

    (testing "does nothing at not found URL value"
      (let [entries
            [{:fhir/type :fhir.Bundle/entry
              :fullUrl #fhir/uri "urn:uuid:44cf9905-f381-4849-8a35-79a6b29ae1b5"
              :resource
              {:fhir/type :fhir/DocumentReference
               :id "0"
               :content
               [{:fhir/type :fhir.DocumentReference/content
                 :attachment
                 #fhir/Attachment{:url #fhir/url "urn:uuid:839c8ffb-de0b-4835-8edf-9d3d2e14d9a7"}}]}
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "POST"
               :url #fhir/uri "DocumentReference"}}]]
        (given (links/resolve-entry-links entries)
          [0 :resource :content 0 :attachment :url] := #fhir/url "urn:uuid:839c8ffb-de0b-4835-8edf-9d3d2e14d9a7")))

    (testing "does nothing at missing URL value"
      (let [entries
            [{:fhir/type :fhir.Bundle/entry
              :fullUrl #fhir/uri "urn:uuid:44cf9905-f381-4849-8a35-79a6b29ae1b5"
              :resource
              {:fhir/type :fhir/DocumentReference
               :id "0"
               :content
               [{:fhir/type :fhir.DocumentReference/content
                 :attachment
                 #fhir/Attachment{:url #fhir/url{:id "foo"}}}]}
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "POST"
               :url #fhir/uri "DocumentReference"}}]]
        (given (links/resolve-entry-links entries)
          [0 :resource :content 0 :attachment :url] := #fhir/url{:id "foo"}))))

  (testing "ConceptMap.source"
    (testing "resolves the ValueSet resource"
      (let [entries
            [{:fhir/type :fhir.Bundle/entry
              :fullUrl #fhir/uri "urn:uuid:44cf9905-f381-4849-8a35-79a6b29ae1b5"
              :resource
              {:fhir/type :fhir/ConceptMap
               :id "0"
               :source #fhir/uri "urn:uuid:76566123-cf61-440b-98f5-7eda0bfde07a"}
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "POST"
               :url #fhir/uri "ConceptMap"}}
             {:fhir/type :fhir.Bundle/entry
              :fullUrl #fhir/uri "urn:uuid:76566123-cf61-440b-98f5-7eda0bfde07a"
              :resource
              {:fhir/type :fhir/ValueSet
               :id "165422"}
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "POST"
               :url #fhir/uri "ValueSet"}}]]
        (given (links/resolve-entry-links entries)
          [0 :resource :source] := #fhir/uri "ValueSet/165422"))

      (testing "preserving the id on the URI"
        (let [entries
              [{:fhir/type :fhir.Bundle/entry
                :fullUrl #fhir/uri "urn:uuid:44cf9905-f381-4849-8a35-79a6b29ae1b5"
                :resource
                {:fhir/type :fhir/ConceptMap
                 :id "0"
                 :source #fhir/uri{:id "id-205412"
                                   :value "urn:uuid:76566123-cf61-440b-98f5-7eda0bfde07a"}}
                :request
                {:fhir/type :fhir.Bundle.entry/request
                 :method #fhir/code "POST"
                 :url #fhir/uri "ConceptMap"}}
               {:fhir/type :fhir.Bundle/entry
                :fullUrl #fhir/uri "urn:uuid:76566123-cf61-440b-98f5-7eda0bfde07a"
                :resource
                {:fhir/type :fhir/ValueSet
                 :id "165422"}
                :request
                {:fhir/type :fhir.Bundle.entry/request
                 :method #fhir/code "POST"
                 :url #fhir/uri "ValueSet"}}]]
          (given (links/resolve-entry-links entries)
            [0 :resource :source] := #fhir/uri{:id "id-205412"
                                               :value "ValueSet/165422"})))

      (testing "preserving the extension on the URI"
        (let [entries
              [{:fhir/type :fhir.Bundle/entry
                :fullUrl #fhir/uri "urn:uuid:44cf9905-f381-4849-8a35-79a6b29ae1b5"
                :resource
                {:fhir/type :fhir/ConceptMap
                 :id "0"
                 :source #fhir/uri{:extension [#fhir/Extension{:url "foo"}]
                                   :value "urn:uuid:76566123-cf61-440b-98f5-7eda0bfde07a"}}
                :request
                {:fhir/type :fhir.Bundle.entry/request
                 :method #fhir/code "POST"
                 :url #fhir/uri "ConceptMap"}}
               {:fhir/type :fhir.Bundle/entry
                :fullUrl #fhir/uri "urn:uuid:76566123-cf61-440b-98f5-7eda0bfde07a"
                :resource
                {:fhir/type :fhir/ValueSet
                 :id "165422"}
                :request
                {:fhir/type :fhir.Bundle.entry/request
                 :method #fhir/code "POST"
                 :url #fhir/uri "ValueSet"}}]]
          (given (links/resolve-entry-links entries)
            [0 :resource :source] := #fhir/uri{:extension [#fhir/Extension{:url "foo"}]
                                               :value "ValueSet/165422"}))))

    (testing "does nothing at not found URI value"
      (let [entries
            [{:fhir/type :fhir.Bundle/entry
              :fullUrl #fhir/uri "urn:uuid:44cf9905-f381-4849-8a35-79a6b29ae1b5"
              :resource
              {:fhir/type :fhir/ConceptMap
               :id "0"
               :source #fhir/uri "urn:uuid:84a95644-82d8-48bc-85a4-36e7d80d176b"}
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "POST"
               :url #fhir/uri "ConceptMap"}}]]
        (given (links/resolve-entry-links entries)
          [0 :resource :source] := #fhir/uri "urn:uuid:84a95644-82d8-48bc-85a4-36e7d80d176b")))

    (testing "does nothing at missing URI value"
      (let [entries
            [{:fhir/type :fhir.Bundle/entry
              :fullUrl #fhir/uri "urn:uuid:44cf9905-f381-4849-8a35-79a6b29ae1b5"
              :resource
              {:fhir/type :fhir/ConceptMap
               :id "0"
               :source #fhir/uri{:id "foo"}}
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "POST"
               :url #fhir/uri "ConceptMap"}}]]
        (given (links/resolve-entry-links entries)
          [0 :resource :source] := #fhir/uri{:id "foo"}))))

  (testing "preserves complex-type records"
    (let [entries
          [{:fhir/type :fhir.Bundle/entry
            :resource
            {:fhir/type :fhir/Observation :id "0"
             :code #fhir/CodeableConcept{}}}]]
      (given (links/resolve-entry-links entries)
        [0 :resource :code :fhir/type] := :fhir/CodeableConcept)))

  (testing "preserves references without reference"
    (let [entries
          [{:fhir/type :fhir.Bundle/entry
            :resource
            {:fhir/type :fhir/Observation :id "0"
             :subject #fhir/Reference{:display #fhir/string "foo"}}}]]
      (given (links/resolve-entry-links entries)
        [0 :resource :subject] := #fhir/Reference{:display #fhir/string "foo"}))))

(deftest resolve-entry-links-in-contained-resources-test
  (let [entries
        [{:fhir/type :fhir.Bundle/entry
          :fullUrl #fhir/uri "urn:uuid:48aacf48-ba32-4aa8-ac0d-b095ac54201b"
          :resource
          {:fhir/type :fhir/Patient :id "0"}
          :request
          {:fhir/type :fhir.Bundle.entry/request
           :method #fhir/code "POST"
           :url #fhir/uri "Patient"}}
         {:fhir/type :fhir.Bundle/entry
          :fullUrl #fhir/uri "urn:uuid:d0f40d1f-2f95-4990-a994-8182cfe71bc2"
          :resource
          {:fhir/type :fhir/ExplanationOfBenefit :id "0"
           :contained
           [{:fhir/type :fhir/ServiceRequest :id "0"
             :subject
             #fhir/Reference
              {:reference #fhir/string "urn:uuid:48aacf48-ba32-4aa8-ac0d-b095ac54201b"}}]}
          :request
          {:fhir/type :fhir.Bundle.entry/request
           :method #fhir/code "POST"
           :url #fhir/uri "ExplanationOfBenefit"}}]]
    (given (links/resolve-entry-links entries)
      [1 :resource :contained 0 :subject :reference] := #fhir/string "Patient/0")))
