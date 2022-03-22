(ns blaze.fhir.spec-test-perf
  (:require
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]
    [blaze.fhir.spec.type.protocols :as p]
    [blaze.fhir.structure-definition-repo]
    [blaze.test-util :as tu]
    [clojure.test :refer [are deftest testing]]
    [criterium.core :as criterium])
  (:import
    [blaze.fhir.spec.type Id]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(tu/init-fhir-specs)


(deftest unform-json-test
  (are [x mean] (< (:mean (criterium/quick-benchmark (fhir-spec/unform-json x) {}) mean))
    #fhir/HumanName {:family "Doe" :given ["John"]} 0.3
    #fhir/CodeableConcept
        {:coding
         [#fhir/Coding
             {:system #fhir/uri "http://loinc.org"
              :code #fhir/code "17861-6"}]} 0.4))


(defn p-inc [x]
  (inc (long (p/-mem-size x))))

(defn i-inc [x]
  (inc (.memSize ^Id x)))


(comment
  (criterium/quick-bench (fhir-spec/unform-json #fhir/HumanName {:family "Doe" :given ["John"]}))
  (criterium/quick-bench (fhir-spec/unform-json #fhir/CodeableConcept {:coding [#fhir/Coding {:system #fhir/uri "http://loinc.org" :code #fhir/code "17861-6"}]}))

  (criterium/quick-bench (type/mem-size 1M))
  (let [id (type/->Id "foo")]
    (criterium/quick-bench (p/-mem-size id)))
  (let [id (Id. "foo")]
    (criterium/bench (p-inc id)))
  (let [id (Id. "foo")]
    (criterium/bench (i-inc id)))

  )
