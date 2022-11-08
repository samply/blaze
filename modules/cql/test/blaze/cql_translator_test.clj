(ns blaze.cql-translator-test
  (:require
    [blaze.cql-translator :refer [translate]]
    [blaze.cql-translator-spec]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


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
    (are [cql] (s/valid? :elm/library (translate cql))
      "library Test
       using FHIR version '4.0.0'
       define Patients: [Patient]"
      "library Test
       using FHIR version '4.0.0'
       context Specimen
       define Specimens: [Specimen]"))

  (testing "With Parameters"
    (given
      (translate
        "library Test
         parameter MeasurementPeriod Interval<DateTime>")
      [:parameters :def 0 :name] := "MeasurementPeriod"
      [:parameters :def 0 :resultTypeSpecifier :type] := "IntervalTypeSpecifier"
      [:parameters :def 0 :resultTypeSpecifier :pointType :type] := "NamedTypeSpecifier"
      [:parameters :def 0 :resultTypeSpecifier :pointType :name] := "{urn:hl7-org:elm-types:r1}DateTime"))

  (testing "Syntax Error"
    (given (translate
             "library Test
              define Error: (")
      ::anom/category := ::anom/incorrect
      ::anom/message := "Syntax error at <EOF>")))
