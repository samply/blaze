(ns blaze.fhir.spec-test-perf
  (:require
   [blaze.fhir.spec :as fhir-spec]
   [blaze.test-util]
   [clojure.alpha.spec :as s2]
   [criterium.core :as criterium]))

(defn- bench-unform-json [x]
  (apply format "%.3f µs <> %.3f µs" (map #(* % 1e6) (second (:mean (criterium/benchmark (fhir-spec/unform-json x) {}))))))

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
  (criterium/quick-bench (fhir-spec/unform-json #fhir/HumanName{:family "Doe" :given ["John"]}))
  (criterium/quick-bench (fhir-spec/unform-json #fhir/CodeableConcept{:coding [#fhir/Coding{:system #fhir/uri"http://loinc.org" :code #fhir/code"17861-6"}]}))

  (criterium/bench (s2/conform :fhir.json/Coding {:system "http://loinc.org" :code "39156-5"}))
  (criterium/bench (s2/conform :fhir.cbor/Coding {:system "http://loinc.org" :code "39156-5"}))

  (dotimes [_ 50000000]
    (s2/conform :fhir.json/Coding {:system "http://loinc.org" :code "39156-5"}))

  (dotimes [_ 50000000]
    (s2/conform :fhir.cbor/Coding {:system "http://loinc.org" :code "39156-5"}))

  (fhir-spec/conform-json (fhir-spec/parse-json
                           "{\n  \"category\": [\n    {\n      \"coding\": [\n        {\n          \"system\": \"http://terminology.hl7.org/CodeSystem/observation-category\",\n          \"code\": \"vital-signs\",\n          \"display\": \"vital-signs\"\n        }\n      ]\n    }\n  ],\n  \"meta\": {\n    \"versionId\": \"481283\",\n    \"lastUpdated\": \"2022-04-20T11:58:38.070Z\",\n    \"profile\": [\n      \"http://hl7.org/fhir/StructureDefinition/bmi\",\n      \"http://hl7.org/fhir/StructureDefinition/vitalsigns\"\n    ]\n  },\n  \"valueQuantity\": {\n    \"value\": 14.97,\n    \"unit\": \"kg/m2\",\n    \"system\": \"http://unitsofmeasure.org\",\n    \"code\": \"kg/m2\"\n  },\n  \"resourceType\": \"Observation\",\n  \"effectiveDateTime\": \"2013-01-04T23:45:50Z\",\n  \"status\": \"final\",\n  \"id\": \"DACG22233TWT7CK4\",\n  \"code\": {\n    \"coding\": [\n      {\n        \"system\": \"http://loinc.org\",\n        \"code\": \"39156-5\",\n        \"display\": \"Body Mass Index\"\n      }\n    ],\n    \"text\": \"Body Mass Index\"\n  },\n  \"issued\": \"2013-01-04T23:45:50.072Z\",\n  \"subject\": {\n    \"reference\": \"Patient/DACG22233TWT7CKL\"\n  }\n}")))
