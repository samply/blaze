(ns blaze.fhir.spec-test-perf
  (:require
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.writing-context]
   [blaze.test-util]
   [criterium.core :as criterium]
   [integrant.core :as ig]))

(def ^:private parsing-context
  (ig/init-key
   :blaze.fhir/parsing-context
   {:structure-definition-repo structure-definition-repo}))

(def ^:private writing-context
  (ig/init-key
   :blaze.fhir/writing-context
   {:structure-definition-repo structure-definition-repo}))

(def kds-bundle-filename
  "../../.github/test-data/kds-testdata-2024.0.1/resources/Bundle-mii-exa-test-data-bundle.json")

(defn- bench-write-json [x]
  (apply format "%.3f µs <> %.3f µs" (map #(* % 1e6) (second (:mean (criterium/benchmark (fhir-spec/write-json-as-bytes writing-context x) {}))))))

(defn- read-json [type x]
  (fhir-spec/parse-json parsing-context type x))

(defn- bench-read-json [type x]
  (apply format "%.3f µs <> %.3f µs" (map #(* % 1e6) (second (:mean (criterium/benchmark (read-json type x) {}))))))

(comment
  ;; 0,168 µs <> 0,169 µs
  (bench-write-json #fhir/HumanName{:family "Doe" :given ["John"]})

  ;; 0,145 µs <> 0,146 µs
  (bench-write-json
   #fhir/CodeableConcept
    {:coding
     [#fhir/Coding
       {:system #fhir/uri "http://loinc.org"
        :code #fhir/code "17861-6"}]})

  ;; 2,380 µs <> 2,393 µs
  (bench-write-json
   {:fhir/type :fhir/Observation :id "DACG22233TWT7CK4"
    :meta #fhir/Meta
           {:versionId #fhir/id "481283"
            :lastUpdated #fhir/instant "2022-04-20T11:58:38.070Z"
            :profile [#fhir/canonical "http://hl7.org/fhir/StructureDefinition/bmi"
                      #fhir/canonical "http://hl7.org/fhir/StructureDefinition/vitalsigns"]}
    :status #fhir/code "final"
    :category
    [#fhir/CodeableConcept
      {:coding
       [#fhir/Coding
         {:system #fhir/uri "http://terminology.hl7.org/CodeSystem/observation-category"
          :code #fhir/code "vital-signs"
          :display #fhir/string "vital-signs"}]}]
    :code #fhir/CodeableConcept
           {:coding [#fhir/Coding{:system #fhir/uri "http://loinc.org"
                                  :code #fhir/code "39156-5"
                                  :display #fhir/string "Body Mass Index"}]
            :text "Body Mass Index"}
    :subject #fhir/Reference{:reference #fhir/string "Patient/DACG22233TWT7CKL"}
    :effective #fhir/dateTime "2013-01-04T23:45:50Z"
    :issued #fhir/instant "2013-01-04T23:45:50.072Z"
    :value #fhir/Quantity
            {:value 14.97M
             :unit "kg/m2"
             :system #fhir/uri "http://unitsofmeasure.org"
             :code #fhir/code "kg/m2"}})

  ;; 2,770 µs <> 2,773 µs
  (bench-write-json
   {:fhir/type :fhir.Bundle/entry
    :fullUrl "http://localhost:8080/fhir/Observation/DACG22233TWT7CK4"
    :resource
    {:fhir/type :fhir/Observation :id "DACG22233TWT7CK4"
     :meta #fhir/Meta
            {:versionId #fhir/id "481283"
             :lastUpdated #fhir/instant "2022-04-20T11:58:38.070Z"
             :profile [#fhir/canonical "http://hl7.org/fhir/StructureDefinition/bmi"
                       #fhir/canonical "http://hl7.org/fhir/StructureDefinition/vitalsigns"]}
     :status #fhir/code "final"
     :category
     [#fhir/CodeableConcept
       {:coding
        [#fhir/Coding
          {:system #fhir/uri "http://terminology.hl7.org/CodeSystem/observation-category"
           :code #fhir/code "vital-signs"
           :display #fhir/string "vital-signs"}]}]
     :code #fhir/CodeableConcept
            {:coding [#fhir/Coding{:system #fhir/uri "http://loinc.org"
                                   :code #fhir/code "39156-5"
                                   :display #fhir/string "Body Mass Index"}]
             :text "Body Mass Index"}
     :subject #fhir/Reference{:reference #fhir/string "Patient/DACG22233TWT7CKL"}
     :effective #fhir/dateTime "2013-01-04T23:45:50Z"
     :issued #fhir/instant "2013-01-04T23:45:50.072Z"
     :value #fhir/Quantity
             {:value 14.97M
              :unit "kg/m2"
              :system #fhir/uri "http://unitsofmeasure.org"
              :code #fhir/code "kg/m2"}}
    :search #fhir/BundleEntrySearch{:mode #fhir/code "match"}})

  ;; 581,264 µs <> 583,088 µs
  (bench-write-json (read-json "Bundle" (slurp kds-bundle-filename)))

  ;; Read Performance

  ;; 3461,458 µs <> 3470,343 µs
  (bench-read-json "Bundle" (slurp kds-bundle-filename)))
