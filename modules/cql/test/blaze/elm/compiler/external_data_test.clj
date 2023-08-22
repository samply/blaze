(ns blaze.elm.compiler.external-data-test
  "11. External Data

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.anomaly :as ba]
    [blaze.cql-translator :as t]
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-config with-system-data]]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.external-data :as ed]
    [blaze.elm.compiler.external-data-spec]
    [blaze.elm.compiler.library :as library]
    [blaze.elm.compiler.test-util :as tu :refer [has-form]]
    [blaze.elm.expression :as expr]
    [blaze.elm.expression-spec]
    [blaze.elm.expression.cache :as-alias expr-cache]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type]
    [blaze.module.test-util :refer [with-system]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]])
  (:import
    [java.time OffsetDateTime]))


(set! *warn-on-reflection* true)
(st/instrument)
(tu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (tu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn- eval-context [db]
  {:db db :now (OffsetDateTime/now)})


(defn- resource [db type id]
  (ed/mk-resource db (d/resource-handle db type id)))


(deftest resource-test
  (testing "toString"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (is (= "Patient[id = 0, t = 1]" (str (resource (d/db node) "Patient" "0")))))))


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
              expr (c/compile context tu/patient-retrieve-elm)
              db (d/db node)
              patient (resource db "Patient" "0")]

          (testing "eval"
            (given (expr/eval (eval-context db) expr patient)
              [0 fhir-spec/fhir-type] := :fhir/Patient
              [0 :id] := "0"))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (testing "form"
            (has-form expr '(retrieve-resource))))))

    (testing "Observation"
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "1"
                 :subject #fhir/Reference{:reference "Patient/0"}}]]]

        (let [context
              {:node node
               :eval-context "Patient"
               :library {}}
              expr (c/compile context #elm/retrieve{:type "Observation"})
              db (d/db node)
              patient (resource db "Patient" "0")]

          (testing "eval"
            (given (expr/eval (eval-context db) expr patient)
              [0 fhir-spec/fhir-type] := :fhir/Observation
              [0 :id] := "1"))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (testing "form"
            (has-form expr '(retrieve "Observation")))))

      (testing "with one code"
        (with-system-data [{:blaze.db/keys [node]} mem-node-config]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "0"
                   :subject #fhir/Reference{:reference "Patient/0"}}]
            [:put {:fhir/type :fhir/Observation :id "1"
                   :code
                   #fhir/CodeableConcept
                           {:coding
                            [#fhir/Coding
                                    {:system #fhir/uri"system-192253"
                                     :code #fhir/code"code-192300"}]}
                   :subject #fhir/Reference{:reference "Patient/0"}}]]]

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
                             :codes #elm/list [#elm/code ["sys-def-131750"
                                                          "code-192300"]]}
                expr (c/compile context elm)
                db (d/db node)
                patient (resource db "Patient" "0")]

            (testing "eval"
              (given (expr/eval (eval-context db) expr patient)
                count := 1
                [0 fhir-spec/fhir-type] := :fhir/Observation
                [0 :id] := "1"))

            (testing "expression is dynamic"
              (is (false? (core/-static expr))))

            (testing "form"
              (has-form expr
                '(retrieve "Observation" [["code" "system-192253|code-192300"]]))))))

      (testing "with two codes"
        (with-system-data [{:blaze.db/keys [node]} mem-node-config]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "0"
                   :subject #fhir/Reference{:reference "Patient/0"}}]
            [:put {:fhir/type :fhir/Observation :id "1"
                   :code
                   #fhir/CodeableConcept
                           {:coding
                            [#fhir/Coding
                                    {:system #fhir/uri"system-192253"
                                     :code #fhir/code"code-192300"}]}
                   :subject #fhir/Reference{:reference "Patient/0"}}]
            [:put {:fhir/type :fhir/Observation :id "2"
                   :code
                   #fhir/CodeableConcept
                           {:coding
                            [#fhir/Coding
                                    {:system #fhir/uri"system-192253"
                                     :code #fhir/code"code-140541"}]}
                   :subject #fhir/Reference{:reference "Patient/0"}}]]]

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
                             #elm/list [#elm/code ["sys-def-131750" "code-192300"]
                                        #elm/code ["sys-def-131750" "code-140541"]]}
                expr (c/compile context elm)
                db (d/db node)
                patient (resource db "Patient" "0")]

            (testing "eval"
              (given (expr/eval (eval-context db) expr patient)
                count := 2
                [0 fhir-spec/fhir-type] := :fhir/Observation
                [0 :id] := "1"
                [1 fhir-spec/fhir-type] := :fhir/Observation
                [1 :id] := "2"))

            (testing "expression is dynamic"
              (is (false? (core/-static expr))))

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
                   :subject #fhir/Reference{:reference "Patient/0"}}]
            [:put {:fhir/type :fhir/Observation :id "1"
                   :code
                   #fhir/CodeableConcept
                           {:coding
                            [#fhir/Coding
                                    {:system #fhir/uri"system-192253"
                                     :code #fhir/code"code-192300"}]}
                   :subject #fhir/Reference{:reference "Patient/0"}}]
            [:put {:fhir/type :fhir/Observation :id "2"
                   :code
                   #fhir/CodeableConcept
                           {:coding
                            [#fhir/Coding
                                    {:system #fhir/uri"system-192253"
                                     :code #fhir/code"code-140541"}]}
                   :subject #fhir/Reference{:reference "Patient/0"}}]]]

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
                             {:type "Property"
                              :path "codes"
                              :source #elm/concept
                                     [[#elm/code ["sys-def-131750" "code-192300"]
                                       #elm/code ["sys-def-131750" "code-140541"]]]}}
                expr (c/compile context elm)
                db (d/db node)
                patient (resource db "Patient" "0")]

            (testing "eval"
              (given (expr/eval (eval-context db) expr patient)
                count := 2
                [0 fhir-spec/fhir-type] := :fhir/Observation
                [0 :id] := "1"
                [1 fhir-spec/fhir-type] := :fhir/Observation
                [1 :id] := "2"))

            (testing "expression is dynamic"
              (is (false? (core/-static expr))))

            (testing "form"
              (has-form expr
                '(retrieve
                   "Observation"
                   [["code"
                     "system-192253|code-192300"
                     "system-192253|code-140541"]]))))))))

  (testing "Specimen context"
    (testing "Patient"
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Specimen :id "0"
                 :subject #fhir/Reference{:reference "Patient/0"}}]]]

        (let [context
              {:node node
               :eval-context "Specimen"
               :library {}}
              expr (c/compile context tu/patient-retrieve-elm)
              db (d/db node)
              specimen (resource db "Specimen" "0")]

          (testing "eval"
            (given (expr/eval (eval-context db) expr specimen)
              [0 fhir-spec/fhir-type] := :fhir/Patient
              [0 :id] := "0"))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (testing "form"
            (has-form expr
              '(retrieve (Specimen) "Patient")))))))

  (testing "Unfiltered context"
    (testing "Medication"
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put {:fhir/type :fhir/Medication :id "0"
                 :code
                 #fhir/CodeableConcept
                         {:coding
                          [#fhir/Coding
                                  {:system #fhir/uri"system-225806"
                                   :code #fhir/code"code-225809"}]}}]]]

        (let [context
              {:node node
               :eval-context "Unfiltered"
               :library
               {:codeSystems
                {:def
                 [{:name "sys-def-225944"
                   :id "system-225806"}]}}}
              elm #elm/retrieve
                          {:type "Medication"
                           :codes #elm/list [#elm/code ["sys-def-225944"
                                                        "code-225809"]]}
              expr (c/compile context elm)
              db (d/db node)]

          (testing "eval"
            (given (expr/eval (eval-context db) expr nil)
              count := 1
              [0 fhir-spec/fhir-type] := :fhir/Medication
              [0 :id] := "0"))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (testing "form"
            (has-form expr
              '(retrieve "Medication" [["code" "system-225806|code-225809"]]))))))

    (testing "unknown code property"
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (let [context
              {:node node
               :eval-context "Unfiltered"
               :library
               {:codeSystems
                {:def
                 [{:name "sys-def-225944"
                   :id "system-225806"}]}}}
              elm #elm/retrieve
                          {:type "Medication"
                           :codes #elm/list [#elm/code ["sys-def-225944"
                                                        "code-225809"]]
                           :code-property "foo"}]

          (given (ba/try-anomaly (c/compile context elm))
            ::anom/category := ::anom/not-found
            ::anom/message := "The search-param with code `foo` and type `Medication` was not found.")))))

  (testing "with related context"
    (testing "without code"
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference "Patient/0"}}]]]

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
              compile-context {::expr-cache/enabled? false}
              {:keys [expression-defs]} (library/compile-library
                                          node library compile-context)
              db (d/db node)
              patient (resource db "Patient" "0")
              eval-context (assoc (eval-context db) :expression-defs expression-defs)
              expr (:expression (get expression-defs "InInitialPopulation"))]

          (testing "eval"
            (given (expr/eval eval-context expr patient)
              count := 1
              [0 fhir-spec/fhir-type] := :fhir/Observation
              [0 :id] := "0"))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (testing "form"
            (has-form expr '(retrieve (expr-ref "name-133756") "Observation"))))))

    (testing "with pre-compiled database query"
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :code
                 #fhir/CodeableConcept
                         {:coding
                          [#fhir/Coding
                                  {:system #fhir/uri"system-133620"
                                   :code #fhir/code"code-133657"}]}
                 :subject #fhir/Reference{:reference "Patient/0"}}]]]

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
              compile-context {::expr-cache/enabled? false}
              {:keys [expression-defs]} (library/compile-library
                                          node library compile-context)
              db (d/db node)
              patient (resource db "Patient" "0")
              eval-context (assoc (eval-context db) :expression-defs expression-defs)
              expr (:expression (get expression-defs "InInitialPopulation"))]

          (testing "eval"
            (given (expr/eval eval-context expr patient)
              count := 1
              [0 fhir-spec/fhir-type] := :fhir/Observation
              [0 :id] := "0"))

          (testing "expression is dynamic"
            (is (false? (core/-static expr))))

          (testing "form"
            (has-form expr
              '(retrieve (expr-ref "name-133730") "Observation"
                         [["code" "system-133620|code-133657"]]))))))

    (testing "unknown code property"
      (with-system [{:blaze.db/keys [node]} mem-node-config]
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

          (given (ba/try-anomaly (c/compile {:node node :library library} elm))
            ::anom/category := ::anom/not-found
            ::anom/message := "The search-param with code `foo` and type `Observation` was not found.")))))

  (testing "with unsupported type namespace"
    (let [elm {:type "Retrieve" :dataType "{foo}Bar"}]
      (given (ba/try-anomaly (c/compile {} elm))
        ::anom/category := ::anom/unsupported))))
