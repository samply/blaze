(ns blaze.fhir-path-test
  "See: http://hl7.org/fhirpath/index.html"
  (:refer-clojure :exclude [compile eval])
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir-path-spec]
    [blaze.fhir-path.protocols :as p]
    [blaze.fhir.spec.type]
    [blaze.fhir.spec.type.system :as system]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def ^:private resolver
  (reify
    p/Resolver
    (-resolve [_ _])))


(defn- compile
  ([expr]
   (compile resolver expr))
  ([resolver expr]
   (fhir-path/compile resolver expr)))


(defn- eval
  ([expr coll]
   (eval expr resolver coll))
  ([expr resolver coll]
   (when-ok [expr (compile resolver expr)]
     (fhir-path/eval expr coll))))



;; 3. Path selection
(deftest path-selection-test
  (testing "Resource.id"
    (are [x]
      (= x (first (eval "Resource.id"
                        [{:fhir/type :fhir/Patient
                          :id x}])))
      "id-161533"
      "id-161537"))

  (testing "Patient.id"
    (are [x]
      (= x (first (eval "Patient.id"
                        [{:fhir/type :fhir/Patient
                          :id x}])))
      "id-161533"
      "id-161537"))

  (testing "one item"
    (is (= [true] (eval "Patient.active"
                        [{:fhir/type :fhir/Patient
                          :active true}]))))

  (testing "(Observation.value as boolean)"
    (is (= [true] (eval "(Observation.value as boolean)"
                        [{:fhir/type :fhir/Observation
                          :id "foo"
                          :value true}]))))

  (testing "two items"
    (testing "navigating one level"
      (is (= (eval "Patient.identifier"
                   [{:fhir/type :fhir/Patient
                     :identifier
                     [#fhir/Identifier{:value "foo"}
                      #fhir/Identifier{:value "bar"}]}])
             [#fhir/Identifier{:value "foo"}
              #fhir/Identifier{:value "bar"}])))

    (testing "navigating two levels"
      (is (= (eval "Patient.name.given"
                   [{:fhir/type :fhir/Patient
                     :name
                     [{:fhir/type :fhir/HumanName
                       :given ["foo" "bar"]}]}])
             ["foo" "bar"]))

      (testing "spread over two nodes at level 1"
        (is (= (eval "Patient.name.given"
                     [{:fhir/type :fhir/Patient
                       :name
                       [{:fhir/type :fhir/HumanName
                         :given ["foo"]}
                        {:fhir/type :fhir/HumanName
                         :given ["bar"]}]}])
               ["foo" "bar"]))

        (testing "with two item on the second level 1 node"
          (is (= (eval "Patient.name.given"
                       [{:fhir/type :fhir/Patient
                         :name
                         [{:fhir/type :fhir/HumanName
                           :given ["foo"]}
                          {:fhir/type :fhir/HumanName
                           :given ["bar" "baz"]}]}])
                 ["foo" "bar" "baz"])))))))



;; 4. Expressions

;; 4.1 Literals
(deftest boolean-test
  (is (= [true] (eval "true" [{}])))
  (is (= [false] (eval "false" [{}]))))


(deftest string-test
  #_(given (meta (compile "'bar'"))
    :name := "StringLiteral"
    :value := "bar")

  (is (= ["bar"] (eval "'bar'" [{}]))))


(deftest integer-test
  (is (= [0] (eval "0" [{}]))))


(deftest decimal-test
  (is (= [0.1M] (eval "0.1" [{}]))))


(deftest date-test
  (are [expr date] (= date (first (eval expr [{}])))
    "@2020" (system/date 2020)
    "@2020-01" (system/date 2020 1)
    "@2020-01-02" (system/date 2020 1 2)))


(deftest date-time-test
  (are [expr date-time] (= date-time (first (eval expr [{}])))
    "@2020T" (system/date-time 2020)
    "@2020-01T" (system/date-time 2020 1)
    "@2020-01-02T" (system/date-time 2020 1 2)
    "@2020-01-02T03" (system/date-time 2020 1 2 3)))


;; 4.5. Singleton Evaluation of Collections
(deftest singleton-test
  (testing "string concatenation"
    (testing "with no given name"
      (is (= (first (eval "Patient.name.family + ', ' + Patient.name.given"
                          [{:fhir/type :fhir/Patient
                            :id "foo"
                            :name
                            [{:fhir/type :fhir/HumanName
                              :family "Doe"}]}]))
             "Doe, ")))

    (testing "with one given name"
      (is (= (first (eval "Patient.name.family + ', ' + Patient.name.given"
                          [{:fhir/type :fhir/Patient
                            :id "foo"
                            :name
                            [{:fhir/type :fhir/HumanName
                              :family "Doe"
                              :given ["John"]}]}]))
             "Doe, John")))

    (testing "with two given names"
      (given (eval "Patient.name.family + ', ' + Patient.name.given"
                   [{:fhir/type :fhir/Patient
                     :id "foo"
                     :name
                     [{:fhir/type :fhir/HumanName
                       :family "Doe"
                       :given ["John" "Foo"]}]}])
        ::anom/category := ::anom/incorrect
        ::anom/message := "unable to evaluate `[\"John\" \"Foo\"]` as singleton"))

    (testing "with non-convertible type"
      (given (eval "Patient.name.family + ', ' + Patient.name"
                   [{:fhir/type :fhir/Patient
                     :id "foo"
                     :name
                     [{:fhir/type :fhir/HumanName
                       :family "Doe"}]}])
        ::anom/category := ::anom/incorrect
        ::anom/message := "unable to evaluate `[{:fhir/type :fhir/HumanName, :family \"Doe\"}]` as singleton")))

  (testing "and expression"
    (testing "with one telecom"
      (is (= (first (eval "Patient.active and Patient.gender and Patient.telecom"
                          [{:fhir/type :fhir/Patient
                            :id "foo"
                            :active true
                            :gender #fhir/code"female"
                            :telecom
                            [{:fhir/type :fhir/ContactPoint
                              :use #fhir/code"home"
                              :value "foo"}]}]))
             true)))

    (testing "with two telecoms"
      (given (eval "Patient.active and Patient.gender and Patient.telecom"
                   [{:fhir/type :fhir/Patient
                     :id "foo"
                     :active true
                     :gender #fhir/code"female"
                     :telecom
                     [{:fhir/type :fhir/ContactPoint
                       :use #fhir/code"home"
                       :value "foo"}
                      {:fhir/type :fhir/ContactPoint
                       :use #fhir/code"work"
                       :value "bar"}]}])
        ::anom/category := ::anom/incorrect
        ::anom/message := "unable to evaluate `[{:fhir/type :fhir/ContactPoint, :use #fhir/code\"home\", :value \"foo\"} {:fhir/type :fhir/ContactPoint, :use #fhir/code\"work\", :value \"bar\"}]` as singleton"))))



;; 5. Functions

(deftest resolve-function-test
  (testing "with local ref"
    (given
      (eval
        "Specimen.subject.where(resolve() is Patient)"
        [{:fhir/type :fhir/Specimen :id "id-175250"
          :subject #fhir/Reference{:reference "Patient/id-130855"}}])
      [0 :reference] := "Patient/id-130855"))

  (testing "with non-local ref"
    (let [resolver
          (reify
            p/Resolver
            (-resolve [_ uri]
              (when (= "reference-180039" uri)
                {:fhir/type :fhir/Patient
                 :id "id-164737"})))]
      (is
        (eval
          "resolve()"
          resolver
          [#fhir/Reference{:reference "reference-180039"}])
        {:fhir/type :fhir/Patient
         :id "id-164737"}))))


;; 5.1. Existence

;; 5.1.2. exists([criteria : expression]) : Boolean
(deftest exists-function-test
  (is (= [false] (eval "Patient.deceased.exists()"
                       [{:fhir/type :fhir/Patient
                         :id "id-182007"}])))

  (is (= [true] (eval "Patient.deceased.exists()"
                      [{:fhir/type :fhir/Patient
                        :id "id-182007"
                        :deceased true}]))))


;; 5.2. Filtering and projection

;; 5.2.1. where(criteria : expression) : collection
(deftest where-function-test
  (testing "missing criteria"
    (given (fhir-path/compile resolver "Patient.telecom.where()")
      ::anom/category := ::anom/incorrect
      ::anom/message := "missing criteria in `where` function in expression `Patient.telecom.where()`"))

  (testing "returns one matching item"
    (given
      (eval
        "Patient.telecom.where(use = 'home')"
        [{:fhir/type :fhir/Patient
          :id "id-162953"
          :telecom
          [{:fhir/type :fhir/ContactPoint
            :use #fhir/code"home"
            :value "value-170758"}]}])
      [0 :use] := #fhir/code"home"
      [0 :value] := "value-170758"))

  (testing "returns two matching items"
    (given
      (eval
        "Patient.telecom.where(use = 'home')"
        [{:fhir/type :fhir/Patient
          :id "id-162953"
          :telecom
          [{:fhir/type :fhir/ContactPoint
            :use #fhir/code"home"
            :value "value-170758"}
           {:fhir/type :fhir/ContactPoint
            :use #fhir/code"home"
            :value "value-145928"}]}])
      [0 :use] := #fhir/code"home"
      [0 :value] := "value-170758"
      [1 :use] := #fhir/code"home"
      [1 :value] := "value-145928"))

  (testing "returns empty collection on non-matching item"
    (is (empty? (eval "Patient.telecom.where(use = 'work')"
                      [{:fhir/type :fhir/Patient
                        :id "id-162953"
                        :telecom
                        [{:fhir/type :fhir/ContactPoint
                          :use #fhir/code"home"
                          :value "value-170758"}]}]))))

  (testing "returns empty collection on empty criteria result"
    (is (empty? (eval "Patient.telecom.where({})"
                      [{:fhir/type :fhir/Patient
                        :id "id-162953"
                        :telecom
                        [{:fhir/type :fhir/ContactPoint
                          :use #fhir/code"home"
                          :value "value-170758"}]}]))))

  (testing "returns empty collection on empty input"
    (is (empty? (eval "Patient.telecom.where(use = 'home')"
                      [{:fhir/type :fhir/Patient
                        :id "id-162953"}]))))

  (testing "return error on multiple criteria result"
    (meta (compile "where(a)"))
    (given (eval "where(a)" [{:a [true true]}])
      ::anom/category := ::anom/incorrect
      ::anom/message := "more than one item"))

  (testing "return error on non-boolean criteria result"
    (given
      (eval
        "Patient.telecom.where(use)"
        [{:fhir/type :fhir/Patient
          :id "id-162953"
          :telecom
          [{:fhir/type :fhir/ContactPoint
            :use #fhir/code"home"
            :value "value-170758"}]}])
      ::anom/category := ::anom/incorrect
      ::anom/message := "non-boolean result `#fhir/code\"home\"` of type `:fhir/code` while evaluating where function criteria")))


;; 5.2.4. ofType(type : type specifier) : collection
(deftest of-type-function-test
  (testing "returns two matching items"
    (is (= (eval "ofType(Quantity)"
                 [#fhir/Quantity{:value 150M}
                  #fhir/Quantity{:value 100M}
                  200M])
           [#fhir/Quantity{:value 150M}
            #fhir/Quantity{:value 100M}]))))



;; 5.3. Subsetting

;; 5.3.1. [ index : Integer ] : collection
(deftest indexer-test
  (let [bundle {:fhir/type :fhir/Bundle
                :entry
                [{:fhir/type :fhir.Bundle/entry
                  :resource
                  {:fhir/type :fhir/Patient
                   :id "foo"}}
                 {:fhir/type :fhir.Bundle/entry
                  :resource
                  {:fhir/type :fhir/Patient
                   :id "bar"}}]}]
    (is (= (eval "Bundle.entry[0].resource.id" [bundle])
           ["foo"]))
    (is (= (eval "Bundle.entry[1].resource.id" [bundle])
           ["bar"]))
    (is (= (eval "Bundle.entry[2].resource.id" [bundle])
           []))))


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
    (are [expr res] (= res (set (eval expr [patient])))
      "{} | {}" #{}
      "1 | {}" #{1}
      "{} | 1" #{1}
      "1 | 1" #{1}
      "1 | 2 | 3" #{1 2 3}
      "Patient.name.family | {}" #{"Doe" "Bolton"}
      "Patient.name.family | 'Wade'" #{"Wade" "Doe" "Bolton"}
      "{} | Patient.name.family" #{"Doe" "Bolton"}
      "'Wade' | Patient.name.family" #{"Wade" "Doe" "Bolton"}
      "Patient.name.family | Patient.name.family" #{"Doe" "Bolton"}))

  (is (= (eval "Patient.gender | Patient.birthDate"
               [{:fhir/type :fhir/Patient
                 :gender #fhir/code"female"
                 :birthDate #fhir/date"2020"}])
         [#fhir/code"female" #fhir/date"2020"])))



;; 6. Operations

;; 6.1. Equality

;; 6.1.1. = (Equals)
(deftest equals-test
  (testing "propagates empty collections"
    (testing "both empty"
      (is (empty? (eval "{} = {}" [{}]))))

    (testing "left empty"
      (is (empty? (eval "{} = 'foo'" [{}]))))

    (testing "right empty"
      (is (empty? (eval "'foo' = {}" [{}])))))

  (testing "string comparison"
    (is (= [true] (eval "'foo' = 'foo'" [{}])))
    (is (= [false] (eval "'foo' = 'bar'" [{}])))))


;; 6.1.3. != (Not Equals)
(deftest not-equals-test
  (testing "propagates empty collections"
    (testing "both empty"
      (is (empty? (eval "{} != {}" [{}]))))

    (testing "left empty"
      (is (empty? (eval "{} != 'foo'" [{}]))))

    (testing "right empty"
      (is (empty? (eval "'foo' != {}" [{}]))))

    (testing "string comparison"
      (is (= [false] (eval "'foo' != 'foo'" [{}])))
      (is (= [true] (eval "'foo' != 'bar'" [{}]))))))


;; 6.3. Types

;; 6.3.1. is type specifier
(deftest is-type-specifier-test
  (testing "single item with matching type returns true"
    (is
      (true?
        (first
          (eval
            "Patient.birthDate is date"
            [{:fhir/type :fhir/Patient :id "foo"
              :birthDate #fhir/date"2020"}])))))

  (testing "single item with non-matching type returns false"
    (is
      (false?
        (first
          (eval
            "Patient.birthDate is string"
            [{:fhir/type :fhir/Patient :id "foo"
              :birthDate #fhir/date"2020"}])))))

  (testing "empty collection returns empty collection"
    (is
      (empty?
        (first
          (eval
            "Patient.birthDate is string"
            [{:fhir/type :fhir/Patient :id "foo"}])))))

  (testing "multiple item returns an error"
    (given
      (eval
        "Patient.identifier is string"
        [{:fhir/type :fhir/Patient
          :identifier
          [#fhir/Identifier{:value "foo"}
           #fhir/Identifier{:value "bar"}]}])
      ::anom/category := ::anom/incorrect
      :item-1 := #fhir/Identifier{:value "foo"}
      :item-2 := #fhir/Identifier{:value "bar"}
      #_#_#_[:expression :name] := "IsTypeExpression"))

  (testing "with resolve"
    (testing "resolves to"
      (testing "empty collection"
        (is (empty? (eval "resolve() is Patient" [{:fhir/type :fhir/Reference}]))))

      (testing "single item"
        (testing "types match"
          (is (= [true] (eval "resolve() is Patient" [{:fhir/type :fhir/Reference
                                                       :reference "Patient/foo"}]))))

        (testing "types do not match"
          (is (= [false] (eval "resolve() is Patient" [{:fhir/type :fhir/Reference
                                                        :reference "Group/foo"}]))))))))


;; 6.3.3 as type specifier
(deftest as-type-specifier-test
  (testing "single item with matching type returns the item"
    (is (= (eval "Patient.birthDate as date"
                 [{:fhir/type :fhir/Patient
                   :birthDate #fhir/date"2020"}])
           [#fhir/date"2020"])))

  (testing "single item with non-matching type returns an empty collection"
    (is (empty? (eval "Patient.birthDate as string"
                      [{:fhir/type :fhir/Patient
                        :birthDate #fhir/date"2020"}]))))

  (testing "empty collection returns empty collection"
    (is (empty? (eval "Patient.birthDate as string"
                      [{:fhir/type :fhir/Patient}]))))

  (testing "multiple item returns an error"
    (given (eval "Patient.identifier as string"
                 [{:fhir/type :fhir/Patient
                   :identifier
                   [#fhir/Identifier{:value "foo"}
                    #fhir/Identifier{:value "bar"}]}])
      ::anom/category := ::anom/incorrect
      :item-1 := #fhir/Identifier{:value "foo"}
      :item-2 := #fhir/Identifier{:value "bar"}
      #_#_#_[:expression :name] := "AsTypeExpression")))


;; 6.3.4 as(type : type specifier)
(deftest as-function-test
  (testing "single item with matching type returns the item"
    (is (= (eval "Patient.birthDate.as(date)"
                 [{:fhir/type :fhir/Patient
                   :birthDate #fhir/date"2020"}])
           [#fhir/date"2020"])))

  (testing "single item with non-matching type returns an empty collection"
    (is (empty? (eval "Patient.birthDate.as(string)"
                      [{:fhir/type :fhir/Patient
                        :birthDate #fhir/date"2020"}]))))

  (testing "empty collection returns empty collection"
    (is (empty? (eval "Patient.birthDate.as(string)"
                      [{:fhir/type :fhir/Patient}]))))

  (testing "multiple item returns an error"
    (given (eval "Patient.identifier.as(string)"
                 [{:fhir/type :fhir/Patient
                   :identifier
                   [#fhir/Identifier{:value "foo"}
                    #fhir/Identifier{:value "bar"}]}])
      ::anom/category := ::anom/incorrect
      :item-1 := #fhir/Identifier{:value "foo"}
      :item-2 := #fhir/Identifier{:value "bar"}
      #_#_#_[:expression :name] := "InvocationExpression"
      #_#_#_[:expression :invocation :name] := "FunctionExpression"))

  (testing "missing type returns an error"
    (given (eval "Patient.identifier.as()" [])
      ::anom/category := ::anom/incorrect
      ::anom/message := "missing type specifier in `as` function in expression `Patient.identifier.as()`")))



;; 6.5. Boolean logic

;; 6.5.1. and
(deftest and-test
  (are [expr pred] (pred (first (eval expr [{:fhir/type :fhir/Patient
                                             :id "id-162953"
                                             :gender #fhir/code"male"
                                             :birthDate #fhir/date"2020"}])))
    "Patient.gender = 'male' and Patient.birthDate = @2020" true?
    "Patient.gender = 'male' and Patient.birthDate = @2021" false?
    "Patient.gender = 'female' and Patient.birthDate = @2020" false?
    "Patient.gender = 'female' and Patient.birthDate = @2021" false?))


;; 6.6.3. + (addition)
(deftest addition-test
  (testing "string"
    (is (= ["foobar"] (eval "'foo' + 'bar'" [{}])))))

(comment
  (eval "Patient.active and Patient.gender"
        [{:fhir/type :fhir/Patient
          :active true
          :gender #fhir/code"male"}])
  )
