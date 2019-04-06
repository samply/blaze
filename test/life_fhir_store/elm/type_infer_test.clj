(ns life-fhir-store.elm.type-infer-test
  (:require
    [clojure.test :refer :all]
    [juxt.iota :refer [given]]
    [life-fhir-store.elm.type-infer
     :refer
     [elm-type-specifier
      infer-types
      list-type-specifier
      named-type-specifier
      named-list-type-specifier]]))


(defmacro ^:private given-infer-types [elm & body]
  `(given (infer-types {} ~elm)
     ~@body))


(defmacro ^:private given-infer-types-with-context [context elm & body]
  `(given (infer-types ~context ~elm)
     ~@body))



;; 2. Structured Values

;; 2.3. Property
(deftest infer-types-property-test
  (testing "No source type without context"
    (given-infer-types
      {:path "value"
       :scope "P"
       :type "Property"}
      :life/source-type := nil))

  (testing "Observation.subject"
    (given-infer-types-with-context
      {:life/scope-types {"O" "{http://hl7.org/fhir}Observation"}}
      {:path "subject"
       :scope "O"
       :type "Property"}
      :life/source-type := "{http://hl7.org/fhir}Observation"))

  (testing "Patient.birthDate"
    (given-infer-types-with-context
      {:life/scope-types {"P" "{http://hl7.org/fhir}Patient"}}
      {:path "birthDate"
       :scope "P"
       :type "Property"}
      :life/source-type := "{http://hl7.org/fhir}Patient")))


;; 9. Reusing Logic

;; 9.2. ExpressionRef
(deftest infer-types-expression-ref-test
  (testing "Pointing from Population Context into Patient Context"
    (given-infer-types-with-context
      {:library
       {:statements
        {:def
         [{:name "MalePatient"
           :context "Patient"
           :expression {}}]}}
       :eval-context "Population"}
      {:type "ExpressionRef" :name "MalePatient"}
      :life/eval-context := "Patient"))

  (testing "Pointing from Population Context into Population Context"
    (given-infer-types-with-context
      {:library
       {:statements
        {:def
         [{:name "MalePatients"
           :context "Population"
           :expression {}}]}}
       :eval-context "Population"}
      {:type "ExpressionRef" :name "MalePatients"}
      :life/eval-context := "Population")))
