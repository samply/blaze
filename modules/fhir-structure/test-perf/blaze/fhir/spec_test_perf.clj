(ns blaze.fhir.spec-test-perf
  (:require
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.structure-definition-repo]
   [blaze.test-util]
   [criterium.core :as criterium]
   [integrant.core :as ig]))

(def ^:private parsing-context
  (:blaze.fhir/parsing-context
   (ig/init
    {:blaze.fhir/parsing-context
     {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}
     :blaze.fhir/structure-definition-repo {}})))

(def ^:private writing-context
  (:blaze.fhir/writing-context
   (ig/init
    {:blaze.fhir/writing-context
     {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}
     :blaze.fhir/structure-definition-repo {}})))

(defn- bench-unform-json [x]
  (apply format "%.3f µs <> %.3f µs" (map #(* % 1e6) (second (:mean (criterium/benchmark (fhir-spec/write-json-as-bytes writing-context x) {}))))))

(comment
  ;; 0,333 µs <> 0,334 µs
  (bench-unform-json #fhir/HumanName{:family "Doe" :given ["John"]})

  ;; 0,330 µs <> 0,332 µs
  (bench-unform-json
   #fhir/CodeableConcept
    {:coding
     [#fhir/Coding
       {:system #fhir/uri"http://loinc.org"
        :code #fhir/code"17861-6"}]})

  ;; 3,264 µs <> 3,269 µs
  (bench-unform-json
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

  ;; 4,169 µs <> 4,178 µs
  (bench-unform-json
   {:fhir/type :fhir.Bundle/entry
    :fullUrl "http://localhost:8080/fhir/Observation/DACG22233TWT7CK4"
    :resource
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
              :code #fhir/code"kg/m2"}}
    :search #fhir/BundleEntrySearch{:mode #fhir/code"match"}}))

(comment
  (criterium/quick-bench (fhir-spec/write-json-as-bytes writing-context #fhir/HumanName{:family "Doe" :given ["John"]}))
  (criterium/quick-bench (fhir-spec/write-json-as-bytes writing-context #fhir/CodeableConcept{:coding [#fhir/Coding{:system #fhir/uri"http://loinc.org" :code #fhir/code"17861-6"}]})))

(def filename
  "/Users/akiel/coding/blaze/.github/validation/kds-testdata-2024.0.1/resources/Observation-mii-exa-test-data-patient-1-muv-arterieller-blutdruck.json")

(comment
  (let [s (slurp filename)]
    (criterium/bench
     (fhir-spec/parse-json parsing-context "Observation" s))))

(comment
  (let [s (slurp filename)]
    (dotimes [_ 1000000]
      (fhir-spec/parse-json parsing-context "Observation" s))))
