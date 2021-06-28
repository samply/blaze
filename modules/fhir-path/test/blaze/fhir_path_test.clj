(ns blaze.fhir-path-test
  "See: http://hl7.org/fhirpath/index.html"
  (:require
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir-path-spec]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type]
    [blaze.fhir.spec.type.system :as system]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:refer-clojure :exclude [eval]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def ^:private resolver
  (reify
    fhir-path/Resolver
    (-resolve [_ _])))


(defn- eval
  ([expr resource]
   (eval expr resolver resource))
  ([expr resolver resource]
   (fhir-path/eval resolver (fhir-path/compile expr) resource)))



;; 3. Path selection
(deftest path-selection-test
  (testing "Resource.id"
    (are [x]
      (= x (first (eval "Resource.id"
                        {:fhir/type :fhir/Patient
                         :id x})))
      "id-161533"
      "id-161537"))

  (testing "Patient.id"
    (are [x]
      (= x (first (eval "Patient.id"
                        {:fhir/type :fhir/Patient
                         :id x})))
      "id-161533"
      "id-161537"))

  (testing "Patient.active"
    (testing "value"
      (are [x]
        (= x (first (eval "Patient.active"
                          {:fhir/type :fhir/Patient
                           :id "foo"
                           :active x})))
        true
        false))
    (testing "type"
      (are [x]
        (= :fhir/boolean
           (fhir-spec/fhir-type (first (eval "Patient.active"
                                             {:fhir/type :fhir/Patient
                                              :id "foo"
                                              :active x}))))
        true
        false)))

  (testing "(Observation.value as boolean)"
    (are [x]
      (= x (first (eval "(Observation.value as boolean)"
                        {:fhir/type :fhir/Observation
                         :id "foo"
                         :value x})))
      true
      false)))



;; 4. Expressions

;; 4.1 Literals
(deftest boolean-test
  (is (true? (first (eval "true" "foo"))))
  (is (false? (first (eval "false" "foo")))))


(deftest string-test
  (is (= "bar" (first (eval "'bar'" "foo")))))


(deftest integer-test
  (is (= 0 (first (eval "0" "foo")))))


(deftest decimal-test
  (is (= 0.1M (first (eval "0.1" "foo")))))


(deftest date-test
  (are [expr date] (= date (first (eval expr "foo")))
    "@2020" (system/date 2020)
    "@2020-01" (system/date 2020 1)
    "@2020-01-02" (system/date 2020 1 2)))


(deftest date-time-test
  (are [expr date-time] (= date-time (first (eval expr "foo")))
    "@2020T" (system/date-time 2020)
    "@2020-01T" (system/date-time 2020 1)
    "@2020-01-02T" (system/date-time 2020 1 2)
    "@2020-01-02T03" (system/date-time 2020 1 2 3)))


;; 4.5. Singleton Evaluation of Collections
(deftest singleton-test
  (testing "string concatenation"
    (testing "with no given name"
      (is (= (first (eval "Patient.name.family + ', ' + Patient.name.given"
                          {:fhir/type :fhir/Patient
                           :id "foo"
                           :name
                           [{:fhir/type :fhir/HumanName
                             :family "Doe"}]}))
             "Doe, ")))

    (testing "with one given name"
      (is (= (first (eval "Patient.name.family + ', ' + Patient.name.given"
                          {:fhir/type :fhir/Patient
                           :id "foo"
                           :name
                           [{:fhir/type :fhir/HumanName
                             :family "Doe"
                             :given ["John"]}]}))
             "Doe, John")))

    (testing "with two given names"
      (given (eval "Patient.name.family + ', ' + Patient.name.given"
                   {:fhir/type :fhir/Patient
                    :id "foo"
                    :name
                    [{:fhir/type :fhir/HumanName
                      :family "Doe"
                      :given ["John" "Foo"]}]})
        ::anom/category := ::anom/incorrect
        ::anom/message := "unable to evaluate `[\"John\" \"Foo\"]` as singleton"))

    (testing "with non-convertible type"
      (given (eval "Patient.name.family + ', ' + Patient.name"
                   {:fhir/type :fhir/Patient
                    :id "foo"
                    :name
                    [{:fhir/type :fhir/HumanName
                      :family "Doe"}]})
        ::anom/category := ::anom/incorrect
        ::anom/message := "unable to evaluate `[{:fhir/type :fhir/HumanName, :family \"Doe\"}]` as singleton")))

  (testing "and expression"
    (testing "with one telecom"
      (is (= (first (eval "Patient.active and Patient.gender and Patient.telecom"
                          {:fhir/type :fhir/Patient
                           :id "foo"
                           :active true
                           :gender #fhir/code"female"
                           :telecom
                           [{:fhir/type :fhir/ContactPoint
                             :use #fhir/code"home"
                             :value "foo"}]}))
             true)))

    (testing "with two telecoms"
      (given (eval "Patient.active and Patient.gender and Patient.telecom"
                   {:fhir/type :fhir/Patient
                    :id "foo"
                    :active true
                    :gender #fhir/code"female"
                    :telecom
                    [{:fhir/type :fhir/ContactPoint
                      :use #fhir/code"home"
                      :value "foo"}
                     {:fhir/type :fhir/ContactPoint
                      :use #fhir/code"work"
                      :value "bar"}]})
        ::anom/category := ::anom/incorrect
        ::anom/message := "unable to evaluate `[{:fhir/type :fhir/ContactPoint, :use #fhir/code\"home\", :value \"foo\"} {:fhir/type :fhir/ContactPoint, :use #fhir/code\"work\", :value \"bar\"}]` as singleton"))))



;; 5. Functions

(deftest resolve-function-test
  (let [resolver
        (reify
          fhir-path/Resolver
          (-resolve [_ uri]
            (when (= "reference-180039" uri)
              {:fhir/type :fhir/Patient
               :id "id-164737"})))]
    (given
      (eval
        "Specimen.subject.where(resolve() is Patient)"
        resolver
        {:fhir/type :fhir/Specimen :id "id-175250"
         :subject #fhir/Reference{:reference "reference-180039"}})
      [0 :reference] := "reference-180039")))


;; 5.1. Existence

;; 5.1.2. exists([criteria : expression]) : Boolean
(deftest exists-function-test
  (is
    (false?
      (first
        (eval
          "Patient.deceased.exists()"
          {:fhir/type :fhir/Patient
           :id "id-182007"}))))

  (is
    (true?
      (first
        (eval
          "Patient.deceased.exists()"
          {:fhir/type :fhir/Patient
           :id "id-182007"
           :deceased true})))))


;; 5.2. Filtering and projection

;; 5.2.1. where(criteria : expression) : collection
(deftest where-function-test
  (testing "missing criteria"
    (given
      (fhir-path/compile "Patient.telecom.where()")
      ::anom/category := ::anom/incorrect
      ::anom/message := "missing criteria in `where` function in expression `Patient.telecom.where()`"))

  (testing "returns one matching item"
    (given
      (eval
        "Patient.telecom.where(use = 'home')"
        {:fhir/type :fhir/Patient
         :id "id-162953"
         :telecom
         [{:fhir/type :fhir/ContactPoint
           :use #fhir/code"home"
           :value "value-170758"}]})
      [0 :use] := #fhir/code"home"
      [0 :value] := "value-170758"))

  (testing "returns two matching items"
    (given
      (eval
        "Patient.telecom.where(use = 'home')"
        {:fhir/type :fhir/Patient
         :id "id-162953"
         :telecom
         [{:fhir/type :fhir/ContactPoint
           :use #fhir/code"home"
           :value "value-170758"}
          {:fhir/type :fhir/ContactPoint
           :use #fhir/code"home"
           :value "value-145928"}]})
      [0 :use] := #fhir/code"home"
      [0 :value] := "value-170758"
      [1 :use] := #fhir/code"home"
      [1 :value] := "value-145928"))

  (testing "returns empty collection on non-matching item"
    (is
      (empty?
        (eval
          "Patient.telecom.where(use = 'work')"
          {:fhir/type :fhir/Patient
           :id "id-162953"
           :telecom
           [{:fhir/type :fhir/ContactPoint
             :use #fhir/code"home"
             :value "value-170758"}]}))))

  (testing "returns empty collection on empty criteria result"
    (is
      (empty?
        (eval
          "Patient.telecom.where({})"
          {:fhir/type :fhir/Patient
           :id "id-162953"
           :telecom
           [{:fhir/type :fhir/ContactPoint
             :use #fhir/code"home"
             :value "value-170758"}]}))))

  (testing "returns empty collection on empty input"
    (is
      (empty?
        (eval
          "Patient.telecom.where(use = 'home')"
          {:fhir/type :fhir/Patient
           :id "id-162953"}))))

  (testing "return error on multiple criteria result"
    (given
      (eval
        "Patient.address.where(line)"
        {:fhir/type :fhir/Patient
         :id "id-162953"
         :address
         [{:fhir/type :fhir/Address
           :line ["a" "b"]}]})
      ::anom/category := ::anom/incorrect
      ::anom/message := "multiple result items `[\"a\" \"b\"]` while evaluating where function criteria"))

  (testing "return error on non-boolean criteria result"
    (given
      (eval
        "Patient.telecom.where(use)"
        {:fhir/type :fhir/Patient
         :id "id-162953"
         :telecom
         [{:fhir/type :fhir/ContactPoint
           :use #fhir/code"home"
           :value "value-170758"}]})
      ::anom/category := ::anom/incorrect
      ::anom/message := "non-boolean result `#fhir/code\"home\"` of type `:fhir/code` while evaluating where function criteria")))


;; 5.2.4. ofType(type : type specifier) : collection
(deftest of-type-function-test
  (testing "returns two matching items"
    (given
      (eval
        "Observation.component.value.ofType(Quantity)"
        {:fhir/type :fhir/Observation
         :component
         [{:fhir/type :fhir.Observation/component
           :value #fhir/Quantity{:value 150M}}
          {:fhir/type :fhir.Observation/component
           :value #fhir/Quantity{:value 100M}}]})
      [0 :value] := 150M
      [1 :value] := 100M)))



;; 5.3. Subsetting

;; 5.3.1. [ index : Integer ] : collection
(deftest indexer-test
  (is
    (=
      (eval
        "Bundle.entry[0].resource"
        {:fhir/type :fhir/Bundle
         :id "id-110914"
         :entry
         [{:fhir/type :fhir.Bundle/entry
           :resource
           {:fhir/type :fhir/Patient
            :id "id-111004"}}]})
      [{:fhir/type :fhir/Patient
        :id "id-111004"}])))


;; 5.4. Combining

;; 5.4.1. union(other : collection)
(deftest union-test
  (let [patient
        {:fhir/type :fhir/Patient :id "foo"
         :name
         [{:fhir/type :fhir/HumanName
           :family "Doe"}
          {:fhir/type :fhir/HumanName
           :family "Bolton"}]}]
    (are [expr res] (= res (eval expr patient))
      "{} | {}" []
      "1 | {}" [1]
      "{} | 1" [1]
      "1 | 1" [1]
      "Patient.name.family | {}" ["Doe" "Bolton"]
      "Patient.name.family | 'Wade'" ["Wade" "Doe" "Bolton"]
      "{} | Patient.name.family" ["Doe" "Bolton"]
      "'Wade' | Patient.name.family" ["Wade" "Doe" "Bolton"]
      "Patient.name.family | Patient.name.family" ["Doe" "Bolton"]))

  (is
    (=
      (eval
        "Patient.gender | Patient.birthDate"
        {:fhir/type :fhir/Patient
         :id "id-162953"
         :gender #fhir/code"female"
         :birthDate #fhir/date"2020"})
      [#fhir/code"female" #fhir/date"2020"])))



;; 6. Operations

;; 6.1. Equality

;; 6.1.1. = (Equals)
(deftest equals-test
  (testing "propagates empty collections"
    (testing "both empty"
      (is
        (empty?
          (eval
            "{} = {}"
            {:fhir/type :fhir/Patient
             :id "foo"}))))

    (testing "left empty"
      (is
        (empty?
          (eval
            "{} = Patient.id"
            {:fhir/type :fhir/Patient
             :id "foo"}))))

    (testing "right empty"
      (is
        (empty?
          (eval
            "Patient.id = {}"
            {:fhir/type :fhir/Patient
             :id "foo"})))))

  (testing "string comparison"
    (is
      (true?
        (first
          (eval "Patient.id = 'foo'"
                {:fhir/type :fhir/Patient
                 :id "foo"}))))
    (is
      (false?
        (first
          (eval "Patient.id = 'bar'"
                {:fhir/type :fhir/Patient
                 :id "foo"}))))))


;; 6.1.3. != (Not Equals)
(deftest not-equals-test
  (testing "propagates empty collections"
    (testing "both empty"
      (is
        (empty?
          (eval
            "{} != {}"
            {:fhir/type :fhir/Patient
             :id "foo"}))))

    (testing "left empty"
      (is
        (empty?
          (eval
            "{} != Patient.id"
            {:fhir/type :fhir/Patient
             :id "foo"}))))

    (testing "right empty"
      (is
        (empty?
          (eval
            "Patient.id != {}"
            {:fhir/type :fhir/Patient
             :id "foo"}))))

    (testing "string comparison"
      (is
        (true?
          (first
            (eval "Patient.id != 'bar'"
                  {:fhir/type :fhir/Patient
                   :id "foo"}))))
      (is
        (false?
          (first
            (eval "Patient.id != 'foo'"
                  {:fhir/type :fhir/Patient
                   :id "foo"})))))))


;; 6.3. Types

;; 6.3.1. is type specifier
(deftest is-type-specifier-test
  (testing "single item with matching type returns true"
    (is
      (true?
        (first
          (eval
            "Patient.birthDate is date"
            {:fhir/type :fhir/Patient :id "foo"
             :birthDate #fhir/date"2020"})))))

  (testing "single item with non-matching type returns false"
    (is
      (false?
        (first
          (eval
            "Patient.birthDate is string"
            {:fhir/type :fhir/Patient :id "foo"
             :birthDate #fhir/date"2020"})))))

  (testing "empty collection returns empty collection"
    (is
      (empty?
        (first
          (eval
            "Patient.birthDate is string"
            {:fhir/type :fhir/Patient :id "foo"})))))

  (testing "multiple item returns an error"
    (given
      (eval
        "Patient.identifier is string"
        {:fhir/type :fhir/Patient :id "id-162953"
         :identifier
         [#fhir/Identifier{:value "value-163922"}
          #fhir/Identifier{:value "value-163928"}]})
      ::anom/category := ::anom/incorrect
      ::anom/message := "is type specifier with more than one item at the left side `[#fhir/Identifier{:value \"value-163922\"} #fhir/Identifier{:value \"value-163928\"}]`")))


;; 6.3.3 as type specifier
(deftest as-type-specifier-test
  (testing "single item with matching type returns the item"
    (is
      (=
        #fhir/date"2020"
        (first
          (eval
            "Patient.birthDate as date"
            {:fhir/type :fhir/Patient :id "foo"
             :birthDate #fhir/date"2020"})))))

  (testing "single item with non-matching type returns an empty collection"
    (is
      (empty?
        (first
          (eval
            "Patient.birthDate as string"
            {:fhir/type :fhir/Patient :id "foo"
             :birthDate #fhir/date"2020"})))))

  (testing "empty collection returns empty collection"
    (is
      (empty?
        (first
          (eval
            "Patient.birthDate as string"
            {:fhir/type :fhir/Patient :id "foo"})))))

  (testing "multiple item returns an error"
    (given
      (eval
        "Patient.identifier as string"
        {:fhir/type :fhir/Patient :id "id-162953"
         :identifier
         [#fhir/Identifier{:value "value-163922"}
          #fhir/Identifier{:value "value-163928"}]})
      ::anom/category := ::anom/incorrect
      ::anom/message := "as type specifier with more than one item at the left side `[#fhir/Identifier{:value \"value-163922\"} #fhir/Identifier{:value \"value-163928\"}]`")))


;; 6.3.4 as(type : type specifier)
(deftest as-function-test
  (testing "single item with matching type returns the item"
    (is
      (=
        #fhir/date"2020"
        (first
          (eval
            "Patient.birthDate.as(date)"
            {:fhir/type :fhir/Patient :id "foo"
             :birthDate #fhir/date"2020"})))))

  (testing "single item with non-matching type returns an empty collection"
    (is
      (empty?
        (first
          (eval
            "Patient.birthDate.as(string)"
            {:fhir/type :fhir/Patient :id "foo"
             :birthDate #fhir/date"2020"})))))

  (testing "empty collection returns empty collection"
    (is
      (empty?
        (first
          (eval
            "Patient.birthDate.as(string)"
            {:fhir/type :fhir/Patient :id "foo"})))))

  (testing "multiple item returns an error"
    (given
      (eval
        "Patient.identifier.as(string)"
        {:fhir/type :fhir/Patient :id "id-162953"
         :identifier
         [#fhir/Identifier{:value "value-163922"}
          #fhir/Identifier{:value "value-163928"}]})
      ::anom/category := ::anom/incorrect
      ::anom/message := "as type specifier with more than one item at the left side `[#fhir/Identifier{:value \"value-163922\"} #fhir/Identifier{:value \"value-163928\"}]`")))



;; 6.5. Boolean logic

;; 6.5.1. and
(deftest and-test
  (are [expr pred] (pred (first (eval expr {:fhir/type :fhir/Patient
                                            :id "id-162953"
                                            :gender #fhir/code"male"
                                            :birthDate #fhir/date"2020"})))
    "Patient.gender = 'male' and Patient.birthDate = @2020" true?
    "Patient.gender = 'male' and Patient.birthDate = @2021" false?
    "Patient.gender = 'female' and Patient.birthDate = @2020" false?
    "Patient.gender = 'female' and Patient.birthDate = @2021" false?))
