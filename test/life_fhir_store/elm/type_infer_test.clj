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


(def ^:private null
  {:type "Null"})


;; 2. Structured Values

;; 2.3. Property
(deftest infer-types-property-test
  (testing "No source type without context"
    (given-infer-types
      {:path "value"
       :scope "P"
       :type "Property"}
      :life/source-type := nil))

  (given-infer-types-with-context
    {:life/scope-types {"O" "{http://hl7.org/fhir}Observation"}}
    {:path "subject"
     :scope "O"
     :type "Property"}
    :life/source-type := "{http://hl7.org/fhir}Observation"
    :life/return-type := (named-type-specifier "{http://hl7.org/fhir}Patient")))


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
           :expression
           {:life/return-type (named-type-specifier "{http://hl7.org/fhir}Patient")}}]}}
       :eval-context "Population"}
      {:type "ExpressionRef" :name "MalePatient"}
      :life/return-type := (named-list-type-specifier "{http://hl7.org/fhir}Patient")
      :life/eval-context := "Patient"))

  (testing "Pointing from Population Context into Population Context"
    (given-infer-types-with-context
      {:library
       {:statements
        {:def
         [{:name "MalePatients"
           :context "Population"
           :expression
           {:life/return-type (named-list-type-specifier "{http://hl7.org/fhir}Patient")}}]}}
       :eval-context "Population"}
      {:type "ExpressionRef" :name "MalePatients"}
      :life/return-type := (named-list-type-specifier "{http://hl7.org/fhir}Patient")
      :life/eval-context := "Population")))

;; 10. Queries

;; 10.1. Query
(deftest infer-types-query-test
  (testing "Query without return"
    (given-infer-types
      {:type "Query"
       :source
       [{:alias "O"
         :expression
         {:dataType "{http://hl7.org/fhir}Patient" :type "Retrieve"}}]}
      :life/return-type := (named-list-type-specifier "{http://hl7.org/fhir}Patient"))))


;; 11. External Data

;; 11.1. Retrieve
(deftest infer-types-retrieve-test
  (given-infer-types
    {:type "Retrieve"
     :dataType "{http://hl7.org/fhir}Patient"}
    :life/return-type := (named-list-type-specifier "{http://hl7.org/fhir}Patient"))

  (given-infer-types
    {:type "Retrieve"
     :dataType "{http://hl7.org/fhir}Observation"}
    :life/return-type := (named-list-type-specifier "{http://hl7.org/fhir}Observation")))


;; 12. Comparison Operators
(deftest infer-types-comparison-operator-test
  (given-infer-types
    {:type "Equal"
     :operand [#elm/integer 1 #elm/integer 1]}
    :life/return-type := (elm-type-specifier "Boolean"))

  (given-infer-types
    {:type "Equivalent"
     :operand [#elm/integer 1 #elm/integer 1]}
    :life/return-type := (elm-type-specifier "Boolean"))

  (given-infer-types
    {:type "Greater"
     :operand [#elm/integer 1 #elm/integer 1]}
    :life/return-type := (elm-type-specifier "Boolean"))

  (given-infer-types
    {:type "GreaterOrEqual"
     :operand [#elm/integer 1 #elm/integer 1]}
    :life/return-type := (elm-type-specifier "Boolean"))

  (given-infer-types
    {:type "Less"
     :operand [#elm/integer 1 #elm/integer 1]}
    :life/return-type := (elm-type-specifier "Boolean"))

  (given-infer-types
    {:type "LessOrEqual"
     :operand [#elm/integer 1 #elm/integer 1]}
    :life/return-type := (elm-type-specifier "Boolean"))

  (given-infer-types
    {:type "NotEqual"
     :operand [#elm/integer 1 #elm/integer 1]}
    :life/return-type := (elm-type-specifier "Boolean")))


;; 13. Logical Operators
(deftest infer-types-logical-operator-test
  (given-infer-types
    {:type "And"
     :operand [null null]}
    :life/return-type := (elm-type-specifier "Boolean"))

  (given-infer-types
    {:type "Implies"
     :operand [null null]}
    :life/return-type := (elm-type-specifier "Boolean"))

  (given-infer-types
    {:type "Not"
     :operand null}
    :life/return-type := (elm-type-specifier "Boolean"))

  (given-infer-types
    {:type "Or"
     :operand [null null]}
    :life/return-type := (elm-type-specifier "Boolean"))

  (given-infer-types
    {:type "Xor"
     :operand [null null]}
    :life/return-type := (elm-type-specifier "Boolean")))


;; 21. Aggregate Operators

;; 21.4. Count
(deftest infer-types-count-test
  (given-infer-types
    {:type "Count"
     :source {:type "List" :element [#elm/integer 1]}}
    :life/return-type := (elm-type-specifier "Integer")))
