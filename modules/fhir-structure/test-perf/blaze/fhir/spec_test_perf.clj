(ns blaze.fhir.spec-test-perf
  (:require
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.test-util]
   [criterium.core :as criterium]
   [integrant.core :as ig])
  (:import
   [java.io OutputStream]))

(def ^:private parsing-context
  (ig/init-key
   :blaze.fhir/parsing-context
   {:structure-definition-repo structure-definition-repo}))

(def kds-bundle-filename
  "../../.github/test-data/kds-testdata-2024.0.1/resources/Bundle-mii-exa-test-data-bundle.json")

(defn- bench-write-json
  "Benchmarks writing `x` into an output stream, like the output middleware
  does.

  Unlike `bench-write-json`, this doesn't include growing and copying the byte
  array the value is written into, which is a considerable part of the time for
  large values like whole bundles."
  [x]
  (apply format "%.3f µs <> %.3f µs" (map #(* % 1e6) (second (:mean (criterium/benchmark (fhir-spec/write-json (OutputStream/nullOutputStream) x) {}))))))

(defn- profile-write-json [n x]
  (dotimes [_ n]
    (fhir-spec/write-json (OutputStream/nullOutputStream) x)))

(defn- read-json [type x]
  (fhir-spec/parse-json parsing-context type x))

(defn- bench-read-json [type x]
  (apply format "%.3f µs <> %.3f µs" (map #(* % 1e6) (second (:mean (criterium/benchmark (read-json type x) {}))))))

(comment
  ;; 0,098 µs <> 0,100 µs
  (bench-write-json #fhir/HumanName{:family #fhir/string "Doe" :given [#fhir/string "John"]})

  ;; 0,129 µs <> 0,130 µs
  (bench-write-json
   #fhir/CodeableConcept
    {:coding
     [#fhir/Coding
       {:system #fhir/uri-interned "http://loinc.org"
        :code #fhir/code "17861-6"}]})

  ;; 1,185 µs <> 1,191 µs
  (bench-write-json
   #fhir/map{:fhir/type :fhir/Observation :id "DACG22233TWT7CK4"
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
                     :text #fhir/string "Body Mass Index"}
             :subject #fhir/Reference{:reference #fhir/string "Patient/DACG22233TWT7CKL"}
             :effective #fhir/dateTime #system/date-time "2013-01-04T23:45:50Z"
             :issued #fhir/instant #system/date-time "2013-01-04T23:45:50.072Z"
             :value #fhir/Quantity
                     {:value #fhir/decimal 14.97M
                      :unit #fhir/string "kg/m2"
                      :system #fhir/uri-interned "http://unitsofmeasure.org"
                      :code #fhir/code "kg/m2"}})

  ;; 1,452 µs <> 1,457 µs
  (bench-write-json
   #fhir/map{:fhir/type :fhir.Bundle/entry
             :fullUrl #fhir/uri "http://localhost:8080/fhir/Observation/DACG22233TWT7CK4"
             :resource
             #fhir/map{:fhir/type :fhir/Observation :id "DACG22233TWT7CK4"
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
                               :text #fhir/string "Body Mass Index"}
                       :subject #fhir/Reference{:reference #fhir/string "Patient/DACG22233TWT7CKL"}
                       :effective #fhir/dateTime #system/date-time "2013-01-04T23:45:50Z"
                       :issued #fhir/instant #system/date-time "2013-01-04T23:45:50.072Z"
                       :value #fhir/Quantity
                               {:value #fhir/decimal 14.97M
                                :unit #fhir/string "kg/m2"
                                :system #fhir/uri-interned "http://unitsofmeasure.org"
                                :code #fhir/code "kg/m2"}}
             :search #fhir.Bundle.entry/search{:mode #fhir/code "match"}})

  ;; 233,359 µs <> 234,750 µs
  (bench-write-json (read-json "Bundle" (slurp kds-bundle-filename)))
  (profile-write-json 100000 (read-json "Bundle" (slurp kds-bundle-filename)))

  ;; Read Performance

  ;; 3071,643 µs <> 3078,915 µs
  (bench-read-json "Bundle" (slurp kds-bundle-filename)))
