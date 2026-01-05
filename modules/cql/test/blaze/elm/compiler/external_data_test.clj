(ns blaze.elm.compiler.external-data-test
  "11. External Data

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.anomaly :as ba]
   [blaze.cql.translator :as t]
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.external-data]
   [blaze.elm.compiler.library :as library]
   [blaze.elm.compiler.library-spec]
   [blaze.elm.compiler.test-util :as ctu :refer [has-form]]
   [blaze.elm.expression :as expr]
   [blaze.elm.expression-spec]
   [blaze.elm.literal :as elm]
   [blaze.elm.util-spec]
   [blaze.fhir.spec.type]
   [blaze.module.test-util :refer [with-system]]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service-spec]
   [blaze.terminology-service.local :as ts-local]
   [blaze.util-spec]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
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

(def ^:private config
  (assoc
   mem-node-config
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :graph-cache (ig/ref ::ts-local/graph-cache)}
   :blaze.test/fixed-rng-fn {}
   ::ts-local/graph-cache {}))

(defn- compile-context [{:blaze.db/keys [node] ::ts/keys [local]}]
  {:node node :terminology-service local})

(defn- eval-context [db]
  {:db db :now (time/offset-date-time)})

;; 11.1. Retrieve
;;
;; All access to external data within ELM is represented by Retrieve expressions.
;;
;; The Retrieve class defines the data type of the request, which determines the
;; type of elements to be returned. The result will always be a list of values
;; of the type specified in the request.
;;
;; The type of the elements to be returned is specified with the dataType
;; attribute of the Retrieve, and must refer to the name of a type within a
;; known data model specified in the dataModels element of the library
;; definition.
;;
;; In addition, the Retrieve introduces the ability to specify optional criteria
;; for the request. The available criteria are intentionally restricted to the
;; set of codes involved, and the date range involved. If these criteria are
;; omitted, the request is interpreted to mean all data of that type.
;;
;; Note that because every expression is being evaluated within a context (such
;; as Patient, Practitioner, or Unfiltered) as defined by the containing
;; ExpressionDef, the data returned by a retrieve depends on the context. For
;; example, for the Patient context, the data is returned for a single patient
;; only, as defined by the evaluation environment. Whereas for the Unfiltered
;; context, the data is returned for the entire source.
(deftest compile-retrieve-test
  (testing "Patient context"
    (testing "Patient"
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (let [context
              {:node node
               :eval-context "Patient"
               :library {}}
              expr (c/compile context ctu/patient-retrieve-elm)
              db (d/db node)
              patient (ctu/resource db "Patient" "0")]

          (testing "eval"
            (given (expr/eval (eval-context db) expr patient)
              [0 :fhir/type] := :fhir/Patient
              [0 :id] := "0"))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (ctu/testing-constant-attach-cache expr)

          (ctu/testing-constant-patient-count expr)

          (ctu/testing-constant-resolve-refs expr)

          (ctu/testing-constant-resolve-params expr)

          (ctu/testing-constant-optimize expr)

          (testing "form"
            (has-form expr '(retrieve-resource))))))

    (testing "Observation"
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "1"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

        (let [context
              {:node node
               :eval-context "Patient"
               :library {}}
              expr (c/compile context #elm/retrieve{:type "Observation"})
              db (d/db node)
              patient (ctu/resource db "Patient" "0")]

          (testing "eval"
            (given (expr/eval (eval-context db) expr patient)
              [0 :fhir/type] := :fhir/Observation
              [0 :id] := "1"))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (ctu/testing-constant-attach-cache expr)

          (ctu/testing-constant-patient-count expr)

          (ctu/testing-constant-resolve-refs expr)

          (ctu/testing-constant-resolve-params expr)

          (ctu/testing-constant-optimize expr)

          (testing "form"
            (has-form expr '(retrieve "Observation")))))

      (testing "with one code"
        (with-system-data [{:blaze.db/keys [node] terminology-service ::ts/local} config]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "0"
                   :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
            [:put {:fhir/type :fhir/Observation :id "1"
                   :code
                   #fhir/CodeableConcept
                    {:coding
                     [#fhir/Coding
                       {:system #fhir/uri "system-192253"
                        :code #fhir/code "code-192300"}]}
                   :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

          (let [context
                {:node node
                 :eval-context "Patient"
                 :library
                 {:codeSystems
                  {:def
                   [{:name "sys-def-131750"
                     :id "system-192253"}]}}
                 :terminology-service terminology-service}
                elm #elm/retrieve
                     {:type "Observation"
                      :codes #elm/list [#elm/code ["sys-def-131750"
                                                   "code-192300"]]}
                expr (c/compile context elm)
                db (d/db node)
                patient (ctu/resource db "Patient" "0")]

            (testing "eval"
              (given (expr/eval (eval-context db) expr patient)
                count := 1
                [0 :fhir/type] := :fhir/Observation
                [0 :id] := "1"))

            (testing "expression is dynamic"
              (is (false? (core/-static expr))))

            (ctu/testing-constant-attach-cache expr)

            (ctu/testing-constant-patient-count expr)

            (ctu/testing-constant-resolve-refs expr)

            (ctu/testing-constant-resolve-params expr)

            (testing "optimize"
              (is (= expr (c/optimize expr db))))

            (testing "form"
              (has-form expr
                '(retrieve "Observation" [["code" "system-192253|code-192300"]])))))

        (testing "optimizing into an empty list because Observation isn't available"
          (with-system [{:blaze.db/keys [node] terminology-service ::ts/local} config]
            (let [context
                  {:node node
                   :eval-context "Patient"
                   :library
                   {:codeSystems
                    {:def
                     [{:name "sys-def-131750"
                       :id "system-192253"}]}}
                   :terminology-service terminology-service}
                  elm #elm/retrieve
                       {:type "Observation"
                        :codes #elm/list [#elm/code ["sys-def-131750"
                                                     "code-192300"]]}
                  expr (c/compile context elm)
                  db (d/db node)]

              (has-form (c/optimize expr db) [])))))

      (testing "with two codes"
        (with-system-data [{:blaze.db/keys [node] terminology-service ::ts/local} config]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "0"
                   :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
            [:put {:fhir/type :fhir/Observation :id "1"
                   :code
                   #fhir/CodeableConcept
                    {:coding
                     [#fhir/Coding
                       {:system #fhir/uri "system-192253"
                        :code #fhir/code "code-192300"}]}
                   :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
            [:put {:fhir/type :fhir/Observation :id "2"
                   :code
                   #fhir/CodeableConcept
                    {:coding
                     [#fhir/Coding
                       {:system #fhir/uri "system-192253"
                        :code #fhir/code "code-140541"}]}
                   :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

          (let [context
                {:node node
                 :eval-context "Patient"
                 :library
                 {:codeSystems
                  {:def
                   [{:name "sys-def-131750"
                     :id "system-192253"}]}}
                 :terminology-service terminology-service}
                elm #elm/retrieve
                     {:type "Observation"
                      :codes
                      #elm/list [#elm/code ["sys-def-131750" "code-192300"]
                                 #elm/code ["sys-def-131750" "code-140541"]]}
                expr (c/compile context elm)
                db (d/db node)
                patient (ctu/resource db "Patient" "0")]

            (testing "eval"
              (given (expr/eval (eval-context db) expr patient)
                count := 2
                [0 :fhir/type] := :fhir/Observation
                [0 :id] := "1"
                [1 :fhir/type] := :fhir/Observation
                [1 :id] := "2"))

            (testing "expression is dynamic"
              (is (false? (core/-static expr))))

            (ctu/testing-constant-attach-cache expr)

            (ctu/testing-constant-patient-count expr)

            (ctu/testing-constant-resolve-refs expr)

            (ctu/testing-constant-resolve-params expr)

            (testing "optimize"
              (is (= expr (c/optimize expr db))))

            (testing "form"
              (has-form expr
                '(retrieve
                  "Observation"
                  [["code"
                    "system-192253|code-192300"
                    "system-192253|code-140541"]]))))))

      (testing "with one concept"
        (with-system-data [{:blaze.db/keys [node]} mem-node-config]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "0"
                   :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
            [:put {:fhir/type :fhir/Observation :id "1"
                   :code
                   #fhir/CodeableConcept
                    {:coding
                     [#fhir/Coding
                       {:system #fhir/uri "system-192253"
                        :code #fhir/code "code-192300"}]}
                   :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
            [:put {:fhir/type :fhir/Observation :id "2"
                   :code
                   #fhir/CodeableConcept
                    {:coding
                     [#fhir/Coding
                       {:system #fhir/uri "system-192253"
                        :code #fhir/code "code-140541"}]}
                   :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

          (let [context
                {:node node
                 :eval-context "Patient"
                 :library
                 {:codeSystems
                  {:def
                   [{:name "sys-def-131750"
                     :id "system-192253"}]}}}
                elm #elm/retrieve
                     {:type "Observation"
                      :codes
                      #elm/source-property
                       [#elm/concept
                         [[#elm/code ["sys-def-131750" "code-192300"]
                           #elm/code ["sys-def-131750" "code-140541"]]]
                        "codes"]}
                expr (c/compile context elm)
                db (d/db node)
                patient (ctu/resource db "Patient" "0")]

            (testing "eval"
              (given (expr/eval (eval-context db) expr patient)
                count := 2
                [0 :fhir/type] := :fhir/Observation
                [0 :id] := "1"
                [1 :fhir/type] := :fhir/Observation
                [1 :id] := "2"))

            (testing "expression is dynamic"
              (is (false? (core/-static expr))))

            (ctu/testing-constant-attach-cache expr)

            (ctu/testing-constant-patient-count expr)

            (ctu/testing-constant-resolve-refs expr)

            (ctu/testing-constant-resolve-params expr)

            (testing "optimize"
              (is (= expr (c/optimize expr db))))

            (testing "form"
              (has-form expr
                '(retrieve
                  "Observation"
                  [["code"
                    "system-192253|code-192300"
                    "system-192253|code-140541"]]))))))

      (testing "unknown code property"
        (with-system [{:blaze.db/keys [node] terminology-service ::ts/local} config]
          (let [context
                {:node node
                 :eval-context "Patient"
                 :library
                 {:codeSystems
                  {:def
                   [{:name "sys-def-225944"
                     :id "system-225806"}]}}
                 :terminology-service terminology-service}
                elm #elm/retrieve
                     {:type "Observation"
                      :codes #elm/list [#elm/code ["sys-def-225944"
                                                   "code-225809"]]
                      :code-property "foo"}]

            (given (ba/try-anomaly (c/compile context elm))
              ::anom/category := ::anom/not-found
              ::anom/message := "The search-param with code `foo` and type `Observation` was not found."))))))

  (testing "Specimen context"
    (testing "Patient"
      (with-system-data [{:blaze.db/keys [node]} (assoc-in mem-node-config [:blaze.db/node :enforce-referential-integrity] false)]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Specimen :id "0"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
          [:put {:fhir/type :fhir/Specimen :id "1"}]
          [:put {:fhir/type :fhir/Group :id "0"}]
          [:put {:fhir/type :fhir/Specimen :id "2"
                 :subject #fhir/Reference{:reference #fhir/string "Group/0"}}]
          [:put {:fhir/type :fhir/Specimen :id "3"
                 :subject #fhir/Reference{:reference #fhir/string "invalid"}}]
          [:put {:fhir/type :fhir/Patient :id "1"}]
          [:put {:fhir/type :fhir/Specimen :id "4"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/1"}}]]
         [[:delete "Patient" "1"]]]

        (let [context
              {:node node
               :eval-context "Specimen"
               :library {}}
              expr (c/compile context ctu/patient-retrieve-elm)
              db (d/db node)
              eval-ctx (eval-context db)]

          (testing "eval"
            (testing "specimen with patient subject"
              (given (expr/eval eval-ctx expr (ctu/resource db "Specimen" "0"))
                count := 1
                [0 :fhir/type] := :fhir/Patient
                [0 :id] := "0"))

            (testing "specimen without subject"
              (is (nil? (expr/eval eval-ctx expr (ctu/resource db "Specimen" "1")))))

            (testing "specimen with group subject"
              (is (nil? (expr/eval eval-ctx expr (ctu/resource db "Specimen" "2")))))

            (testing "specimen with invalid subject reference"
              (is (nil? (expr/eval eval-ctx expr (ctu/resource db "Specimen" "3")))))

            (testing "specimen with deleted patient subject"
              (is (nil? (expr/eval eval-ctx expr (ctu/resource db "Specimen" "4"))))))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (ctu/testing-constant-attach-cache expr)

          (ctu/testing-constant-patient-count expr)

          (ctu/testing-constant-resolve-refs expr)

          (ctu/testing-constant-resolve-params expr)

          (ctu/testing-constant-optimize expr)

          (testing "form"
            (has-form expr
              '(retrieve (Specimen) "Patient")))))))

  (testing "Unfiltered context"
    (testing "Medication"
      (with-system-data [{:blaze.db/keys [node] terminology-service ::ts/local} config]
        [[[:put {:fhir/type :fhir/Medication :id "0"
                 :code
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri "system-225806"
                      :code #fhir/code "code-225809"}]}}]]]

        (let [context
              {:node node
               :eval-context "Unfiltered"
               :library
               {:codeSystems
                {:def
                 [{:name "sys-def-225944"
                   :id "system-225806"}]}}
               :terminology-service terminology-service}
              elm #elm/retrieve
                   {:type "Medication"
                    :codes #elm/list [#elm/code ["sys-def-225944"
                                                 "code-225809"]]}
              expr (c/compile context elm)
              db (d/db node)]

          (testing "eval"
            (given (expr/eval (eval-context db) expr nil)
              count := 1
              [0 :fhir/type] := :fhir/Medication
              [0 :id] := "0"))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (ctu/testing-constant-attach-cache expr)

          (ctu/testing-constant-patient-count expr)

          (ctu/testing-constant-resolve-refs expr)

          (ctu/testing-constant-resolve-params expr)

          (ctu/testing-constant-optimize expr)

          (testing "form"
            (has-form expr
              '(retrieve "Medication" [["code" "system-225806|code-225809"]]))))))

    (testing "unknown code property"
      (with-system [{:blaze.db/keys [node] terminology-service ::ts/local} config]
        (let [context
              {:node node
               :eval-context "Unfiltered"
               :library
               {:codeSystems
                {:def
                 [{:name "sys-def-225944"
                   :id "system-225806"}]}}
               :terminology-service terminology-service}
              elm #elm/retrieve
                   {:type "Medication"
                    :codes #elm/list [#elm/code ["sys-def-225944"
                                                 "code-225809"]]
                    :code-property "foo"}]

          (given (ba/try-anomaly (c/compile context elm))
            ::anom/category := ::anom/not-found
            ::anom/message := "The search-param with code `foo` and type `Medication` was not found."))))

    (testing "dynamic codes expression"
      (given (ba/try-anomaly (c/compile {} #elm/retrieve{:type "Medication" :codes elm/today}))
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported dynamic codes expression `today` in Retrieve expression.")))

  (testing "with related context"
    (testing "without code"
      (with-system-data [{:blaze.db/keys [node] :as system} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

        (let [library (t/translate
                       "library test
                        using FHIR version '4.0.0'
                        include FHIRHelpers version '4.0.0'

                        context Patient

                        define \"name-133756\":
                          singleton from ([Patient])

                        define InInitialPopulation:
                          [\"name-133756\" -> Observation]
                        ")
              context (compile-context system)
              {:keys [expression-defs]} (library/compile-library context library {})
              db (d/db node)
              patient (ctu/resource db "Patient" "0")
              eval-context (assoc (eval-context db) :expression-defs expression-defs)
              expr (:expression (get expression-defs "InInitialPopulation"))]

          (testing "eval"
            (given (expr/eval eval-context expr patient)
              count := 1
              [0 :fhir/type] := :fhir/Observation
              [0 :id] := "0"))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (ctu/testing-constant-attach-cache expr)

          (ctu/testing-constant-patient-count expr)

          (ctu/testing-constant-resolve-refs expr)

          (ctu/testing-constant-resolve-params expr)

          (ctu/testing-constant-optimize expr)

          (testing "form"
            (has-form expr
              '(retrieve (singleton-from (retrieve-resource)) "Observation"))))))

    (testing "with pre-compiled database query"
      (with-system-data [{:blaze.db/keys [node] :as system} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :code
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri "system-133620"
                      :code #fhir/code "code-133657"}]}
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

        (let [library (t/translate
                       "library test
                        using FHIR version '4.0.0'
                        include FHIRHelpers version '4.0.0'

                        codesystem sys: 'system-133620'

                        context Patient

                        define \"name-133730\":
                          singleton from ([Patient])

                        define InInitialPopulation:
                          [\"name-133730\" -> Observation: Code 'code-133657' from sys]
                        ")
              context (compile-context system)
              {:keys [expression-defs]} (library/compile-library context library {})
              db (d/db node)
              patient (ctu/resource db "Patient" "0")
              eval-context (assoc (eval-context db) :expression-defs expression-defs)
              expr (:expression (get expression-defs "InInitialPopulation"))]

          (testing "eval"
            (given (expr/eval eval-context expr patient)
              count := 1
              [0 :fhir/type] := :fhir/Observation
              [0 :id] := "0"))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (ctu/testing-constant-attach-cache expr)

          (ctu/testing-constant-patient-count expr)

          (ctu/testing-constant-resolve-refs expr)

          (ctu/testing-constant-resolve-params expr)

          (ctu/testing-constant-optimize expr)

          (testing "form"
            (has-form expr
              '(retrieve (singleton-from (retrieve-resource)) "Observation"
                         [["code" "system-133620|code-133657"]]))))))

    (testing "unknown code property"
      (with-system [{:blaze.db/keys [node] terminology-service ::ts/local} config]
        (let [library {:codeSystems
                       {:def [{:name "sys-def-174848" :id "system-174915"}]}
                       :statements
                       {:def
                        [{:type "ExpressionDef"
                          :name "name-174207"
                          :resultTypeName "{http://hl7.org/fhir}Patient"}]}}
              elm #elm/retrieve
                   {:type "Observation"
                    :context #elm/expression-ref "name-174207"
                    :codes #elm/list [#elm/code ["sys-def-174848"
                                                 "code-174911"]]
                    :code-property "foo"}]

          (given (ba/try-anomaly (c/compile {:node node :library library :terminology-service terminology-service} elm))
            ::anom/category := ::anom/not-found
            ::anom/message := "The search-param with code `foo` and type `Observation` was not found."))))

    (testing "missing context result type"
      (with-system [{:blaze.db/keys [node] terminology-service ::ts/local} config]
        (let [library {:codeSystems
                       {:def [{:name "sys-def-174848" :id "system-174915"}]}
                       :statements
                       {:def
                        [{:type "ExpressionDef"
                          :name "name-174207"}]}}
              elm #elm/retrieve
                   {:type "Observation"
                    :context #elm/expression-ref "name-174207"
                    :codes #elm/list [#elm/code ["sys-def-174848"
                                                 "code-174911"]]}]

          (given (ba/try-anomaly (c/compile {:node node :library library :terminology-service terminology-service} elm))
            ::anom/category := ::anom/unsupported
            ::anom/message := "Unsupported related context retrieve expression without result type."))))

    (testing "unsupported context result type namespace"
      (with-system [{:blaze.db/keys [node] terminology-service ::ts/local} config]
        (let [library {:codeSystems
                       {:def [{:name "sys-def-174848" :id "system-174915"}]}
                       :statements
                       {:def
                        [{:type "ExpressionDef"
                          :name "name-174207"
                          :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"}]}}
              elm #elm/retrieve
                   {:type "Observation"
                    :context #elm/expression-ref "name-174207"
                    :codes #elm/list [#elm/code ["sys-def-174848"
                                                 "code-174911"]]}]

          (given (ba/try-anomaly (c/compile {:node node :library library :terminology-service terminology-service} elm))
            ::anom/category := ::anom/unsupported
            ::anom/message := "Unsupported related context retrieve expression with result type namespace of `urn:hl7-org:elm-types:r1`.")))))

  (testing "with unsupported type namespace"
    (let [elm {:type "Retrieve" :dataType "{foo}Bar"}]
      (given (ba/try-anomaly (c/compile {} elm))
        ::anom/category := ::anom/unsupported))))
