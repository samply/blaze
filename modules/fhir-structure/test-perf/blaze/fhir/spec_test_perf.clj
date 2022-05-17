(ns blaze.fhir.spec-test-perf
  (:require
    [blaze.fhir.spec :as fhir-spec]
    [blaze.test-util :as tu]
    [clojure.test :refer [are deftest]]
    [criterium.core :as criterium]))


(tu/init-fhir-specs)


(deftest unform-json-test
  (are [x mean] (< (:mean (criterium/quick-benchmark (fhir-spec/unform-json x) {}) mean))
    #fhir/HumanName{:family "Doe" :given ["John"]} 0.3
    #fhir/CodeableConcept
            {:coding
             [#fhir/Coding
                     {:system #fhir/uri"http://loinc.org"
                      :code #fhir/code"17861-6"}]} 0.4))


(comment
  (criterium/quick-bench (fhir-spec/unform-json #fhir/HumanName{:family "Doe" :given ["John"]}))
  (criterium/quick-bench (fhir-spec/unform-json #fhir/CodeableConcept{:coding [#fhir/Coding{:system #fhir/uri"http://loinc.org" :code #fhir/code"17861-6"}]}))
  )
