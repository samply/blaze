(ns blaze.fhir.hash-test-perf
  (:require
   [blaze.fhir.hash :as hash]
   [blaze.test-util]
   [criterium.core :as criterium]))

(def observation
  {:fhir/type :fhir/Observation :id "DACG22233TWT7CK4"
   :meta #fhir/Meta
          {:versionId #fhir/id"481283"
           :lastUpdated #fhir/instant"2022-04-20T11:58:38.070Z"
           :profile [#fhir/canonical"http://hl7.org/fhir/StructureDefinition/bmi"
                     #fhir/canonical"http://hl7.org/fhir/StructureDefinition/vitalsigns"]}
   :status #fhir/code"final"
   :category
   [#fhir/CodeableConcept
     {:coding
      [#fhir/Coding
        {:system #fhir/uri"http://terminology.hl7.org/CodeSystem/observation-category"
         :code #fhir/code"vital-signs"
         :display #fhir/string"vital-signs"}]}]
   :code #fhir/CodeableConcept
          {:coding [#fhir/Coding{:system #fhir/uri"http://loinc.org"
                                 :code #fhir/code"39156-5"
                                 :display #fhir/string"Body Mass Index"}]
           :text "Body Mass Index"}
   :subject #fhir/Reference{:reference "Patient/DACG22233TWT7CKL"}
   :effective #fhir/dateTime"2013-01-04T23:45:50Z"
   :issued #fhir/instant"2013-01-04T23:45:50.072Z"
   :value #fhir/Quantity
           {:value 14.97M
            :unit "kg/m2"
            :system #fhir/uri"http://unitsofmeasure.org"
            :code #fhir/code"kg/m2"}})

(comment
  (criterium/bench (hash/generate observation)))
