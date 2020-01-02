(ns blaze.fhir-path-test
  (:require
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir-path-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)

(def resolver
  (reify
    fhir-path/Resolver
    (-resolve [_ _])))


;; See: http://hl7.org/fhirpath/index.html#is-type-specifier
(deftest is-type-specifier
  (testing "single item with matching type returns true"
    (is
      (true?
        @(first
           (fhir-path/eval
             resolver
             (fhir-path/compile "Patient.birthDate is date")
             {:resourceType "Patient"
              :id "id-162953"
              :birthDate "2020"})))))

  (testing "single item with non-matching type returns false"
    (is
      (false?
        @(first
           (fhir-path/eval
             resolver
             (fhir-path/compile "Patient.birthDate is string")
             {:resourceType "Patient"
              :id "id-162953"
              :birthDate "2020"})))))

  (testing "multiple item returns an error"
    (given
      (fhir-path/eval
        resolver
        (fhir-path/compile "Patient.identifier is string")
        {:resourceType "Patient"
         :id "id-162953"
         :identifier
         [{:value "value-163922"}
          {:value "value-163928"}]})
      ::anom/category := ::anom/incorrect
      ::anom/message := "is type specifier with more than one item at the left side `[{:value \"value-163922\"} {:value \"value-163928\"}]`")))


;; See: http://hl7.org/fhirpath/index.html#wherecriteria-expression-collection
(deftest where-function
  (testing "returns matching item"
    (given
      (fhir-path/eval
        resolver
        (fhir-path/compile "Patient.telecom.where(use = 'home')")
        {:resourceType "Patient"
         :id "id-162953"
         :telecom
         [{:use "home"
           :value "value-170758"}]})
      [0 :use] := "home"
      [0 :value] := "value-170758"))

  (testing "returns empty collection on non-matching item"
    (is
      (empty?
        (fhir-path/eval
          resolver
          (fhir-path/compile "Patient.telecom.where(use = 'work')")
          {:resourceType "Patient"
           :id "id-162953"
           :telecom
           [{:use "home"
             :value "value-170758"}]}))))

  (testing "returns empty collection on empty input"
    (is
      (empty?
        (fhir-path/eval
          resolver
          (fhir-path/compile "Patient.telecom.where(use = 'home')")
          {:resourceType "Patient"
           :id "id-162953"})))))


;; See: https://hl7.org/fhir/fhirpath.html#functions
(deftest resolve-function
  (let [resolver
        (reify
          fhir-path/Resolver
          (-resolve [_ uri]
            (when (= "reference-180039" uri)
              {:resourceType "Patient"
               :id "id-164737"})))]
    (given
      (fhir-path/eval
        resolver
        (fhir-path/compile "Specimen.subject.where(resolve() is Patient)")
        {:resourceType "Specimen"
         :id "id-175250"
         :subject {:reference "reference-180039"}})
      [0 :reference] := "reference-180039")))


;; See: http://hl7.org/fhirpath/index.html#existscriteria-expression-boolean
(deftest exists-function
  (is
    (false?
      @(first
         (fhir-path/eval
           resolver
           (fhir-path/compile "Patient.deceased.exists()")
           {:resourceType "Patient"
            :id "id-182007"}))))

  (is
    (true?
      @(first
         (fhir-path/eval
           resolver
           (fhir-path/compile "Patient.deceased.exists()")
           {:resourceType "Patient"
            :id "id-182007"
            :deceasedBoolean true})))))
