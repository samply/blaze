(ns blaze.elm.type-infer-test
  (:require
    [blaze.elm.type-infer :refer [infer-types]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer [deftest testing]]
    [juxt.iota :refer [given]]))


(st/instrument)


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
  (testing "Pointing from Unspecified Context into Patient Context"
    (given-infer-types-with-context
      {:library
       {:statements
        {:def
         [{:name "MalePatient"
           :context "Patient"
           :expression {}}]}}
       :eval-context "Unspecified"}
      {:type "ExpressionRef" :name "MalePatient"}
      :life/eval-context := "Patient"))

  (testing "Pointing from Unspecified Context into Unspecified Context"
    (given-infer-types-with-context
      {:library
       {:statements
        {:def
         [{:name "MalePatients"
           :context "Unspecified"
           :expression {}}]}}
       :eval-context "Unspecified"}
      {:type "ExpressionRef" :name "MalePatients"}
      :life/eval-context := "Unspecified")))
