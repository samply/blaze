(ns blaze.fhir.hash-test-perf
  (:require
   [blaze.fhir.hash :as hash]
   [blaze.test-util]
   [criterium.core :as criterium]))

(def observation
  {:fhir/type :fhir/Observation :id "DACG22233TWT7CK4"
   :meta #fhir/Meta
          {:versionId #fhir/id "481283"
           :lastUpdated #fhir/instant #system/date-time "2022-04-20T11:58:38.070Z"
           :profile [#fhir/canonical "http://hl7.org/fhir/StructureDefinition/bmi"
                     #fhir/canonical "http://hl7.org/fhir/StructureDefinition/vitalsigns"]}
   :status #fhir/code "final"
   :category
   [#fhir/CodeableConcept
     {:coding
      [#fhir/Coding
        {:system #fhir/uri-interned "http://terminology.hl7.org/CodeSystem/observation-category"
         :code #fhir/code "vital-signs"
         :display #fhir/string-interned "vital-signs"}]}]
   :code #fhir/CodeableConcept
          {:coding [#fhir/Coding{:system #fhir/uri-interned "http://loinc.org"
                                 :code #fhir/code "39156-5"
                                 :display #fhir/string-interned "Body Mass Index"}]
           :text "Body Mass Index"}
   :subject #fhir/Reference{:reference #fhir/string "Patient/DACG22233TWT7CKL"}
   :effective #fhir/dateTime #system/date-time "2013-01-04T23:45:50Z"
   :issued #fhir/instant #system/date-time "2013-01-04T23:45:50.072Z"
   :value #fhir/Quantity
           {:value 14.97M
            :unit "kg/m2"
            :system #fhir/uri-interned "http://unitsofmeasure.org"
            :code #fhir/code "kg/m2"}})

(comment
  (criterium/bench (hash/generate observation)))
