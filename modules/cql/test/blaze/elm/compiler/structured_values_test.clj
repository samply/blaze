(ns blaze.elm.compiler.structured-values-test
  "2. Structured Values

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.coll.core :as coll]
   [blaze.elm.code :refer [code]]
   [blaze.elm.code-spec]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.core-spec]
   [blaze.elm.compiler.structured-values]
   [blaze.elm.compiler.test-util :as ctu :refer [has-form]]
   [blaze.elm.literal]
   [blaze.elm.literal-spec]
   [blaze.fhir.spec.type]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [juxt.iota :refer [given]])
  (:import
   [blaze.elm.code Code]))

(st/instrument)
(ctu/instrument-compile)

(defn- fixture [f]
  (st/instrument)
  (ctu/instrument-compile)
  (f)
  (st/unstrument))

(test/use-fixtures :each fixture)

;; 2.1. Tuple
;;
;; The Tuple expression allows tuples of any type to be built up as an
;; expression. The tupleType attribute specifies the type of the tuple being
;; built, if any, and the list of tuple elements specify the values for the
;; elements of the tuple. Note that the value of an element may be any
;; expression, including another Tuple.
(deftest compile-tuple-test
  (testing "Static"
    (are [elm res] (= res (core/-eval (c/compile {} elm) {} nil nil))
      #elm/tuple{"id" #elm/integer "1"}
      {:id 1}

      #elm/tuple{"id" #elm/integer "1" "name" #elm/string "john"}
      {:id 1 :name "john"}))

  (testing "Dynamic"
    (are [elm res] (= res (ctu/dynamic-compile-eval elm))
      #elm/tuple{"id" #elm/parameter-ref "1"}
      {:id 1}

      #elm/tuple{"id" #elm/parameter-ref "1" "name" #elm/parameter-ref "a"}
      {:id 1 :name "a"})

    (testing "Static"
      (is (false? (core/-static (ctu/dynamic-compile #elm/tuple{"id" #elm/parameter-ref "1"})))))

    (testing "form"
      (let [expr (ctu/dynamic-compile #elm/tuple{"id" #elm/parameter-ref "x"})]
        (has-form expr '{:id (param-ref "x")})))))

;; 2.2. Instance
;;
;; The Instance expression allows class instances of any type to be built up as
;; an expression. The classType attribute specifies the type of the class
;; instance being built, and the list of instance elements specify the values
;; for the elements of the class instance. Note that the value of an element may
;; be any expression, including another Instance.
(deftest compile-instance-test
  (testing "Code"
    (given (c/compile {} (ctu/code "system-134534" "code-134551"))
      type := Code
      :system := "system-134534"
      :code := "code-134551")))

;; 2.3. Property
;;
;; The Property operator returns the value of the property on source specified
;; by the path attribute.
;;
;; If the result of evaluating source is null, the result is null.
;;
;; The path attribute may include qualifiers (.) and indexers ([x]). Indexers
;; must be literal integer values.
;;
;; If the path attribute contains qualifiers or indexers, each qualifier or
;; indexer is traversed to obtain the actual value. If the object of the
;; property access at any point in traversing the path is null, the result is
;; null.
;;
;; If a scope is specified, the name is used to resolve the scope in which the
;; path will be resolved. Scopes can be named by operators such as Filter and
;; ForEach.
;;
;; Property expressions can also be used to access the individual points and
;; closed indicators for interval types using the property names low, high,
;; lowClosed, and highClosed.
(deftest compile-property-test
  (testing "with scope"
    (testing "with entity supplied over query context"
      (testing "Patient.identifier"
        (testing "with source-type"
          (let [identifier
                #fhir/Identifier
                 {:system #fhir/uri"foo"
                  :value "bar"}
                entity
                {:fhir/type :fhir/Patient :id "0"
                 :identifier [identifier]}
                elm #elm/scope-property ["R" "identifier"]
                expr (c/compile {:eval-context "Patient"} elm)]

            (testing "eval"
              (is (= identifier (coll/first (core/-eval expr nil nil {"R" entity})))))

            (testing "expression is dynamic"
              (is (false? (core/-static expr))))

            (ctu/testing-constant-attach-cache expr)

            (ctu/testing-constant-patient-count expr)

            (ctu/testing-constant-resolve-refs expr)

            (ctu/testing-constant-resolve-params expr)

            (ctu/testing-equals-hash-code elm)

            (testing "form"
              (has-form expr '(:identifier R)))))

        (testing "without source-type"
          (let [identifier
                #fhir/Identifier
                 {:system #fhir/uri"foo"
                  :value "bar"}
                entity
                {:fhir/type :fhir/Patient :id "0"
                 :identifier [identifier]}
                elm #elm/scope-property ["R" "identifier"]
                expr (c/compile {:eval-context "Patient"} elm)]

            (testing "eval"
              (is (= identifier (coll/first (core/-eval expr nil nil {"R" entity})))))

            (testing "expression is dynamic"
              (is (false? (core/-static expr))))

            (ctu/testing-constant-attach-cache expr)

            (ctu/testing-constant-patient-count expr)

            (ctu/testing-constant-resolve-refs expr)

            (ctu/testing-constant-resolve-params expr)

            (ctu/testing-equals-hash-code elm)

            (testing "form"
              (has-form expr '(:identifier R))))))

      (testing "Patient.extension"
        (testing "without source-type"
          (let [extension
                #fhir/Extension
                 {:url "foo"
                  :valueString "bar"}
                entity
                {:fhir/type :fhir/Patient :id "0"
                 :extension [extension]}
                elm #elm/scope-property ["R" "extension"]
                expr (c/compile {:eval-context "Patient"} elm)]

            (testing "eval"
              (is (= extension (coll/first (core/-eval expr nil nil {"R" entity})))))

            (testing "expression is dynamic"
              (is (false? (core/-static expr))))

            (ctu/testing-constant-attach-cache expr)

            (ctu/testing-constant-patient-count expr)

            (ctu/testing-constant-resolve-refs expr)

            (ctu/testing-constant-resolve-params expr)

            (ctu/testing-equals-hash-code elm)

            (testing "form"
              (has-form expr '(:extension R))))))

      (testing "Patient.gender"
        (testing "with source-type"
          (let [entity
                {:fhir/type :fhir/Patient :id "0"
                 :gender #fhir/code"male"}
                elm #elm/scope-property ["R" "gender"]
                expr (c/compile {:eval-context "Patient"} elm)]

            (testing "eval"
              (is (= #fhir/code"male" (core/-eval expr nil nil {"R" entity}))))

            (testing "expression is dynamic"
              (is (false? (core/-static expr))))

            (ctu/testing-constant-attach-cache expr)

            (ctu/testing-constant-patient-count expr)

            (ctu/testing-constant-resolve-refs expr)

            (ctu/testing-constant-resolve-params expr)

            (ctu/testing-equals-hash-code elm)

            (testing "form"
              (has-form expr '(:gender R)))))

        (testing "without source-type"
          (let [entity
                {:fhir/type :fhir/Patient :id "0"
                 :gender #fhir/code"male"}
                elm #elm/scope-property ["R" "gender"]
                expr (c/compile {:eval-context "Patient"} elm)]

            (testing "eval"
              (is (= #fhir/code"male" (core/-eval expr nil nil {"R" entity}))))

            (testing "expression is dynamic"
              (is (false? (core/-static expr))))

            (ctu/testing-constant-attach-cache expr)

            (ctu/testing-constant-patient-count expr)

            (ctu/testing-constant-resolve-refs expr)

            (ctu/testing-constant-resolve-params expr)

            (ctu/testing-equals-hash-code elm)

            (testing "form"
              (has-form expr '(:gender R))))))

      (testing "Patient.birthDate.value"
        (let [entity
              (fn [x]
                {:fhir/type :fhir/Patient :id "0"
                 :birthDate x})
              elm #elm/scope-property ["R" "birthDate.value"]
              expr (c/compile {:eval-context "Patient"} elm)]

          (testing "eval"
            (are [birth-date res] (= res (core/-eval expr nil nil {"R" (entity birth-date)}))
              #fhir/date"2023-05-07" #system/date"2023-05-07"
              #fhir/date{:id "foo" :value "2023-05-07"} #system/date"2023-05-07"
              #fhir/date{:id "foo"} nil
              #fhir/date{:extension [#fhir/Extension{:url "foo"}]} nil))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (ctu/testing-constant-attach-cache expr)

          (ctu/testing-constant-patient-count expr)

          (ctu/testing-constant-resolve-refs expr)

          (ctu/testing-constant-resolve-params expr)

          (ctu/testing-equals-hash-code elm)

          (testing "form"
            (has-form expr '(:value (:birthDate R))))))

      (testing "Observation.value"
        (testing "with source-type"
          (let [entity
                {:fhir/type :fhir/Observation :id "0"
                 :value "value-114318"}
                elm #elm/scope-property ["R" "value"]
                expr (c/compile {:eval-context "Patient"} elm)]

            (testing "eval"
              (is (= "value-114318" (core/-eval expr nil nil {"R" entity}))))

            (testing "expression is dynamic"
              (is (false? (core/-static expr))))

            (ctu/testing-constant-attach-cache expr)

            (ctu/testing-constant-patient-count expr)

            (ctu/testing-constant-resolve-refs expr)

            (ctu/testing-constant-resolve-params expr)

            (ctu/testing-equals-hash-code elm)

            (testing "form"
              (has-form expr '(:value R)))))

        (testing "without source-type"
          (let [entity
                {:fhir/type :fhir/Observation :id "0"
                 :value "value-114318"}
                elm #elm/scope-property ["R" "value"]
                expr (c/compile {:eval-context "Patient"} elm)]

            (testing "eval"
              (is (= "value-114318" (core/-eval expr nil nil {"R" entity}))))

            (testing "expression is dynamic"
              (is (false? (core/-static expr))))

            (ctu/testing-constant-attach-cache expr)

            (ctu/testing-constant-patient-count expr)

            (ctu/testing-constant-resolve-refs expr)

            (ctu/testing-constant-resolve-params expr)

            (ctu/testing-equals-hash-code elm)

            (testing "form"
              (has-form expr '(:value R))))))))

  (testing "with source"
    (testing "Patient.identifier"
      (testing "with source-type"
        (let [library {:statements {:def [{:type "ExpressionDef"
                                           :name "Patient"}]}}
              elm
              #elm/source-property [#elm/expression-ref "Patient" "identifier"]
              identifier
              #fhir/Identifier
               {:system #fhir/uri"foo"
                :value "bar"}
              source
              {:fhir/type :fhir/Patient :id "0"
               :identifier [identifier]}
              expr (c/compile {:library library :eval-context "Patient"} elm)
              expr-def {:type "ExpressionDef"
                        :context "Patient"
                        :name "Patient"
                        :expression source}]

          (testing "eval"
            (is (= identifier (coll/first (core/-eval expr {:expression-defs {"Patient" expr-def}} nil nil)))))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (ctu/testing-constant-attach-cache expr)

          (ctu/testing-constant-patient-count expr)

          (testing "resolve expression references"
            (let [expr (c/resolve-refs expr {"Patient" expr-def})]
              (has-form expr (list :identifier source))))

          (testing "resolve parameters"
            (let [expr (c/resolve-params expr {})]
              (has-form expr '(:identifier (expr-ref "Patient")))))

          (testing "form"
            (has-form expr '(:identifier (expr-ref "Patient"))))))

      (testing "without source-type"
        (let [library {:statements {:def [{:type "ExpressionDef"
                                           :name "Patient"}]}}
              elm
              #elm/source-property [#elm/expression-ref "Patient" "identifier"]
              identifier
              #fhir/Identifier
               {:system #fhir/uri"foo"
                :value "bar"}
              source
              {:fhir/type :fhir/Patient :id "0"
               :identifier [identifier]}
              expr (c/compile {:library library :eval-context "Patient"} elm)
              expr-def {:type "ExpressionDef"
                        :context "Patient"
                        :name "Patient"
                        :expression source}]

          (testing "eval"
            (is (= identifier (coll/first (core/-eval expr {:expression-defs {"Patient" expr-def}} nil nil)))))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (ctu/testing-constant-attach-cache expr)

          (ctu/testing-constant-patient-count expr)

          (testing "resolve expression references"
            (let [expr (c/resolve-refs expr {"Patient" expr-def})]
              (has-form expr (list :identifier source))))

          (testing "resolve parameters"
            (let [expr (c/resolve-params expr {})]
              (has-form expr '(:identifier (expr-ref "Patient")))))

          (testing "form"
            (has-form expr '(:identifier (expr-ref "Patient")))))))

    (testing "Patient.gender"
      (testing "with source-type"
        (let [library {:statements {:def [{:type "ExpressionDef"
                                           :name "Patient"}]}}
              elm
              #elm/source-property [#elm/expression-ref "Patient" "gender"]
              source
              {:fhir/type :fhir/Patient :id "0"
               :gender #fhir/code"male"}
              expr (c/compile {:library library :eval-context "Patient"} elm)
              expr-def {:type "ExpressionDef"
                        :context "Patient"
                        :name "Patient"
                        :expression source}]

          (testing "eval"
            (is (= #fhir/code"male" (core/-eval expr {:expression-defs {"Patient" expr-def}} nil nil))))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (ctu/testing-constant-attach-cache expr)

          (ctu/testing-constant-patient-count expr)

          (testing "resolve expression references"
            (let [expr (c/resolve-refs expr {"Patient" expr-def})]
              (has-form expr (list :gender source))))

          (testing "resolve parameters"
            (let [expr (c/resolve-params expr {})]
              (has-form expr '(:gender (expr-ref "Patient")))))

          (testing "form"
            (has-form expr '(:gender (expr-ref "Patient"))))))

      (testing "without source-type"
        (let [library {:statements {:def [{:type "ExpressionDef"
                                           :name "Patient"}]}}
              elm
              #elm/source-property [#elm/expression-ref "Patient" "gender"]
              source
              {:fhir/type :fhir/Patient :id "0"
               :gender #fhir/code"male"}
              expr (c/compile {:library library :eval-context "Patient"} elm)
              expr-def {:type "ExpressionDef"
                        :context "Patient"
                        :name "Patient"
                        :expression source}]

          (testing "eval"
            (is (= #fhir/code"male" (core/-eval expr {:expression-defs {"Patient" expr-def}} nil nil))))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (ctu/testing-constant-attach-cache expr)

          (ctu/testing-constant-patient-count expr)

          (testing "resolve expression references"
            (let [expr (c/resolve-refs expr {"Patient" expr-def})]
              (has-form expr (list :gender source))))

          (testing "resolve parameters"
            (let [expr (c/resolve-params expr {})]
              (has-form expr '(:gender (expr-ref "Patient")))))

          (testing "form"
            (has-form expr '(:gender (expr-ref "Patient")))))))

    (testing "Patient.birthDate.value"
      (let [library {:statements {:def [{:type "ExpressionDef"
                                         :name "Patient"}]}}
            elm
            #elm/source-property [#elm/expression-ref "Patient" "birthDate.value"]
            source
            (fn [x]
              {:fhir/type :fhir/Patient :id "0"
               :birthDate x})
            expr (c/compile {:library library :eval-context "Patient"} elm)]

        (testing "eval"
          (are [birth-date res] (= res (core/-eval expr {:expression-defs {"Patient" {:expression (source birth-date)}}} nil nil))
            #fhir/date"2023-05-07" #system/date"2023-05-07"
            #fhir/date{:id "foo" :value "2023-05-07"} #system/date"2023-05-07"
            #fhir/date{:id "foo"} nil
            #fhir/date{:extension [#fhir/Extension{:url "foo"}]} nil))

        (testing "expression is dynamic"
          (is (false? (core/-static expr))))

        (ctu/testing-constant-attach-cache expr)

        (ctu/testing-constant-patient-count expr)

        (testing "resolve expression references"
          (let [expr-def {:type "ExpressionDef"
                          :context "Patient"
                          :name "Patient"
                          :expression (source #fhir/date"2023-05-07")}
                expr (c/resolve-refs expr {"Patient" expr-def})]
            (has-form expr (list :value (list :birthDate (source #fhir/date"2023-05-07"))))))

        (testing "resolve parameters"
          (let [expr (c/resolve-params expr {})]
            (has-form expr '(:value (:birthDate (expr-ref "Patient"))))))

        (testing "form"
          (has-form expr '(:value (:birthDate (expr-ref "Patient")))))))

    (testing "Observation.value"
      (testing "with source-type"
        (let [library {:statements {:def [{:type "ExpressionDef"
                                           :name "Observation"}]}}
              elm
              #elm/source-property [#elm/expression-ref "Observation" "value"]
              source
              {:fhir/type :fhir/Observation :id "0"
               :value "value-114318"}
              expr (c/compile {:library library :eval-context "Patient"} elm)
              expr-def {:type "ExpressionDef"
                        :context "Patient"
                        :name "Observation"
                        :expression source}]

          (testing "eval"
            (is (= "value-114318" (core/-eval expr {:expression-defs {"Observation" expr-def}} nil nil))))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (ctu/testing-constant-attach-cache expr)

          (ctu/testing-constant-patient-count expr)

          (testing "resolve expression references"
            (let [expr (c/resolve-refs expr {"Observation" expr-def})]
              (has-form expr (list :value source))))

          (testing "resolve parameters"
            (let [expr (c/resolve-params expr {})]
              (has-form expr '(:value (expr-ref "Observation")))))

          (testing "form"
            (has-form expr '(:value (expr-ref "Observation"))))))

      (testing "without source-type"
        (let [library {:statements {:def [{:type "ExpressionDef"
                                           :name "Observation"}]}}
              elm
              #elm/source-property [#elm/expression-ref "Observation" "value"]
              source
              {:fhir/type :fhir/Observation :id "0"
               :value "value-114318"}
              expr (c/compile {:library library :eval-context "Patient"} elm)
              expr-def {:type "ExpressionDef"
                        :context "Patient"
                        :name "Observation"
                        :expression source}]

          (testing "eval"
            (is (= "value-114318" (core/-eval expr {:expression-defs {"Observation" expr-def}} nil nil))))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (ctu/testing-constant-attach-cache expr)

          (ctu/testing-constant-patient-count expr)

          (testing "resolve expression references"
            (let [expr (c/resolve-refs expr {"Observation" expr-def})]
              (has-form expr (list :value source))))

          (testing "resolve parameters"
            (let [expr (c/resolve-params expr {})]
              (has-form expr '(:value (expr-ref "Observation")))))

          (testing "form"
            (has-form expr '(:value (expr-ref "Observation")))))))

    (testing "Tuple"
      (are [elm result] (= result (c/compile {} elm))
        #elm/source-property[#elm/tuple {"id" #elm/integer "1"} "id"] 1
        #elm/source-property[#elm/tuple {"id" #elm/integer "2"} "id"] 2
        #elm/source-property[#elm/tuple {"x" #elm/integer "3"} "x"] 3))

    (testing "Concept"
      (let [context {:library
                     {:codeSystems
                      {:def
                       [{:name "sys-def-131750"
                         :id "system-192253"}]}}}]
        (are [elm result] (= result (c/compile context elm))
          #elm/source-property
           [#elm/concept
             [[#elm/code ["sys-def-131750" "code-192300"]
               #elm/code ["sys-def-131750" "code-140541"]]]
            "codes"]
          [(code "system-192253" nil "code-192300")
           (code "system-192253" nil "code-140541")])))

    (testing "Quantity"
      (testing "value"
        (are [elm result] (= result (c/compile {} elm))
          #elm/source-property [#elm/quantity [42 "m"] "value"]
          42M))

      (testing "unit"
        (are [elm result] (= result (c/compile {} elm))
          #elm/source-property [#elm/quantity [42 "m"] "unit"]
          "m")))

    (testing "nil"
      (are [elm result] (= result (c/compile {} elm))
        #elm/source-property [{:type "Null"} "value"]
        nil))))
