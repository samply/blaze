(ns blaze.cql-translator-test
  (:require
    [blaze.cql-translator :refer [translate]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer [deftest is testing]]
    [juxt.iota :refer [given]]))


(st/instrument)


(defmacro given-translation [cql & body]
  `(given (-> (translate ~cql) :statements :def) ~@body))


(deftest translate-test
  (testing "Simple Retrieve"
    (given-translation
      "library Test
        using FHIR version '3.0.0'
        define Patients: [Patient]"
      [0 :expression :type] := "Retrieve"
      [0 :expression :dataType] := "{http://hl7.org/fhir}Patient"))

  (testing "Retrieve with Code"
    (given-translation
      "library Test
        using FHIR version '3.0.0'
        codesystem test: 'test'
        code T0: '0' from test
        define Observations: [Observation: T0]"
      [0 :expression :type] := "Retrieve"
      [0 :expression :dataType] := "{http://hl7.org/fhir}Observation"
      [0 :expression :codes :type] := "ToList"
      [0 :expression :codes :operand :type] := "CodeRef"
      [0 :expression :codes :operand :name] := "T0"))

  (testing "Returns a valid :elm/library"
    (is
      (s/valid?
        :elm/library
        (translate
          "library Test
           using FHIR version '4.0.0'
           define Patients: [Patient]")))))


(comment
  (translate
    "library Retrieve\nusing FHIR version '4.0.0'\ninclude FHIRHelpers version '4.0.0'\n\ncodesystem loinc: 'http://loinc.org'\n\ncontext Patient\n\ndefine InInitialPopulation:\n    true\n\ndefine Bmi:\n  First(from [Observation: Code '39156-5' from loinc] O\n    return (O.value as Quantity).value)")
  )
