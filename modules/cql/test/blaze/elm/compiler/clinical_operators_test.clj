(ns blaze.elm.compiler.clinical-operators-test
  "23. Clinical Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-system with-system-data]]
   [blaze.elm.code :as code]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler.clinical-operators]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.core-spec]
   [blaze.elm.compiler.test-util :as ctu :refer [has-form]]
   [blaze.elm.concept :as concept]
   [blaze.elm.expression :as expr]
   [blaze.elm.expression-spec]
   [blaze.elm.literal :as elm]
   [blaze.elm.literal-spec]
   [blaze.elm.value-set-spec]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service-spec]
   [blaze.terminology-service.protocols :as p]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [cognitect.anomalies :as anom]
   [java-time.api :as time]
   [juxt.iota :refer [given]]))

(st/instrument)
(ctu/instrument-compile)

(defn- fixture [f]
  (st/instrument)
  (ctu/instrument-compile)
  (f)
  (st/unstrument))

(test/use-fixtures :each fixture)

;; TODO 23.1. AnyInCodeSystem

;; TODO 23.2. AnyInValueSet

;; 23.3. CalculateAge
;;
;; Normalized to CalculateAgeAt
(deftest compile-calculate-age-test
  (ctu/unsupported-unary-operand "CalculateAge"))

;; 23.4. CalculateAgeAt
;;
;; Calculates the age in the specified precision of a person born on a given
;; date, as of another given date.
;;
;; The CalculateAgeAt operator has two signatures: (Date, Date) (DateTime,
;; DateTime)
;;
;; For the Date overload, precision must be one of year, month, week, or day,
;; and the result is the number of whole calendar periods that have elapsed
;; between the first date and the second date.
;;
;; For the DateTime overload, the result is the number of whole calendar periods
;; that have elapsed between the first datetime and the second datetime.
(deftest compile-calculate-age-at-test
  (testing "Static"
    (are [elm res] (= res (core/-eval (c/compile {} elm) {:now ctu/now} nil nil))
      #elm/calculate-age-at [#elm/date"2018" #elm/date"2019" "year"] 1
      #elm/calculate-age-at [#elm/date"2018" #elm/date"2018" "year"] 0
      #elm/calculate-age-at [#elm/date"2018" #elm/date"2019" "month"] nil

      #elm/calculate-age-at [#elm/date"2018-01" #elm/date"2019-02" "year"] 1
      #elm/calculate-age-at [#elm/date"2018-01" #elm/date"2018-12" "year"] 0
      #elm/calculate-age-at [#elm/date"2018-01" #elm/date"2018-12" "month"] 11
      #elm/calculate-age-at [#elm/date"2018-01" #elm/date"2018-12" "day"] nil

      #elm/calculate-age-at [#elm/date"2018-01-01" #elm/date"2019-02-02" "year"] 1
      #elm/calculate-age-at [#elm/date"2018-01" #elm/date"2018-12-15" "year"] 0
      #elm/calculate-age-at [#elm/date"2018-01-01" #elm/date"2018-12-02" "month"] 11
      #elm/calculate-age-at [#elm/date"2018-01-01" #elm/date"2018-02-01" "day"] 31

      #elm/calculate-age-at [#elm/date-time"2018-01-01" #elm/date-time"2018-02-01" "day"] 31))

  (ctu/testing-binary-null elm/calculate-age-at #elm/date"2018")
  (ctu/testing-binary-null elm/calculate-age-at #elm/date-time"2018-01-01")

  (ctu/testing-binary-precision-dynamic elm/calculate-age-at "year" "month" "day")

  (ctu/testing-binary-precision-attach-cache elm/calculate-age-at "year" "month" "day")

  (ctu/testing-binary-precision-patient-count elm/calculate-age-at "year" "month" "day")

  (ctu/testing-binary-precision-resolve-refs elm/calculate-age-at "year" "month" "day")

  (ctu/testing-binary-precision-resolve-params elm/calculate-age-at "year" "month" "day")

  (ctu/testing-binary-precision-optimize elm/calculate-age-at "year" "month" "day")

  (ctu/testing-binary-precision-equals-hash-code elm/calculate-age-at "year" "month" "day")

  (ctu/testing-binary-precision-form elm/calculate-age-at "year" "month" "day"))

;; 23.5. Equal

;; 23.6. Equivalent

;; 23.7. InCodeSystem
;;
;; The InCodeSystem operator returns true if the given code is in the given code
;; system.
;;
;; The first argument is expected to be a String, Code, or Concept.
;;
;; Note that this operator explicitly requires a CodeSystemRef as its codesystem
;; argument. This allows for both static analysis of the code system references
;; within an artifact, as well as the implementation of code system membership
;; by the target environment as a service call to a terminology server, if
;; desired. 
;; 
;; The third argument is expected to be a CodeSystem, allowing references to 
;; code systems to be preserved as references.
(defn- eval-context [db]
  {:db db :now (time/offset-date-time)
   :parameters {"nil" nil "code-115927" "code-115927"}})

(deftest compile-in-code-system-test
  (ctu/testing-binary-attach-cache elm/in-code-system-expression)

  (ctu/testing-binary-patient-count elm/in-code-system-expression)

  (ctu/testing-binary-resolve-refs elm/in-code-system-expression "in-code-system")

  (ctu/testing-binary-optimize elm/in-code-system-expression "in-code-system")

  (ctu/testing-binary-equals-hash-code elm/in-code-system-expression)

  (doseq [elm-constructor [elm/in-code-system #_elm/in-code-system-expression]]
    (testing "Null"
      (with-system [{:blaze.db/keys [node] terminology-service ::ts/local} api-stub/mem-node-config]
        (let [context
              {:library
               {:parameters
                {:def
                 [{:name "nil"}]}
                :codeSystems
                {:def
                 [{:name "code-system-def-135520"
                   :id "code-system-135750"}]}}
               :terminology-service terminology-service}
              db (d/db node)]

          (testing "Static"
            (let [elm (elm-constructor [{:type "Null"}
                                        #elm/code-system-ref "code-system-def-135520"])
                  expr (c/compile context elm)]

              (testing "eval"
                (is (false? (expr/eval (eval-context db) expr nil))))

              (ctu/testing-constant expr)))

          (testing "Dynamic"
            (let [elm (elm-constructor [#elm/parameter-ref "nil"
                                        #elm/code-system-ref "code-system-def-135520"])
                  expr (c/compile context elm)]

              (testing "eval"
                (is (false? (expr/eval (eval-context db) expr nil)))))))))

    (testing "String"
      (with-system-data [{:blaze.db/keys [node] terminology-service ::ts/local} api-stub/mem-node-config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-105600"}]}]]]

        (let [context
              {:library
               {:parameters
                {:def
                 [{:name "code-115927"}]}
                :codeSystems
                {:def
                 [{:name "code-system-def-135520"
                   :id "system-115910"}]}}
               :terminology-service terminology-service}
              db (d/db node)]

          (testing "Static"
            (let [elm (elm-constructor [#elm/string "code-115927"
                                        #elm/code-system-ref "code-system-def-135520"])
                  expr (c/compile context elm)]

              (testing "eval"
                (is (true? (expr/eval (eval-context db) expr nil))))

              (testing "expression is dynamic"
                (is (false? (core/-static expr))))

              (testing "attach cache"
                (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
                  count := 2
                  [0] := expr
                  [1] :? empty?))

              (ctu/testing-constant-patient-count expr)

              (ctu/testing-constant-resolve-refs expr)

              (ctu/testing-constant-resolve-params expr)

              (ctu/testing-constant-optimize expr)

              (testing "form"
                (has-form expr
                  '(in-code-system
                    "code-115927"
                    (code-system "system-115910"))))))

          (testing "Dynamic"
            (let [elm (elm-constructor [#elm/parameter-ref "code-115927"
                                        #elm/code-system-ref "code-system-def-135520"])
                  expr (c/compile context elm)]

              (testing "eval"
                (is (true? (expr/eval (eval-context db) expr nil))))))))

      (testing "with failing terminology service"
        (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
          (let [context
                {:library
                 {:parameters
                  {:def
                   [{:name "code-115927"}]}
                  :codeSystems
                  {:def
                   [{:name "code-system-def-135520"
                     :id "code-system-135750"}]}}
                 :terminology-service
                 (reify p/TerminologyService
                   (-code-system-validate-code [_ _]
                     (ac/completed-future (ba/fault "msg-094502"))))}
                db (d/db node)]

            (testing "Static"
              (let [elm (elm-constructor [#elm/string "code-115927"
                                          #elm/code-system-ref "code-system-def-135520"])
                    expr (c/compile context elm)]

                (testing "eval"
                  (given (ba/try-anomaly (expr/eval (eval-context db) expr nil))
                    ::anom/category := ::anom/fault
                    ::anom/message := "Error while testing that the code `code-115927` is in CodeSystem `code-system-135750`. Cause: msg-094502"))

                (testing "expression is dynamic"
                  (is (false? (core/-static expr))))

                (testing "attach cache"
                  (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
                    count := 2
                    [0] := expr
                    [1] :? empty?))

                (ctu/testing-constant-patient-count expr)

                (ctu/testing-constant-resolve-refs expr)

                (ctu/testing-constant-resolve-params expr)

                (ctu/testing-constant-optimize expr)

                (testing "form"
                  (has-form expr
                    '(in-code-system
                      "code-115927"
                      (code-system "code-system-135750"))))))

            (testing "Dynamic"
              (let [elm (elm-constructor [#elm/parameter-ref "code-115927"
                                          #elm/code-system-ref "code-system-def-135520"])
                    expr (c/compile context elm)]

                (testing "eval"
                  (given (ba/try-anomaly (expr/eval (eval-context db) expr nil))
                    ::anom/category := ::anom/fault
                    ::anom/message := "Error while testing that the code `code-115927` is in CodeSystem `code-system-135750`. Cause: msg-094502"))))))))

    (testing "Code"
      (with-system-data [{:blaze.db/keys [node] terminology-service ::ts/local} api-stub/mem-node-config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-105600"}]}]]]

        (let [context
              {:library
               {:codeSystems
                {:def [{:name "code-system-def-134106"
                        :id "system-115910"}]}}
               :terminology-service terminology-service}
              elm (elm-constructor [#elm/code ["code-system-def-134106" "code-115927"]
                                    #elm/code-system-ref "code-system-def-134106"])
              expr (c/compile context elm)
              db (d/db node)]

          (testing "eval"
            (is (true? (expr/eval (eval-context db) expr nil))))

          (testing "form"
            (has-form expr
              '(in-code-system
                (code "system-115910" nil "code-115927")
                (code-system "system-115910")))))))

    (testing "Concept"
      (with-system-data [{:blaze.db/keys [node] terminology-service ::ts/local} api-stub/mem-node-config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-105600"}]}]]]

        (let [context
              {:library
               {:parameters
                {:def
                 [{:name "concept"}]}
                :codeSystems
                {:def [{:name "code-system-def-135520"
                        :id "system-115910"
                        :version "version-132113"}]}}
               :terminology-service terminology-service}
              db (d/db node)]

          (testing "Static"
            (let [elm (elm-constructor [#elm/concept [[#elm/code ["code-system-def-135520" "code-115927"]]]
                                        #elm/code-system-ref "code-system-def-135520"])
                  expr (c/compile context elm)]

              (testing "eval"
                (is (true? (expr/eval (eval-context db) expr nil))))

              (testing "form"
                (has-form expr
                  '(in-code-system
                    (concept (code "system-115910" "version-132113" "code-115927"))
                    (code-system "system-115910" "version-132113"))))))

          (testing "Dynamic"
            (let [elm (elm-constructor [#elm/parameter-ref "concept"
                                        #elm/code-system-ref "code-system-def-135520"])
                  expr (c/compile context elm)]

              (testing "eval"
                (testing "true"
                  (let [eval-ctx (assoc-in (eval-context db) [:parameters "concept"]
                                           (concept/concept [(code/code "system-115910" "version-132113" "code-115927")]))]
                    (is (true? (expr/eval eval-ctx expr nil)))))

                (testing "false"
                  (let [eval-ctx (assoc-in (eval-context db) [:parameters "concept"]
                                           (concept/concept [(code/code "system-115910" "version-132113" "code-135028")]))]
                    (is (false? (expr/eval eval-ctx expr nil)))))))))))))

;; 23.8. InValueSet
;;
;; The InValueSet operator returns true if the given code is in the given value
;; set.
;;
;; The first argument is expected to be a String, Code, or Concept.
;;
;; The second argument is statically a ValueSetRef. This allows for both static
;; analysis of the value set references within an artifact, as well as the
;; implementation of value set membership by the target environment as a service
;; call to a terminology server, if desired.
;;
;; The third argument is expected to be a ValueSet, allowing references to value
;; sets to be preserved as references.
(deftest compile-in-value-set-test
  (ctu/testing-binary-attach-cache elm/in-value-set-expression)

  (ctu/testing-binary-patient-count elm/in-value-set-expression)

  (ctu/testing-binary-resolve-refs elm/in-value-set-expression "in-value-set")

  (ctu/testing-binary-optimize elm/in-value-set-expression "in-value-set")

  (ctu/testing-binary-equals-hash-code elm/in-value-set-expression)

  (doseq [elm-constructor [elm/in-value-set elm/in-value-set-expression]]
    (testing "Null"
      (with-system [{:blaze.db/keys [node] terminology-service ::ts/local} api-stub/mem-node-config]
        (let [context
              {:library
               {:parameters
                {:def
                 [{:name "nil"}]}
                :valueSets
                {:def
                 [{:name "value-set-def-135520"
                   :id "value-set-135750"}]}}
               :terminology-service terminology-service}
              db (d/db node)]

          (testing "Static"
            (let [elm (elm-constructor [{:type "Null"}
                                        #elm/value-set-ref "value-set-def-135520"])
                  expr (c/compile context elm)]

              (testing "eval"
                (is (false? (expr/eval (eval-context db) expr nil))))

              (ctu/testing-constant expr)))

          (testing "Dynamic"
            (let [elm (elm-constructor [#elm/parameter-ref "nil"
                                        #elm/value-set-ref "value-set-def-135520"])
                  expr (c/compile context elm)]

              (testing "eval"
                (is (false? (expr/eval (eval-context db) expr nil)))))))))

    (testing "String"
      (with-system-data [{:blaze.db/keys [node] terminology-service ::ts/local} api-stub/mem-node-config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-105600"}]}]
          [:put {:fhir/type :fhir/CodeSystem :id "1"
                 :url #fhir/uri "system-191928"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-115910"}]}}]
          [:put {:fhir/type :fhir/ValueSet :id "1"
                 :url #fhir/uri "value-set-191950"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-115910"}
                   {:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-191928"}]}}]]]

        (let [context
              {:library
               {:parameters
                {:def
                 [{:name "code-115927"}]}
                :valueSets
                {:def
                 [{:name "value-set-def-135520"
                   :id "value-set-135750"}]}}
               :terminology-service terminology-service}
              db (d/db node)]

          (testing "Static"
            (let [elm (elm-constructor [#elm/string "code-115927"
                                        #elm/value-set-ref "value-set-def-135520"])
                  expr (c/compile context elm)]

              (testing "eval"
                (is (true? (expr/eval (eval-context db) expr nil))))

              (testing "expression is dynamic"
                (is (false? (core/-static expr))))

              (testing "attach cache"
                (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
                  count := 2
                  [0] := expr
                  [1] :? empty?))

              (ctu/testing-constant-patient-count expr)

              (ctu/testing-constant-resolve-refs expr)

              (ctu/testing-constant-resolve-params expr)

              (ctu/testing-constant-optimize expr)

              (testing "form"
                (has-form expr
                  '(in-value-set
                    "code-115927"
                    (value-set "value-set-135750"))))))

          (testing "Dynamic"
            (let [elm (elm-constructor [#elm/parameter-ref "code-115927"
                                        #elm/value-set-ref "value-set-def-135520"])
                  expr (c/compile context elm)]

              (testing "eval"
                (is (true? (expr/eval (eval-context db) expr nil))))))))

      (testing "with failing terminology service"
        (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
          (let [context
                {:library
                 {:parameters
                  {:def
                   [{:name "code-115927"}]}
                  :valueSets
                  {:def
                   [{:name "value-set-def-135520"
                     :id "value-set-135750"}]}}
                 :terminology-service
                 (reify p/TerminologyService
                   (-value-set-validate-code [_ _]
                     (ac/completed-future (ba/fault "msg-094502"))))}
                db (d/db node)]

            (testing "Static"
              (let [elm (elm-constructor [#elm/string "code-115927"
                                          #elm/value-set-ref "value-set-def-135520"])
                    expr (c/compile context elm)]

                (testing "eval"
                  (given (ba/try-anomaly (expr/eval (eval-context db) expr nil))
                    ::anom/category := ::anom/fault
                    ::anom/message := "Error while testing that the code `code-115927` is in ValueSet `value-set-135750`. Cause: msg-094502"))

                (testing "expression is dynamic"
                  (is (false? (core/-static expr))))

                (testing "attach cache"
                  (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
                    count := 2
                    [0] := expr
                    [1] :? empty?))

                (ctu/testing-constant-patient-count expr)

                (ctu/testing-constant-resolve-refs expr)

                (ctu/testing-constant-resolve-params expr)

                (ctu/testing-constant-optimize expr)

                (testing "form"
                  (has-form expr
                    '(in-value-set
                      "code-115927"
                      (value-set "value-set-135750"))))))

            (testing "Dynamic"
              (let [elm (elm-constructor [#elm/parameter-ref "code-115927"
                                          #elm/value-set-ref "value-set-def-135520"])
                    expr (c/compile context elm)]

                (testing "eval"
                  (given (ba/try-anomaly (expr/eval (eval-context db) expr nil))
                    ::anom/category := ::anom/fault
                    ::anom/message := "Error while testing that the code `code-115927` is in ValueSet `value-set-135750`. Cause: msg-094502"))))))))

    (testing "Code"
      (with-system-data [{:blaze.db/keys [node] terminology-service ::ts/local} api-stub/mem-node-config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-105600"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-115910"}]}}]]]

        (let [context
              {:library
               {:codeSystems
                {:def [{:name "sys-def-115852" :id "system-115910"}]}
                :valueSets
                {:def
                 [{:name "value-set-def-135520"
                   :id "value-set-135750"}]}}
               :terminology-service terminology-service}
              elm (elm-constructor [#elm/code ["sys-def-115852" "code-115927"]
                                    #elm/value-set-ref "value-set-def-135520"])
              expr (c/compile context elm)
              db (d/db node)]

          (testing "eval"
            (is (true? (expr/eval (eval-context db) expr nil))))

          (testing "form"
            (has-form expr
              '(in-value-set
                (code "system-115910" nil "code-115927")
                (value-set "value-set-135750")))))))

    (testing "Concept"
      (with-system-data [{:blaze.db/keys [node] terminology-service ::ts/local} api-stub/mem-node-config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-105600"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-115910"}]}}]]]

        (let [context
              {:library
               {:parameters
                {:def
                 [{:name "concept"}]}
                :codeSystems
                {:def [{:name "sys-def-115852" :id "system-115910" :version "version-132113"}]}
                :valueSets
                {:def
                 [{:name "value-set-def-135520"
                   :id "value-set-135750"}]}}
               :terminology-service terminology-service}
              db (d/db node)]

          (testing "Static"
            (let [elm (elm-constructor [#elm/concept [[#elm/code ["sys-def-115852" "code-115927"]]]
                                        #elm/value-set-ref "value-set-def-135520"])
                  expr (c/compile context elm)]

              (testing "eval"
                (is (true? (expr/eval (eval-context db) expr nil))))

              (testing "form"
                (has-form expr
                  '(in-value-set
                    (concept (code "system-115910" "version-132113" "code-115927"))
                    (value-set "value-set-135750"))))))

          (testing "Dynamic"
            (let [elm (elm-constructor [#elm/parameter-ref "concept"
                                        #elm/value-set-ref "value-set-def-135520"])
                  expr (c/compile context elm)
                  eval-ctx (assoc-in (eval-context db) [:parameters "concept"]
                                     (concept/concept [(st/with-instrument-disabled (code/code nil nil nil))]))]

              (testing "eval"
                (is (false? (expr/eval eval-ctx expr nil)))))))))))

;; 23.9. Not Equal

;; TODO 23.10. SubsumedBy

;; TODO 23.11. Subsumes
