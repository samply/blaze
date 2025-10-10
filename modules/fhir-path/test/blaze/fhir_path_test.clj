(ns blaze.fhir-path-test
  "See: http://hl7.org/fhirpath/index.html"
  (:refer-clojure :exclude [eval])
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir-path-spec]
   [blaze.fhir.test-util]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest compile-test
  (testing "random input"
    (satisfies-prop 1000
      (prop/for-all [s gen/string]
        (let [x (fhir-path/compile s)]
          (or (ba/anomaly? x) (satisfies? fhir-path/Expression x)))))))

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
    (are [x] (= x (first (eval "Resource.id"
                               {:fhir/type :fhir/Patient
                                :id x})))
      "id-161533"
      "id-161537"))

  (testing "Patient.id"
    (are [x] (= x (first (eval "Patient.id"
                               {:fhir/type :fhir/Patient
                                :id x})))
      "id-161533"
      "id-161537"))

  (testing "Patient.active"
    (are [x] (= x (first (eval "Patient.active"
                               {:fhir/type :fhir/Patient
                                :id "foo"
                                :active x})))
      #fhir/boolean true
      #fhir/boolean false))

  (testing "(Observation.value as boolean)"
    (are [x] (= x (first (eval "(Observation.value as boolean)"
                               {:fhir/type :fhir/Observation
                                :id "foo"
                                :value x})))
      #fhir/boolean true
      #fhir/boolean false)))

;; 4. Expressions

;; 4.1 Literals
(deftest boolean-test
  (is (true? (first (eval "true" #fhir/string "foo"))))
  (is (false? (first (eval "false" #fhir/string "foo")))))

(deftest string-test
  (is (= "bar" (first (eval "'bar'" #fhir/string "foo")))))

(deftest integer-test
  (is (zero? (first (eval "0" #fhir/string "foo")))))

(deftest decimal-test
  (is (= 0.1M (first (eval "0.1" #fhir/string "foo")))))

(deftest date-test
  (are [expr date] (= date (first (eval expr #fhir/string "foo")))
    "@2020" #system/date"2020"
    "@2020-01" #system/date"2020-01"
    "@2020-01-02" #system/date"2020-01-02"))

(deftest date-time-test
  (are [expr date-time] (= date-time (first (eval expr #fhir/string "foo")))
    "@2020T" #system/date-time"2020"
    "@2020-01T" #system/date-time"2020-01"
    "@2020-01-02T" #system/date-time"2020-01-02"
    "@2020-01-02T03" #system/date-time"2020-01-02T03"))

;; 4.5. Singleton Evaluation of Collections
(deftest singleton-test
  (testing "string concatenation"
    (testing "with no given name"
      (is (empty? (eval "Patient.name.family + ', ' + Patient.name.given"
                        {:fhir/type :fhir/Patient
                         :id "foo"
                         :name [#fhir/HumanName{:family #fhir/string "Doe"}]}))))

    (testing "with one given name"
      (is (= (eval "Patient.name.family + ', ' + Patient.name.given"
                   {:fhir/type :fhir/Patient
                    :id "foo"
                    :name [#fhir/HumanName{:family #fhir/string "Doe" :given [#fhir/string "John"]}]})
             ["Doe, John"])))

    (testing "with two given names"
      (given (eval "Patient.name.family + ', ' + Patient.name.given"
                   {:fhir/type :fhir/Patient
                    :id "foo"
                    :name [#fhir/HumanName{:family #fhir/string "Doe" :given [#fhir/string "John" #fhir/string "Foo"]}]})
        ::anom/category := ::anom/incorrect
        ::anom/message := "unable to evaluate `[#fhir/string-interned \"John\" #fhir/string-interned \"Foo\"]` as singleton"))

    (testing "with non-convertible type"
      (given (eval "Patient.name.family + ', ' + Patient.name"
                   {:fhir/type :fhir/Patient
                    :id "foo"
                    :name [#fhir/HumanName{:family #fhir/string "Doe"}]})
        ::anom/category := ::anom/incorrect
        ::anom/message := "unable to evaluate `[#fhir/HumanName{:family #fhir/string-interned \"Doe\"}]` as singleton")))

  (testing "and expression"
    (testing "with one telecom"
      (given (eval "Patient.active and Patient.gender and Patient.telecom"
                   {:fhir/type :fhir/Patient
                    :id "foo"
                    :active #fhir/boolean true
                    :gender #fhir/code "female"
                    :telecom
                    [#fhir/ContactPoint
                      {:value #fhir/string "foo"
                       :use #fhir/code "home"}]})
        identity := [true]))

    (testing "with two telecoms"
      (given (eval "Patient.active and Patient.gender and Patient.telecom"
                   {:fhir/type :fhir/Patient
                    :id "foo"
                    :active #fhir/boolean true
                    :gender #fhir/code "female"
                    :telecom
                    [#fhir/ContactPoint
                      {:value #fhir/string "foo"
                       :use #fhir/code "home"}
                     #fhir/ContactPoint
                      {:value #fhir/string "bar"
                       :use #fhir/code "work"}]})
        ::anom/category := ::anom/incorrect
        ::anom/message := "unable to evaluate `[#fhir/ContactPoint{:value #fhir/string-interned \"foo\" :use #fhir/code \"home\"} #fhir/ContactPoint{:value #fhir/string-interned \"bar\" :use #fhir/code \"work\"}]` as singleton"))))

;; 5. Functions

(deftest resolve-function-test
  (testing "Specimen with reference to Patient"
    (let [resolver
          (reify
            fhir-path/Resolver
            (-resolve [_ uri]
              (when (= "reference-180039" uri)
                {:fhir/type :fhir/Patient
                 :id "id-164737"})))]
      (given (eval
              "Specimen.subject.where(resolve() is Patient)"
              resolver
              {:fhir/type :fhir/Specimen :id "foo"
               :subject #fhir/Reference{:reference #fhir/string "reference-180039"}})
        [0 :reference] := #fhir/string "reference-180039")))

  (testing "Specimen with display only reference"
    (given (eval
            "Specimen.subject.where(resolve() is Patient)"
            resolver
            {:fhir/type :fhir/Specimen :id "foo"
             :subject #fhir/Reference{:display #fhir/string "foo"}})
      count := 0))

  (testing "Resolving on unsupported data type is skipped"
    (given (eval
            "Patient.gender.where(resolve())"
            resolver
            {:fhir/type :fhir/Patient :id "foo"
             :gender #fhir/code "unknown"})
      count := 0))

  (testing "Resolving string"
    (given (eval
            "Patient.id.where(resolve())"
            resolver
            {:fhir/type :fhir/Patient :id "foo"})
      count := 0)))

;; 5.1. Existence

;; 5.1.2. exists([criteria : expression]) : Boolean
(deftest exists-function-test
  (are [patient res] (= [res] (eval "Patient.deceased.exists()" patient))
    {:fhir/type :fhir/Patient} false
    {:fhir/type :fhir/Patient :deceased #fhir/boolean true} true
    {:fhir/type :fhir/Patient :deceased #fhir/boolean false} true)

  (given (eval "Patient.identifier.exists(use = 'official')" {:fhir/type :fhir/Patient})
    ::anom/category := ::anom/unsupported
    ::anom/message := "unsupported `exists` function with criteria"))

;; 5.2. Filtering and projection

;; 5.2.1. where(criteria : expression) : collection
(deftest where-function-test
  (testing "missing criteria"
    (given (fhir-path/compile "Patient.telecom.where()")
      ::anom/category := ::anom/incorrect
      ::anom/message := "missing criteria in `where` function in expression `Patient.telecom.where()`"))

  (testing "returns one matching item"
    (given (eval
            "Patient.telecom.where(use = 'home')"
            {:fhir/type :fhir/Patient
             :id "id-162953"
             :telecom
             [#fhir/ContactPoint{:value #fhir/string "value-170758"
                                 :use #fhir/code "home"}]})
      [0 :use] := #fhir/code "home"
      [0 :value] := #fhir/string "value-170758"))

  (testing "returns two matching items"
    (given (eval
            "Patient.telecom.where(use = 'home')"
            {:fhir/type :fhir/Patient
             :id "id-162953"
             :telecom
             [#fhir/ContactPoint{:value #fhir/string "value-170758"
                                 :use #fhir/code "home"}
              #fhir/ContactPoint{:value #fhir/string "value-145928"
                                 :use #fhir/code "home"}]})
      [0 :use] := #fhir/code "home"
      [0 :value] := #fhir/string "value-170758"
      [1 :use] := #fhir/code "home"
      [1 :value] := #fhir/string "value-145928"))

  (testing "returns empty collection on non-matching item"
    (given (eval
            "Patient.telecom.where(use = 'work')"
            {:fhir/type :fhir/Patient
             :id "id-162953"
             :telecom
             [#fhir/ContactPoint{:value #fhir/string "value-170758"
                                 :use #fhir/code "home"}]})
      count := 0))

  (testing "returns empty collection on empty criteria result"
    (given (eval
            "Patient.telecom.where({})"
            {:fhir/type :fhir/Patient
             :id "id-162953"
             :telecom
             [#fhir/ContactPoint{:value #fhir/string "value-170758"
                                 :use #fhir/code "home"}]})
      count := 0))

  (testing "returns empty collection on empty input"
    (given (eval
            "Patient.telecom.where(use = 'home')"
            {:fhir/type :fhir/Patient
             :id "id-162953"})
      count := 0))

  (testing "return error on multiple criteria result"
    (given (eval
            "Patient.address.where(line)"
            {:fhir/type :fhir/Patient
             :id "id-162953"
             :address [#fhir/Address{:line [#fhir/string "a" #fhir/string "b"]}]})
      ::anom/category := ::anom/incorrect
      ::anom/message := "multiple result items `[#fhir/string-interned \"a\" #fhir/string-interned \"b\"]` while evaluating where function criteria"))

  (testing "return error on non-boolean criteria result"
    (given (eval
            "Patient.telecom.where(use)"
            {:fhir/type :fhir/Patient
             :id "id-162953"
             :telecom
             [#fhir/ContactPoint{:value #fhir/string "value-170758"
                                 :use #fhir/code "home"}]})
      ::anom/category := ::anom/incorrect
      ::anom/message := "non-boolean result `#fhir/code \"home\"` of type `:fhir/code` while evaluating where function criteria")))

;; 5.2.4. ofType(type : type specifier) : collection
(deftest of-type-function-test
  (testing "returns two matching items"
    (given (eval
            "Observation.component.value.ofType(Quantity)"
            {:fhir/type :fhir/Observation
             :component
             [{:fhir/type :fhir.Observation/component
               :value #fhir/Quantity{:value #fhir/decimal 150M}}
              {:fhir/type :fhir.Observation/component
               :value #fhir/Quantity{:value #fhir/decimal 100M}}]})
      [0 :value] := #fhir/decimal 150M
      [1 :value] := #fhir/decimal 100M)))

;; 5.3. Subsetting

;; 5.3.1. [ index : Integer ] : collection
(deftest indexer-test
  (are [resource expr result] (= result (eval expr resource))
    {:fhir/type :fhir/Patient
     :name
     [#fhir/HumanName{:family #fhir/string "Doe"}
      #fhir/HumanName{:family #fhir/string "Bolton"}]}
    "Patient.name[0]"
    [#fhir/HumanName{:family #fhir/string "Doe"}]

    {:fhir/type :fhir/Patient
     :name
     [#fhir/HumanName{:family #fhir/string "Doe"}
      #fhir/HumanName{:family #fhir/string "Bolton"}]}
    "Patient.name[1]"
    [#fhir/HumanName{:family #fhir/string "Bolton"}]

    {:fhir/type :fhir/Patient
     :name
     [#fhir/HumanName{:family #fhir/string "Doe"}
      #fhir/HumanName{:family #fhir/string "Bolton"}]}
    "Patient.name[2]"
    []

    {:fhir/type :fhir/Patient}
    "Patient.name[0]"
    []))

;; 5.3.3. first() : collection
(deftest first-test
  (are [resource expr result] (= result (eval expr resource))
    {:fhir/type :fhir/Patient
     :name
     [#fhir/HumanName{:family #fhir/string "Doe"}
      #fhir/HumanName{:family #fhir/string "Bolton"}]}
    "Patient.name.first()"
    [#fhir/HumanName{:family #fhir/string "Doe"}]

    {:fhir/type :fhir/Patient}
    "Patient.name.first()"
    []))

;; 5.4. Combining

;; 5.4.1. union(other : collection)
(deftest union-test
  (let [patient
        {:fhir/type :fhir/Patient :id "foo"
         :name
         [#fhir/HumanName{:family #fhir/string "Doe"}
          #fhir/HumanName{:family #fhir/string "Bolton"}]}]
    (are [expr res] (= (set res) (set (eval expr patient)))
      "{} | {}" []
      "1 | {}" [1]
      "{} | 1" [1]
      "1 | 1" [1]
      "Patient.name.family | {}" [#fhir/string "Doe" #fhir/string "Bolton"]
      "Patient.name.family | 'Wade'" ["Wade" #fhir/string "Doe" #fhir/string "Bolton"]
      "{} | Patient.name.family" [#fhir/string "Doe" #fhir/string "Bolton"]
      "'Wade' | Patient.name.family" ["Wade" #fhir/string "Doe" #fhir/string "Bolton"]
      "Patient.name.family | Patient.name.family" [#fhir/string "Doe" #fhir/string "Bolton"]))

  (given (eval
          "Patient.gender | Patient.birthDate"
          {:fhir/type :fhir/Patient
           :id "id-162953"
           :gender #fhir/code "female"
           :birthDate #fhir/date #system/date "2020"})
    identity := [#fhir/code "female" #fhir/date #system/date "2020"]))

;; 6. Operations

;; 6.1. Equality

;; 6.1.1. = (Equals)
(deftest equals-test
  (testing "propagates empty collections"
    (testing "both empty"
      (given (eval
              "{} = {}"
              {:fhir/type :fhir/Patient
               :id "foo"})
        count := 0))

    (testing "left empty"
      (given (eval
              "{} = Patient.id"
              {:fhir/type :fhir/Patient
               :id "foo"})
        count := 0))

    (testing "right empty"
      (given (eval
              "Patient.id = {}"
              {:fhir/type :fhir/Patient
               :id "foo"})
        count := 0)))

  (testing "string comparison"
    (given (eval "Patient.id = 'foo'"
                 {:fhir/type :fhir/Patient
                  :id "foo"})
      identity := [true])

    (given (eval "Patient.id = 'bar'"
                 {:fhir/type :fhir/Patient
                  :id "foo"})
      identity := [false])))

;; 6.1.3. != (Not Equals)
(deftest not-equals-test
  (testing "propagates empty collections"
    (testing "both empty"
      (given (eval
              "{} != {}"
              {:fhir/type :fhir/Patient
               :id "foo"})
        count := 0))

    (testing "left empty"
      (given (eval
              "{} != Patient.id"
              {:fhir/type :fhir/Patient
               :id "foo"})
        count := 0))

    (testing "right empty"
      (given (eval
              "Patient.id != {}"
              {:fhir/type :fhir/Patient
               :id "foo"})
        count := 0))

    (testing "string comparison"
      (given (eval "Patient.id != 'bar'"
                   {:fhir/type :fhir/Patient
                    :id "foo"})
        identity := [true])

      (given (eval "Patient.id != 'foo'"
                   {:fhir/type :fhir/Patient
                    :id "foo"})
        identity := [false]))))

;; 6.3. Types

;; 6.3.1. is type specifier
(deftest is-type-specifier-test
  (testing "single item with matching type returns true"
    (given (eval
            "Patient.birthDate is date"
            {:fhir/type :fhir/Patient :id "foo"
             :birthDate #fhir/date #system/date "2020"})
      identity := [true]))

  (testing "single item with non-matching type returns false"
    (given (eval
            "Patient.birthDate is string"
            {:fhir/type :fhir/Patient :id "foo"
             :birthDate #fhir/date #system/date "2020"})
      identity := [false]))

  (testing "empty collection returns empty collection"
    (given (eval
            "Patient.birthDate is string"
            {:fhir/type :fhir/Patient :id "foo"})
      count := 0))

  (testing "multiple item returns an error"
    (given (eval
            "Patient.identifier is string"
            {:fhir/type :fhir/Patient :id "id-162953"
             :identifier
             [#fhir/Identifier{:value #fhir/string "value-163922"}
              #fhir/Identifier{:value #fhir/string "value-163928"}]})
      ::anom/category := ::anom/incorrect
      ::anom/message := "is type specifier with more than one item at the left side `[#fhir/Identifier{:value #fhir/string \"value-163922\"} #fhir/Identifier{:value #fhir/string \"value-163928\"}]`")))

;; 6.3.3 as type specifier
(deftest as-type-specifier-test
  (testing "single item with matching type returns the item"
    (given (eval
            "Patient.birthDate as date"
            {:fhir/type :fhir/Patient :id "foo"
             :birthDate #fhir/date #system/date "2020"})
      identity := [#fhir/date #system/date "2020"]))

  (testing "single item with non-matching type returns an empty collection"
    (given (eval
            "Patient.birthDate as string"
            {:fhir/type :fhir/Patient :id "foo"
             :birthDate #fhir/date #system/date "2020"})
      count := 0))

  (testing "empty collection returns empty collection"
    (given (eval
            "Patient.birthDate as string"
            {:fhir/type :fhir/Patient :id "foo"})
      count := 0))

  ;; HACK: normally multiple items should throw an error. However in R4 many
  ;; FHIRPath expressions of search parameters use the as type specifier wrongly.
  ;; Please remove that hack for R5.
  (testing "multiple items are filtered"
    (given (eval
            "Patient.identifier as Identifier"
            {:fhir/type :fhir/Patient :id "id-162953"
             :identifier
             [#fhir/Identifier{:value #fhir/string "value-163922"}
              #fhir/Identifier{:value #fhir/string "value-163928"}]})
      count := 2
      [0] := #fhir/Identifier{:value #fhir/string "value-163922"}
      [1] := #fhir/Identifier{:value #fhir/string "value-163928"})))

;; 6.3.4 as(type : type specifier)
(deftest as-function-test
  (testing "single item with matching type returns the item"
    (given (eval
            "Patient.birthDate.as(date)"
            {:fhir/type :fhir/Patient :id "foo"
             :birthDate #fhir/date #system/date "2020"})
      identity := [#fhir/date #system/date "2020"]))

  (testing "single item with non-matching type returns an empty collection"
    (given (eval
            "Patient.birthDate.as(string)"
            {:fhir/type :fhir/Patient :id "foo"
             :birthDate #fhir/date #system/date "2020"})
      count := 0))

  (testing "empty collection returns empty collection"
    (given (eval
            "Patient.birthDate.as(string)"
            {:fhir/type :fhir/Patient :id "foo"})
      count := 0))

  ;; HACK: normally multiple items should throw an error. However in R4 many
  ;; FHIRPath expressions of search parameters use the as type specifier wrongly.
  ;; Please remove that hack for R5.
  (testing "multiple items are filtered"
    (given (eval
            "Patient.identifier.as(Identifier)"
            {:fhir/type :fhir/Patient :id "id-162953"
             :identifier
             [#fhir/Identifier{:value #fhir/string "value-163922"}
              #fhir/Identifier{:value #fhir/string "value-163928"}]})
      count := 2
      [0] := #fhir/Identifier{:value #fhir/string "value-163922"}
      [1] := #fhir/Identifier{:value #fhir/string "value-163928"})))

;; 6.5. Boolean logic

;; 6.5.1. and
(deftest and-test
  (let [patient {:fhir/type :fhir/Patient
                 :gender #fhir/code "male"
                 :birthDate #fhir/date #system/date "2020"}]
    (are [expr pred] (pred (first (eval expr patient)))
      "Patient.gender = 'male' and Patient.birthDate = @2020" true?
      "Patient.gender = 'male' and Patient.birthDate = @2021" false?
      "Patient.gender = 'female' and Patient.birthDate = @2020" false?
      "Patient.gender = 'female' and Patient.birthDate = @2021" false?)))

;; Additional functions (https://www.hl7.org/fhir/fhirpath.html#functions)

(deftest extension-test
  (testing "missing url returns empty collection"
    (given (eval
            "Patient.extension().value"
            {:fhir/type :fhir/Patient :id "foo"
             :extension [#fhir/Extension{:url "url-145553" :value #fhir/string "value-145600"}]})
      count := 0))

  (given (eval
          "Patient.extension('url-145553').value"
          {:fhir/type :fhir/Patient :id "foo"
           :extension [#fhir/Extension{:url "url-145553" :value #fhir/string "value-145600"}]})
    identity := [#fhir/string "value-145600"]))

;; 6.6. Math

;; 6.6.3. + (addition)

(deftest plus-test
  (testing "string"
    (are [expr result] (= result (eval expr {:fhir/type :fhir/Patient :id "0"}))
      "Patient.id + '1'" ["01"]
      "'1' + Patient.id" ["10"]
      "Patient.id + {}" []
      "{} + Patient.id" [])))
