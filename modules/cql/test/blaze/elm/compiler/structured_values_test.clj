(ns blaze.elm.compiler.structured-values-test
  "2. Structured Values

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.anomaly :as ba]
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
   [blaze.elm.protocols :as p]
   [blaze.fhir.spec.type :as type]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]])
  (:import
   [blaze.elm.code Code]
   [java.time Instant]))

(st/instrument)
(ctu/instrument-compile)

(defn- fixture [f]
  (st/instrument)
  (ctu/instrument-compile)
  (f)
  (st/unstrument))

(test/use-fixtures :each fixture)

(deftest get-test
  (are [value] (= value (p/get (type/boolean value) :value))
    true
    false)

  (are [value] (= value (p/get (type/boolean {:id "foo" :value value}) :value))
    true
    false)

  (is (= 1 (p/get #fhir/integer 1 :value)))

  (is (= "value-172719" (p/get #fhir/string "value-172719" :value)))

  (is (= 1M (p/get #fhir/decimal 1M :value)))

  (is (= "value-170022" (p/get #fhir/uri "value-170022" :value)))

  (is (= "value-170031" (p/get #fhir/url "value-170031" :value)))

  (is (= "value-170723" (p/get #fhir/canonical "value-170723" :value)))

  (is (= "value-170805" (p/get #fhir/base64Binary "value-170805" :value)))

  (is (= Instant/EPOCH (p/get #fhir/instant "1970-01-01T00:00:00Z" :value)))

  (is (= #system/date"2025" (p/get #fhir/date "2025" :value)))

  (is (= #system/date"2025-04" (p/get #fhir/date "2025-04" :value)))

  (is (= #system/date"2025-04-09" (p/get #fhir/date "2025-04-09" :value)))

  (is (= #system/date-time"2025" (p/get #fhir/dateTime "2025" :value)))

  (is (= #system/date-time"2025-04" (p/get #fhir/dateTime "2025-04" :value)))

  (is (= #system/date-time"2025-04-09" (p/get #fhir/dateTime "2025-04-09" :value)))

  (is (= #system/date-time"2025-04-09T12:34:56" (p/get #fhir/dateTime "2025-04-09T12:34:56" :value)))

  (is (= #system/date-time"2025-04-09T12:34:56Z" (p/get #fhir/dateTime "2025-04-09T12:34:56Z" :value)))

  (is (= #system/date-time"2025-04-09T12:34:56+01:00" (p/get #fhir/dateTime "2025-04-09T12:34:56+01:00" :value)))

  (is (= #system/time"17:20:08" (p/get #fhir/time "17:20:08" :value)))

  (is (= "value-165314" (p/get #fhir/code "value-165314" :value)))

  (is (= "value-165314" (p/get #fhir/code{:id "foo" :value "value-165314"} :value)))

  (is (= "value-172210" (p/get #fhir/oid "value-172210" :value)))

  (is (= "value-172229" (p/get #fhir/id "value-172229" :value)))

  (is (= "value-172243" (p/get #fhir/markdown "value-172243" :value)))

  (is (= 1 (p/get #fhir/unsignedInt 1 :value)))

  (is (= 1 (p/get #fhir/positiveInt 1 :value)))

  (is (= #uuid"6a989368-0d9a-48b0-8bdb-5b61e29f9b39" (p/get #fhir/uuid "urn:uuid:6a989368-0d9a-48b0-8bdb-5b61e29f9b39" :value))))

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
    (testing "one element"
      (let [elm #elm/tuple{"id" #elm/parameter-ref "1"}
            expr (ctu/dynamic-compile elm)]

        (testing "eval"
          (is (= {:id 1} (core/-eval expr ctu/dynamic-eval-ctx nil nil))))

        (testing "expression is dynamic"
          (is (false? (core/-static expr))))

        (ctu/testing-constant-attach-cache expr)

        (ctu/testing-constant-patient-count expr)

        (ctu/testing-constant-resolve-refs expr)

        (ctu/testing-constant-resolve-params expr)

        (ctu/testing-constant-optimize expr)

        (testing "form"
          (has-form expr '{:id (param-ref "1")}))))

    (testing "two elements"
      (let [elm #elm/tuple{"id" #elm/parameter-ref "1" "name" #elm/parameter-ref "a"}
            expr (ctu/dynamic-compile elm)]

        (testing "eval"
          (is (= {:id 1 :name "a"} (core/-eval expr ctu/dynamic-eval-ctx nil nil))))

        (testing "expression is dynamic"
          (is (false? (core/-static expr))))

        (ctu/testing-constant-attach-cache expr)

        (ctu/testing-constant-patient-count expr)

        (ctu/testing-constant-resolve-refs expr)

        (ctu/testing-constant-resolve-params expr)

        (ctu/testing-constant-optimize expr)

        (testing "form"
          (has-form expr '{:id (param-ref "1") :name (param-ref "a")}))))))

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
      :code := "code-134551"))

  (testing "Dynamic"
    (testing "unsupported type namespace"
      (given (ba/try-anomaly (ctu/dynamic-compile #elm/instance["{foo}Bar" {"x" #elm/parameter-ref "a"}]))
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported type namespace `foo` in instance expression."))

    (testing "unsupported type"
      (given (ba/try-anomaly (ctu/dynamic-compile #elm/instance["{http://hl7.org/fhir}Foo" {"x" #elm/parameter-ref "a"}]))
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported type `Foo` in instance expression."))

    (testing "CodeableConcept"
      (let [elm #elm/instance["{http://hl7.org/fhir}CodeableConcept" {"coding" #elm/list [#elm/parameter-ref "coding"]}]
            expr (ctu/dynamic-compile elm)]

        (testing "eval"
          (is (= (core/-eval expr ctu/dynamic-eval-ctx nil nil)
                 #fhir/CodeableConcept{:coding [#fhir/Coding{:system #fhir/uri "foo" :code #fhir/code "bar"}]})))

        (testing "expression is dynamic"
          (is (false? (core/-static expr))))

        (ctu/testing-constant-attach-cache expr)

        (ctu/testing-constant-patient-count expr)

        (ctu/testing-constant-resolve-refs expr)

        (ctu/testing-constant-resolve-params expr)

        (ctu/testing-constant-optimize expr)

        (testing "form"
          (has-form expr '("CodeableConcept" {:coding (list (param-ref "coding"))})))))))

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
        (let [identifier
              #fhir/Identifier
               {:system #fhir/uri "foo"
                :value #fhir/string "bar"}
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

          (ctu/testing-constant-optimize expr)

          (ctu/testing-equals-hash-code elm)

          (testing "form"
            (has-form expr '(:identifier R)))))

      (testing "Patient.extension"
        (let [extension
              #fhir/Extension
               {:url "foo"
                :value #fhir/string "bar"}
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

          (ctu/testing-constant-optimize expr)

          (ctu/testing-equals-hash-code elm)

          (testing "form"
            (has-form expr '(:extension R)))))

      (testing "Patient.gender"
        (let [entity
              {:fhir/type :fhir/Patient :id "0"
               :gender #fhir/code "male"}
              elm #elm/scope-property ["R" "gender"]
              expr (c/compile {:eval-context "Patient"} elm)]

          (testing "eval"
            (is (= #fhir/code "male" (core/-eval expr nil nil {"R" entity}))))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (ctu/testing-constant-attach-cache expr)

          (ctu/testing-constant-patient-count expr)

          (ctu/testing-constant-resolve-refs expr)

          (ctu/testing-constant-resolve-params expr)

          (ctu/testing-constant-optimize expr)

          (ctu/testing-equals-hash-code elm)

          (testing "form"
            (has-form expr '(:gender R)))))

      (testing "Patient.birthDate.value"
        (let [entity
              (fn [x]
                {:fhir/type :fhir/Patient :id "0"
                 :birthDate x})
              elm #elm/scope-property ["R" "birthDate.value"]
              expr (c/compile {:eval-context "Patient"} elm)]

          (testing "eval"
            (are [birth-date res] (= res (core/-eval expr nil nil {"R" (entity birth-date)}))
              #fhir/date "2023-05-07" #system/date"2023-05-07"
              #fhir/date{:id "foo" :value #system/date"2023-05-07"} #system/date"2023-05-07"
              #fhir/date{:id "foo"} nil
              #fhir/date{:extension [#fhir/Extension{:url "foo"}]} nil))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (ctu/testing-constant-attach-cache expr)

          (ctu/testing-constant-patient-count expr)

          (ctu/testing-constant-resolve-refs expr)

          (ctu/testing-constant-resolve-params expr)

          (ctu/testing-constant-optimize expr)

          (ctu/testing-equals-hash-code elm)

          (testing "form"
            (has-form expr '(:value (:birthDate R))))))

      (testing "Observation.value"
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

          (ctu/testing-constant-optimize expr)

          (ctu/testing-equals-hash-code elm)

          (testing "form"
            (has-form expr '(:value R)))))))

  (testing "with source"
    (testing "Patient.identifier"
      (let [library {:statements {:def [{:type "ExpressionDef"
                                         :name "Patient"}]}}
            elm
            #elm/source-property [#elm/expression-ref "Patient" "identifier"]
            identifier
            #fhir/Identifier
             {:system #fhir/uri "foo"
              :value #fhir/string "bar"}
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

    (testing "Patient.gender"
      (let [library {:statements {:def [{:type "ExpressionDef"
                                         :name "Patient"}]}}
            elm
            #elm/source-property [#elm/expression-ref "Patient" "gender"]
            source
            {:fhir/type :fhir/Patient :id "0"
             :gender #fhir/code "male"}
            expr (c/compile {:library library :eval-context "Patient"} elm)
            expr-def {:type "ExpressionDef"
                      :context "Patient"
                      :name "Patient"
                      :expression source}]

        (testing "eval"
          (is (= #fhir/code "male" (core/-eval expr {:expression-defs {"Patient" expr-def}} nil nil))))

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

    (testing "Patient.gender.value"
      (let [library {:statements {:def [{:type "ExpressionDef"
                                         :name "Patient"}]}}
            elm
            #elm/source-property [#elm/source-property [#elm/expression-ref "Patient" "gender"] "value"]
            source
            {:fhir/type :fhir/Patient :id "0"
             :gender #fhir/code "male"}
            expr (c/compile {:library library :eval-context "Patient"} elm)
            expr-def {:type "ExpressionDef"
                      :context "Patient"
                      :name "Patient"
                      :expression source}]

        (testing "eval"
          (is (= "male" (core/-eval expr {:expression-defs {"Patient" expr-def}} nil nil))))

        (testing "expression is dynamic"
          (is (false? (core/-static expr))))

        (ctu/testing-constant-attach-cache expr)

        (ctu/testing-constant-patient-count expr)

        (testing "resolve expression references"
          (let [expr (c/resolve-refs expr {"Patient" expr-def})]
            (has-form expr (list :value (list :gender source)))))

        (testing "resolve parameters"
          (let [expr (c/resolve-params expr {})]
            (has-form expr '(:value (:gender (expr-ref "Patient"))))))

        (testing "form"
          (has-form expr '(:value (:gender (expr-ref "Patient")))))))

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
            #fhir/date "2023-05-07" #system/date"2023-05-07"
            #fhir/date{:id "foo" :value #system/date"2023-05-07"} #system/date"2023-05-07"
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
                          :expression (source #fhir/date "2023-05-07")}
                expr (c/resolve-refs expr {"Patient" expr-def})]
            (has-form expr (list :value (list :birthDate (source #fhir/date "2023-05-07"))))))

        (testing "resolve parameters"
          (let [expr (c/resolve-params expr {})]
            (has-form expr '(:value (:birthDate (expr-ref "Patient"))))))

        (testing "form"
          (has-form expr '(:value (:birthDate (expr-ref "Patient")))))))

    (testing "Observation.value"
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

    (testing "Interval"
      (testing "low"
        (are [elm result] (= result (c/compile {} elm))
          #elm/source-property [#elm/interval [#elm/integer "1" #elm/integer "2"] "low"]
          1))

      (testing "high"
        (are [elm result] (= result (c/compile {} elm))
          #elm/source-property [#elm/interval [#elm/integer "1" #elm/integer "2"] "high"]
          2)))

    (testing "nil"
      (are [elm result] (= result (c/compile {} elm))
        #elm/source-property [{:type "Null"} "value"]
        nil))))
