(ns blaze.elm.compiler.library-test
  (:require
   [blaze.cql.translator :as t]
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler-spec]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.function-spec]
   [blaze.elm.compiler.library :as library]
   [blaze.elm.compiler.library-spec]
   [blaze.elm.expression.cache :as ec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.system]
   [blaze.module.test-util :refer [with-system]]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn- compile-context [{:blaze.db/keys [node] ::ts/keys [local]}]
  {:node node :terminology-service local})

(def ^:private default-opts {})

(def ^:private expr-form (comp c/form :expression))

(defn- codeable-concept [system code]
  (type/codeable-concept
   {:coding
    [(type/coding
      {:system (type/uri system)
       :code (type/code code)})]}))

(defn- function-def [name arity]
  (fn [context]
    (core/resolve-function-def context name arity)))

;; 5.1. Library
;;
;; 1. The identifier element defines a unique identifier for this library, and
;;    optionally, a system (or namespace) and version.
;;
;; 2. This is the identifier of the XML schema (and its version) which governs
;;    the structure of this Library.
;;
;; 3. Set of data models referenced in the Expression objects in this knowledge
;;    artifact.
;;
;; 4. A reference to a data model that is used in the artifact, e.g., the Virtual
;;    Medical Record.
;;
;; 5. Set of libraries referenced by this artifact. Components of referenced
;;    libraries may be used within this artifact.
;;
;; 6. A reference to a library whose components can be used within the
;;    artifact.
;;
;; 7. The parameters defined within this library.
;;
;; 8. The code systems defined within this library.
;;
;; 9. The value sets defined within this library.
;;
;; 10. The codes defined within this library.
;;
;; 11. The concepts defined within this library.
;;
;; 12. The contexts used within this library.
;;
;; 13. The statements section contains the expression and function definitions
;;     for the library.
;;
;; A Library is an instance of a CQL-ELM library.
(deftest compile-library-test
  (testing "empty library"
    (let [library (t/translate "library Test")]
      (with-system [system mem-node-config]
        (given (library/compile-library (compile-context system) library default-opts)
          :expression-defs := {}))))

  (testing "one static expression"
    (let [library (t/translate "library Test define Foo: true")]
      (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library default-opts)]
          (given expression-defs
            ["Foo" :context] := "Patient"
            ["Foo" :expression] := true)

          (testing "there are no optimizations available"
            (is (= expression-defs (library/optimize (d/db node) expression-defs))))))))

  (testing "one dynamic expression"
    (let [library (t/translate "library Test
        using FHIR version '4.0.0'
        context Patient
        define Gender: Patient.gender")]
      (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library default-opts)]
          (given expression-defs
            ["Gender" :context] := "Patient"
            ["Gender" expr-form] := '(:gender (singleton-from (retrieve-resource))))

          (testing "there are no references to resolve"
            (is (= expression-defs (library/resolve-all-refs expression-defs))))

          (testing "there are no optimizations available"
            (is (= expression-defs (library/optimize (d/db node) expression-defs))))))))

  (testing "one function"
    (let [library (t/translate "library Test
        using FHIR version '4.0.0'
        context Patient
        define function Gender(P Patient): P.gender
        define InInitialPopulation: Gender(Patient)")]
      (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
        (let [{:keys [expression-defs] :as context}
              (library/compile-library (compile-context system) library default-opts)]

          (given expression-defs
            ["InInitialPopulation" :context] := "Patient"
            ["InInitialPopulation" :resultTypeName] := "{http://hl7.org/fhir}AdministrativeGender"
            ["InInitialPopulation" expr-form] := '(call "Gender" (singleton-from (retrieve-resource))))

          (given context
            [(function-def "Gender" 1) :context] := "Patient"
            [(function-def "Gender" 1) :resultTypeName] := "{http://hl7.org/fhir}AdministrativeGender"
            [(function-def "Gender" 1) :function #(% "x") c/form] := '(call "Gender" "x")
            [(function-def "Gender" 1) :operand count] := 1
            [(function-def "Gender" 1) :operand 0 :name] := "P"
            [(function-def "Gender" 1) :operand 0 :operandTypeSpecifier :resultTypeName] := "{http://hl7.org/fhir}Patient")

          (testing "there are no references to resolve"
            (is (= expression-defs (library/resolve-all-refs expression-defs))))

          (testing "there are no optimizations available"
            (is (= expression-defs (library/optimize (d/db node) expression-defs))))))))

  (testing "two functions, one calling the other"
    (let [library (t/translate "library Test
        using FHIR version '4.0.0'
        context Patient
        define function Inc(i System.Integer): i + 1
        define function Inc2(i System.Integer): Inc(i) + 1
        define InInitialPopulation: Inc2(1)")]
      (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
        (let [{:keys [expression-defs] :as context}
              (library/compile-library (compile-context system) library default-opts)]
          (given expression-defs
            ["InInitialPopulation" :context] := "Patient"
            ["InInitialPopulation" expr-form] := '(call "Inc2" 1))

          (given context
            [(function-def "Inc" 1) :context] := "Patient"
            [(function-def "Inc" 1) :resultTypeName] := "{urn:hl7-org:elm-types:r1}Integer"
            [(function-def "Inc" 1) :function #(% 1) c/form] := '(call "Inc" 1)
            [(function-def "Inc" 1) :operand count] := 1
            [(function-def "Inc" 1) :operand 0 :name] := "i"
            [(function-def "Inc" 1) :operand 0 :operandTypeSpecifier :resultTypeName] := "{urn:hl7-org:elm-types:r1}Integer"
            [(function-def "Inc2" 1) :context] := "Patient"
            [(function-def "Inc2" 1) :resultTypeName] := "{urn:hl7-org:elm-types:r1}Integer"
            [(function-def "Inc2" 1) :function #(% 1) c/form] := '(call "Inc2" 1)
            [(function-def "Inc2" 1) :operand count] := 1
            [(function-def "Inc2" 1) :operand 0 :name] := "i"
            [(function-def "Inc2" 1) :operand 0 :operandTypeSpecifier :resultTypeName] := "{urn:hl7-org:elm-types:r1}Integer")

          (testing "there are no references to resolve"
            (is (= expression-defs (library/resolve-all-refs expression-defs))))

          (testing "there are no optimizations available"
            (is (= expression-defs (library/optimize (d/db node) expression-defs))))))))

  (testing "functions of different arities"
    (let [library (t/translate "library Test
        using FHIR version '4.0.0'
        context Patient
        define function Const(): 1
        define function Inc(i System.Integer): i + 1
        define function Add(i System.Integer, j System.Integer): i + j
        define function Add(i System.Integer, j System.Integer, k System.Integer): { i, j, k }")]
      (with-system [system mem-node-config]
        (let [context (library/compile-library (compile-context system) library default-opts)]
          (given context
            [(function-def "Const" 0) :context] := "Patient"
            [(function-def "Const" 0) :resultTypeName] := "{urn:hl7-org:elm-types:r1}Integer"
            [(function-def "Const" 0) :function #(%) c/form] := '(call "Const")
            [(function-def "Const" 0) :function #(%) #(core/-eval % {} nil {})] := 1
            [(function-def "Const" 0) :operand count] := 0
            [(function-def "Inc" 1) :context] := "Patient"
            [(function-def "Inc" 1) :resultTypeName] := "{urn:hl7-org:elm-types:r1}Integer"
            [(function-def "Inc" 1) :function #(% 1) c/form] := '(call "Inc" 1)
            [(function-def "Inc" 1) :function #(% 1) #(core/-eval % {} nil {})] := 2
            [(function-def "Inc" 1) :operand count] := 1
            [(function-def "Inc" 1) :operand 0 :name] := "i"
            [(function-def "Inc" 1) :operand 0 :operandTypeSpecifier :resultTypeName] := "{urn:hl7-org:elm-types:r1}Integer"
            [(function-def "Add" 2) :context] := "Patient"
            [(function-def "Add" 2) :resultTypeName] := "{urn:hl7-org:elm-types:r1}Integer"
            [(function-def "Add" 2) :function #(% 1 2) c/form] := '(call "Add" 1 2)
            [(function-def "Add" 2) :function #(% 1 2) #(core/-eval % {} nil {})] := 3
            [(function-def "Add" 2) :operand count] := 2
            [(function-def "Add" 2) :operand 0 :name] := "i"
            [(function-def "Add" 2) :operand 0 :operandTypeSpecifier :resultTypeName] := "{urn:hl7-org:elm-types:r1}Integer"
            [(function-def "Add" 2) :operand 1 :name] := "j"
            [(function-def "Add" 2) :operand 1 :operandTypeSpecifier :resultTypeName] := "{urn:hl7-org:elm-types:r1}Integer"
            [(function-def "Add" 3) :context] := "Patient"
            [(function-def "Add" 3) :resultTypeSpecifier :type] := "ListTypeSpecifier"
            [(function-def "Add" 3) :resultTypeSpecifier :elementType :name] := "{urn:hl7-org:elm-types:r1}Integer"
            [(function-def "Add" 3) :function #(% 1 2 3) c/form] := '(call "Add" 1 2 3)
            [(function-def "Add" 3) :function #(% 1 2 3) #(core/-eval % {} nil {})] := [1 2 3]
            [(function-def "Add" 3) :function #(% 3 1 2) #(core/-eval % {} nil {})] := [3 1 2]
            [(function-def "Add" 3) :operand count] := 3
            [(function-def "Add" 3) :operand 0 :name] := "i"
            [(function-def "Add" 3) :operand 0 :operandTypeSpecifier :resultTypeName] := "{urn:hl7-org:elm-types:r1}Integer"
            [(function-def "Add" 3) :operand 1 :name] := "j"
            [(function-def "Add" 3) :operand 1 :operandTypeSpecifier :resultTypeName] := "{urn:hl7-org:elm-types:r1}Integer"
            [(function-def "Add" 3) :operand 2 :name] := "k"
            [(function-def "Add" 3) :operand 2 :operandTypeSpecifier :resultTypeName] := "{urn:hl7-org:elm-types:r1}Integer")))))

  (testing "expressions from Patient context are resolved"
    (let [library (t/translate "library Test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        context Patient

        define Female:
          Patient.gender = 'female'

        define HasObservation:
          exists [Observation]

        define HasCondition:
          exists [Condition]

        define Inclusion:
          Female and
          HasObservation

        define Exclusion:
          HasCondition

        define InInitialPopulation:
          Inclusion and
          not Exclusion")]
      (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library default-opts)]
          (given expression-defs
            ["InInitialPopulation" :context] := "Patient"
            ["InInitialPopulation" expr-form] :=
            '(and
              (and
               (equal
                (call
                 "ToString"
                 (:gender
                  (singleton-from (retrieve-resource))))
                "female")
               (exists (retrieve "Observation")))
              (not
               (exists (retrieve "Condition")))))

          (testing "there are no references to resolve"
            (is (= expression-defs (library/resolve-all-refs expression-defs))))

          (testing "there are no optimizations available"
            (is (= expression-defs (library/optimize (d/db node) expression-defs))))))))

  (testing "expressions from Unfiltered context are not resolved"
    (let [library (t/translate "library Test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        codesystem atc: 'http://fhir.de/CodeSystem/dimdi/atc'

        context Unfiltered

        define TemozolomidRefs:
          [Medication: Code 'L01AX03' from atc] M return 'Medication/' + M.id

        context Patient

        define InInitialPopulation:
          Patient.gender = 'female' and
          exists from [MedicationStatement] M
            where M.medication.reference in TemozolomidRefs")]
      (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library default-opts)]
          (given expression-defs
            ["TemozolomidRefs" :context] := "Unfiltered"
            ["TemozolomidRefs" expr-form] :=
            '(vector-query
              (comp
               (map
                (fn [M]
                  (concatenate "Medication/" (call "ToString" (:id M)))))
               distinct)
              (retrieve
               "Medication"
               [["code" "http://fhir.de/CodeSystem/dimdi/atc|L01AX03"]]))

            ["InInitialPopulation" :context] := "Patient"
            ["InInitialPopulation" expr-form] :=
            '(and
              (equal
               (call
                "ToString"
                (:gender
                 (singleton-from
                  (retrieve-resource))))
               "female")
              (exists
               (eduction-query
                (filter
                 (fn [M]
                   (contains
                    (expr-ref
                     "TemozolomidRefs")
                    (call
                     "ToString"
                     (:reference
                      (:medication
                       M))))))
                (retrieve "MedicationStatement")))))

          (testing "TemozolomidRefs expression will be resolved"
            (given (library/resolve-all-refs expression-defs)
              ["InInitialPopulation" :context] := "Patient"
              ["InInitialPopulation" expr-form] :=
              '(and
                (equal
                 (call
                  "ToString"
                  (:gender
                   (singleton-from
                    (retrieve-resource))))
                 "female")
                (exists
                 (eduction-query
                  (filter
                   (fn [M]
                     (contains
                      (vector-query
                       (comp
                        (map
                         (fn
                           [M]
                           (concatenate
                            "Medication/"
                            (call
                             "ToString"
                             (:id
                              M)))))
                        distinct)
                       (retrieve
                        "Medication"
                        [["code"
                          "http://fhir.de/CodeSystem/dimdi/atc|L01AX03"]]))
                      (call
                       "ToString"
                       (:reference
                        (:medication
                         M))))))
                  (retrieve "MedicationStatement"))))))

          (testing "there are no optimizations available"
            (is (= expression-defs (library/optimize (d/db node) expression-defs))))))))

  (testing "expressions without refs are preserved"
    (let [library (t/translate "library Retrieve
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        context Patient

        define InInitialPopulation:
          true

        define AllEncounters:
          [Encounter]

        define Gender:
          Patient.gender")]
      (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library default-opts)]
          (given expression-defs
            ["Patient" :context] := "Patient"
            ["Patient" expr-form] := '(singleton-from (retrieve-resource))
            ["InInitialPopulation" :context] := "Patient"
            ["InInitialPopulation" expr-form] := true
            ["AllEncounters" :context] := "Patient"
            ["AllEncounters" expr-form] := '(retrieve "Encounter")
            ["Gender" :context] := "Patient"
            ["Gender" expr-form] := '(:gender (singleton-from (retrieve-resource))))

          (testing "there are no references to resolve"
            (is (= expression-defs (library/resolve-all-refs expression-defs))))

          (testing "there are no optimizations available"
            (is (= expression-defs (library/optimize (d/db node) expression-defs))))))))

  (testing "medication reference optimization"
    (testing "with two references"
      (let [library (t/translate "library Retrieve
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        context Patient

        define InInitialPopulation:
          exists from [MedicationAdministration] M
            where M.medication.reference in {'Medication/0', 'Medication/1'}")]
        (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
          (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library default-opts)]
            (given expression-defs
              ["InInitialPopulation" :context] := "Patient"
              ["InInitialPopulation" expr-form] :=
              '(exists
                (eduction-query
                 (filter
                  (fn [M]
                    (contains
                     ["Medication/0" "Medication/1"]
                     (call "ToString" (:reference (:medication M))))))
                 (retrieve "MedicationAdministration"))))

            (testing "there are no references to resolve"
              (is (= expression-defs (library/resolve-all-refs expression-defs))))

            (testing "there are no optimizations available"
              (given (library/optimize (d/db node) expression-defs)
                ["InInitialPopulation" :context] := "Patient"
                ["InInitialPopulation" expr-form] :=
                '(exists
                  (eduction-query
                   (matcher [["medication" "Medication/0" "Medication/1"]])
                   (retrieve "MedicationAdministration")))))))))

    (testing "with no reference because there are no Medications"
      (let [library (t/translate "library Retrieve
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        codesystem atc: 'http://fhir.de/CodeSystem/bfarm/atc'

        context Unfiltered

        define MedicationRefs:
          from [Medication: Code 'A10BF01' from atc] M
            return 'Medication/' + M.id

        context Patient

        define InInitialPopulation:
          exists from [MedicationAdministration] M
            where M.medication.reference in MedicationRefs")]
        (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
          (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library default-opts)]
            (given expression-defs
              ["InInitialPopulation" :context] := "Patient"
              ["InInitialPopulation" expr-form] :=
              '(exists
                (eduction-query
                 (filter
                  (fn [M]
                    (contains
                     (expr-ref "MedicationRefs")
                     (call "ToString" (:reference (:medication M))))))
                 (retrieve "MedicationAdministration"))))

            (testing "the whole exists expression optimizes to false"
              (let [db (d/db node)]
                (given (->> (library/eval-unfiltered {:db db
                                                      :now (time/offset-date-time)}
                                                     expression-defs)
                            (library/resolve-all-refs)
                            (library/optimize db))
                  ["InInitialPopulation" :context] := "Patient"
                  ["InInitialPopulation" expr-form] := false))))))

      (testing "with a disjunction of two such queries"
        (let [library (t/translate "library Retrieve
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        codesystem atc: 'http://fhir.de/CodeSystem/bfarm/atc'

        context Unfiltered

        define MedicationRefsA:
          from [Medication: Code 'A10BF01' from atc] M
            return 'Medication/' + M.id

        define MedicationRefsB:
          from [Medication: Code 'A10BB31' from atc] M
            return 'Medication/' + M.id

        context Patient

        define InInitialPopulation:
          exists (from [MedicationAdministration] M
            where M.medication.reference in MedicationRefsA) or
          exists (from [MedicationAdministration] M
            where M.medication.reference in MedicationRefsB)")]
          (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
            (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library default-opts)]
              (given expression-defs
                ["InInitialPopulation" :context] := "Patient"
                ["InInitialPopulation" expr-form] :=
                '(or
                  (exists
                   (eduction-query
                    (filter
                     (fn [M]
                       (contains
                        (expr-ref "MedicationRefsA")
                        (call "ToString" (:reference (:medication M))))))
                    (retrieve "MedicationAdministration")))
                  (exists
                   (eduction-query
                    (filter
                     (fn [M]
                       (contains
                        (expr-ref "MedicationRefsB")
                        (call "ToString" (:reference (:medication M))))))
                    (retrieve "MedicationAdministration")))))

              (testing "the whole exists expression optimizes to false"
                (let [db (d/db node)]
                  (given (->> (library/eval-unfiltered {:db db
                                                        :now (time/offset-date-time)}
                                                       expression-defs)
                              (library/resolve-all-refs)
                              (library/optimize db))
                    ["InInitialPopulation" :context] := "Patient"
                    ["InInitialPopulation" expr-form] := false)))))))

      (testing "with the second query having Medication refs"
        (let [library (t/translate "library Retrieve
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        codesystem atc: 'http://fhir.de/CodeSystem/bfarm/atc'

        context Unfiltered

        define MedicationRefsA:
          from [Medication: Code 'A10BF01' from atc] M
            return 'Medication/' + M.id

        context Patient

        define InInitialPopulation:
          exists (from [MedicationAdministration] M
            where M.medication.reference in MedicationRefsA) or
          exists (from [MedicationAdministration] M
            where M.medication.reference in {'Medication/0'})")]
          (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
            (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library default-opts)]
              (given expression-defs
                ["InInitialPopulation" :context] := "Patient"
                ["InInitialPopulation" expr-form] :=
                '(or
                  (exists
                   (eduction-query
                    (filter
                     (fn [M]
                       (contains
                        (expr-ref "MedicationRefsA")
                        (call "ToString" (:reference (:medication M))))))
                    (retrieve "MedicationAdministration")))
                  (exists
                   (eduction-query
                    (filter
                     (fn [M]
                       (contains
                        ["Medication/0"]
                        (call "ToString" (:reference (:medication M))))))
                    (retrieve "MedicationAdministration")))))

              (testing "the first query optimizes away"
                (let [db (d/db node)]
                  (given (->> (library/eval-unfiltered {:db db
                                                        :now (time/offset-date-time)}
                                                       expression-defs)
                              (library/resolve-all-refs)
                              (library/optimize db))
                    ["InInitialPopulation" :context] := "Patient"
                    ["InInitialPopulation" expr-form] :=
                    '(exists
                      (eduction-query
                       (matcher [["medication" "Medication/0"]])
                       (retrieve "MedicationAdministration"))))))))))))

  (testing "with compile-time error"
    (testing "function"
      (let [library (t/translate "library Test
          define function Error(): singleton from {1, 2}")]
        (with-system [system mem-node-config]
          (given (library/compile-library (compile-context system) library default-opts)
            ::anom/category := ::anom/conflict
            ::anom/message := "More than one element in `SingletonFrom` expression."))))

    (testing "expression"
      (let [library (t/translate "library Test
          define Error: singleton from {1, 2}")]
        (with-system [system mem-node-config]
          (given (library/compile-library (compile-context system) library default-opts)
            ::anom/category := ::anom/conflict
            ::anom/message := "More than one element in `SingletonFrom` expression.")))))

  (testing "one parameter"
    (let [library (t/translate "library Test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        parameter Gender String

        context Patient

        define InInitialPopulation:
          Patient.gender = Gender")]
      (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library default-opts)]
          (given expression-defs
            ["InInitialPopulation" :context] := "Patient"
            ["InInitialPopulation" expr-form] :=
            '(equal
              (call "ToString" (:gender (singleton-from (retrieve-resource))))
              (param-ref "Gender")))

          (testing "there are no references to resolve"
            (is (= expression-defs (library/resolve-all-refs expression-defs))))

          (testing "resolving parameters"
            (given (library/resolve-params expression-defs {"Gender" "female"})
              ["InInitialPopulation" :context] := "Patient"
              ["InInitialPopulation" expr-form] :=
              '(equal
                (call "ToString" (:gender (singleton-from (retrieve-resource))))
                "female")))

          (testing "there are no optimizations available"
            (is (= expression-defs (library/optimize (d/db node) expression-defs))))))))

  (testing "with parameter default"
    (let [library (t/translate "library Test
        parameter \"Measurement Period\" Interval<Date> default Interval[@2020-01-01, @2020-12-31]")]
      (with-system [system mem-node-config]
        (given (library/compile-library (compile-context system) library default-opts)
          [:parameter-default-values "Measurement Period" :low] := #system/date"2020-01-01"
          [:parameter-default-values "Measurement Period" :high] := #system/date"2020-12-31"))))

  (testing "with invalid parameter default"
    (let [library (t/translate "library Test
        parameter \"Measurement Start\" Integer default singleton from {1, 2}")]
      (with-system [system mem-node-config]
        (given (library/compile-library (compile-context system) library default-opts)
          ::anom/category := ::anom/conflict
          ::anom/message "More than one element in `SingletonFrom` expression."))))

  (testing "query with semi-join"
    (let [library (t/translate "library \"q50-specimen-condition-reference\"
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        context Patient

        define InInitialPopulation:
            exists [Observation] O
              with [Encounter] E
              such that O.encounter.reference = 'Encounter/' + E.id")]
      (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
        (let [{:keys [expression-defs] :as context}
              (library/compile-library (compile-context system) library {})]
          (given expression-defs
            ["InInitialPopulation" :context] := "Patient"
            ["InInitialPopulation" expr-form] :=
            '(exists
              (eduction-query
               (filter
                (fn [O]
                  (exists
                   (fn [E]
                     (equal
                      (call
                       "ToString"
                       (:reference
                        (:encounter
                         O)))
                      (concatenate
                       "Encounter/"
                       (call "ToString" (:id E)))))
                   (retrieve "Encounter"))))
               (retrieve "Observation"))))

          (given context
            [(function-def "hasDiagnosis" 1) :function] := nil)

          (testing "there are no references to resolve"
            (is (= expression-defs (library/resolve-all-refs expression-defs))))

          (testing "there are no optimizations available"
            (is (= expression-defs (library/optimize (d/db node) expression-defs))))))))

  (testing "with related context"
    (let [library (t/translate "library test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        context Patient

        define \"name-133756\":
          singleton from ([Patient])

        define InInitialPopulation:
          [\"name-133756\" -> Observation]")]
      (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library {})]
          (given expression-defs
            ["InInitialPopulation" :context] := "Patient"
            ["InInitialPopulation" expr-form] :=
            '(retrieve
              (singleton-from
               (retrieve-resource))
              "Observation"))

          (testing "there are no references to resolve"
            (is (= expression-defs (library/resolve-all-refs expression-defs))))

          (testing "there are no optimizations available"
            (is (= expression-defs (library/optimize (d/db node) expression-defs))))))))

  (testing "retrieve with static concept"
    (let [library (t/translate "library test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        codesystem icd10: 'http://hl7.org/fhir/sid/icd-10'
        codesystem sct: 'http://snomed.info/sct'

        code \"ICD-10: C61\": 'C61' from icd10
        code \"SNOMED: 254900004\": '254900004' from sct

        concept prostata: {\"ICD-10: C61\", \"SNOMED: 254900004\"}

        define InInitialPopulation:
          exists [Condition: prostata]")]
      (with-system [system mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library {})]
          (given expression-defs
            ["InInitialPopulation" :context] := "Patient"
            ["InInitialPopulation" expr-form] :=
            '(exists
              (retrieve
               "Condition"
               [["code"
                 "http://hl7.org/fhir/sid/icd-10|C61"
                 "http://snomed.info/sct|254900004"]])))

          (testing "there are no references to resolve"
            (is (= expression-defs (library/resolve-all-refs expression-defs))))))))

  (testing "and expression"
    (let [library (t/translate "library test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        context Patient

        define InInitialPopulation:
          exists [Observation] and
          exists [Condition] and
          exists [Encounter] and
          exists [Specimen]")]
      (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library {})]
          (given expression-defs
            ["InInitialPopulation" :context] := "Patient"
            ["InInitialPopulation" expr-form] :=
            '(and
              (and
               (and
                (exists (retrieve "Observation"))
                (exists (retrieve "Condition")))
               (exists (retrieve "Encounter")))
              (exists (retrieve "Specimen"))))

          (testing "there are no references to resolve"
            (is (= expression-defs (library/resolve-all-refs expression-defs))))

          (testing "there are no optimizations available"
            (is (= expression-defs (library/optimize (d/db node) expression-defs))))))))

  (testing "and expression with named expressions"
    (let [library (t/translate "library test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        context Patient

        define Criterion_1:
          exists [Observation] and
          exists [Condition]

        define Criterion_2:
          exists [Encounter] and
          exists [Specimen]

        define InInitialPopulation:
          Criterion_1 and
          Criterion_2")]
      (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library default-opts)
              in-initial-population (get expression-defs "InInitialPopulation")]

          (testing "after compilation the named expressions are resolved
                    the structure however is retained
                    so the and-expressions are still binary"
            (given in-initial-population
              :context := "Patient"
              expr-form :=
              '(and
                (and
                 (exists (retrieve "Observation"))
                 (exists (retrieve "Condition")))
                (and
                 (exists (retrieve "Encounter"))
                 (exists (retrieve "Specimen"))))))

          (testing "after attaching the cache we get one single, flat and-expression"
            (with-redefs [ec/get (fn [_ _])]
              (let [{:keys [expression]} in-initial-population]
                (given (st/with-instrument-disabled (c/attach-cache expression ::cache))
                  [0 c/form] :=
                  '(and
                    (exists (retrieve "Observation"))
                    (exists (retrieve "Condition"))
                    (exists (retrieve "Encounter"))
                    (exists (retrieve "Specimen")))))))

          (testing "there are no references to resolve"
            (is (= expression-defs (library/resolve-all-refs expression-defs))))

          (testing "there are no optimizations available"
            (is (= expression-defs (library/optimize (d/db node) expression-defs))))))))

  (testing "or expression"
    (let [library (t/translate "library test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        context Patient

        define InInitialPopulation:
          exists [Observation] or
          exists [Condition] or
          exists [Encounter] or
          exists [Specimen]")]
      (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library {})]
          (given expression-defs
            ["InInitialPopulation" :context] := "Patient"
            ["InInitialPopulation" expr-form] :=
            '(or
              (or
               (or
                (exists (retrieve "Observation"))
                (exists (retrieve "Condition")))
               (exists (retrieve "Encounter")))
              (exists (retrieve "Specimen"))))

          (testing "there are no references to resolve"
            (is (= expression-defs (library/resolve-all-refs expression-defs))))

          (testing "there are no optimizations available"
            (is (= expression-defs (library/optimize (d/db node) expression-defs))))))))

  (testing "or expression with named expressions"
    (let [library (t/translate "library test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        context Patient

        define Criterion_1:
          exists [Observation] or
          exists [Condition]

        define Criterion_2:
          exists [Encounter] or
          exists [Specimen]

        define InInitialPopulation:
          Criterion_1 or
          Criterion_2")]
      (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library default-opts)
              in-initial-population (get expression-defs "InInitialPopulation")]

          (testing "after compilation the named expressions are resolved
                    the structure however is retained
                    so the or-expressions are still binary"
            (given in-initial-population
              :context := "Patient"
              expr-form :=
              '(or
                (or
                 (exists (retrieve "Observation"))
                 (exists (retrieve "Condition")))
                (or
                 (exists (retrieve "Encounter"))
                 (exists (retrieve "Specimen"))))))

          (testing "after attaching the cache we get one single, flat or-expression"
            (with-redefs [ec/get (fn [_ _])]
              (let [{:keys [expression]} in-initial-population]
                (given (st/with-instrument-disabled (c/attach-cache expression ::cache))
                  [0 c/form] :=
                  '(or
                    (exists (retrieve "Observation"))
                    (exists (retrieve "Condition"))
                    (exists (retrieve "Encounter"))
                    (exists (retrieve "Specimen")))))))

          (testing "there are no references to resolve"
            (is (= expression-defs (library/resolve-all-refs expression-defs))))

          (testing "there are no optimizations available"
            (is (= expression-defs (library/optimize (d/db node) expression-defs))))))))

  (testing "mixed and and or expressions"
    (let [library (t/translate "library test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        context Patient

        define Criterion_1:
          exists [Observation] or
          exists [Condition]

        define Criterion_2:
          exists [Encounter] or
          exists [Specimen]

        define InInitialPopulation:
          Criterion_1 and
          Criterion_2")]
      (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library default-opts)
              in-initial-population (get expression-defs "InInitialPopulation")]

          (testing "after compilation the named expressions are resolved
                    the structure however is retained
                    so the or-expressions are still binary"
            (given in-initial-population
              :context := "Patient"
              expr-form :=
              '(and
                (or
                 (exists (retrieve "Observation"))
                 (exists (retrieve "Condition")))
                (or
                 (exists (retrieve "Encounter"))
                 (exists (retrieve "Specimen"))))))

          (testing "after attaching the cache the expressions don't change"
            (with-redefs [ec/get (fn [_ _])]
              (let [{:keys [expression]} in-initial-population]
                (given (st/with-instrument-disabled (c/attach-cache expression ::cache))
                  [0 c/form] :=
                  '(and
                    (or
                     (exists (retrieve "Observation"))
                     (exists (retrieve "Condition")))
                    (or
                     (exists (retrieve "Encounter"))
                     (exists (retrieve "Specimen"))))))))

          (testing "there are no references to resolve"
            (is (= expression-defs (library/resolve-all-refs expression-defs))))

          (testing "there are no optimizations available"
            (is (= expression-defs (library/optimize (d/db node) expression-defs))))))))

  (testing "Equivalent on Concept"
    (let [library (t/translate "library test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        codesystem IdentifierType: 'http://fhir.de/CodeSystem/identifier-type-de-basis'

        context Patient

        define InInitialPopulation:
          Patient.identifier.where(type ~ Code 'GKV' from IdentifierType).exists()")]
      (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library {})]
          (given expression-defs
            ["InInitialPopulation" :context] := "Patient"
            ["InInitialPopulation" expr-form] :=
            '(exists
              (eduction-query
               (filter
                (fn [$this]
                  (equivalent
                   (call
                    "ToConcept"
                    (:type
                     $this))
                   (concept
                    (code
                     "http://fhir.de/CodeSystem/identifier-type-de-basis"
                     nil
                     "GKV")))))
               (:identifier
                (singleton-from
                 (retrieve-resource))))))

          (testing "there are no references to resolve"
            (is (= expression-defs (library/resolve-all-refs expression-defs))))

          (testing "there are no optimizations available"
            (is (= expression-defs (library/optimize (d/db node) expression-defs))))))))

  (testing "Retrieve on primary code"
    (let [library (t/translate "library test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        codesystem loinc: 'http://loinc.org'

        context Patient

        define InInitialPopulation:
          exists [Observation: Code '788-0' from loinc]")]
      (with-system-data [{:blaze.db/keys [node] :as system} mem-node-config]
        [[[:put {:fhir/type :fhir/Observation :id "0"
                 :code (codeable-concept "http://loinc.org" "788-0")}]]]

        (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library {})]
          (given expression-defs
            ["InInitialPopulation" :context] := "Patient"
            ["InInitialPopulation" expr-form] :=
            '(exists
              (retrieve "Observation" [["code" "http://loinc.org|788-0"]])))

          (testing "there are no references to resolve"
            (is (= expression-defs (library/resolve-all-refs expression-defs))))

          (testing "there are no optimizations available"
            (is (= expression-defs (library/optimize (d/db node) expression-defs)))))))

    (testing "and no available Observations"
      (let [library (t/translate "library test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        codesystem loinc: 'http://loinc.org'

        context Patient

        define InInitialPopulation:
          exists [Observation: Code '788-0' from loinc]")]
        (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
          (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library {})]
            (given expression-defs
              ["InInitialPopulation" :context] := "Patient"
              ["InInitialPopulation" expr-form] :=
              '(exists
                (retrieve "Observation" [["code" "http://loinc.org|788-0"]])))

            (testing "there are no references to resolve"
              (is (= expression-defs (library/resolve-all-refs expression-defs))))

            (testing "the whole expression will be optimized to false"
              (given (library/optimize (d/db node) expression-defs)
                ["InInitialPopulation" :context] := "Patient"
                ["InInitialPopulation" expr-form] := false))))))

    (testing "with value set"
      (let [library (t/translate "library test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        valueset vs: 'http://fhir.org/VCL?v1=(http://system-115910)*'

        context Patient

        define InInitialPopulation:
          exists [Condition: vs]")]
        (with-system-data [{:blaze.db/keys [node] :as system} mem-node-config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "http://system-115910"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-115927"}
                    {:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-140541"}]}]]
           [[:put {:fhir/type :fhir/Condition :id "0"
                   :code (codeable-concept "http://system-115910" "code-115927")}]]]

          (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library {})]
            (given expression-defs
              ["InInitialPopulation" :context] := "Patient"
              ["InInitialPopulation" expr-form] :=
              '(exists
                (retrieve "Condition" [["code"
                                        "http://system-115910|code-140541"
                                        "http://system-115910|code-115927"]])))

            (testing "there are no references to resolve"
              (is (= expression-defs (library/resolve-all-refs expression-defs))))

            (testing "there are no optimizations available"
              (is (= expression-defs (library/optimize (d/db node) expression-defs)))))))))

  (testing "query with where clause"
    (let [library (t/translate "library test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        codesystem loinc: 'http://loinc.org'
        code body_weight: '29463-7' from loinc

        context Patient

        define InInitialPopulation:
          exists [Observation: body_weight] O where O.value < 3.3 'kg'")]
      (with-system-data [{:blaze.db/keys [node] :as system} mem-node-config]
        [[[:put {:fhir/type :fhir/Observation :id "0"
                 :code (codeable-concept "http://loinc.org" "29463-7")}]]]

        (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library {})]
          (given expression-defs
            ["InInitialPopulation" :context] := "Patient"
            ["InInitialPopulation" expr-form] :=
            '(exists
              (eduction-query
               (filter
                (fn [O]
                  (less (call "ToQuantity" (as fhir/Quantity (:value O)))
                        (quantity 3.3M "kg"))))
               (retrieve "Observation" [["code" "http://loinc.org|29463-7"]]))))

          (testing "there are no references to resolve"
            (is (= expression-defs (library/resolve-all-refs expression-defs))))

          (testing "there are no optimizations available"
            (is (= expression-defs (library/optimize (d/db node) expression-defs)))))))

    (testing "and no available Observations"
      (let [library (t/translate "library test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        codesystem loinc: 'http://loinc.org'
        code body_weight: '29463-7' from loinc

        context Patient

        define InInitialPopulation:
          exists [Observation: body_weight] O where O.value < 3.3 'kg'")]
        (with-system [{:blaze.db/keys [node] :as system} mem-node-config]
          (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library {})]
            (given expression-defs
              ["InInitialPopulation" :context] := "Patient"
              ["InInitialPopulation" expr-form] :=
              '(exists
                (eduction-query
                 (filter
                  (fn [O]
                    (less (call "ToQuantity" (as fhir/Quantity (:value O)))
                          (quantity 3.3M "kg"))))
                 (retrieve "Observation" [["code" "http://loinc.org|29463-7"]]))))

            (testing "there are no references to resolve"
              (is (= expression-defs (library/resolve-all-refs expression-defs))))

            (testing "the whole expression will be optimized to false"
              (given (library/optimize (d/db node) expression-defs)
                ["InInitialPopulation" :context] := "Patient"
                ["InInitialPopulation" expr-form] := false)))))))

  (testing "FHIR Period and overlaps"
    (let [library (t/translate "library test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        codesystem snomed: 'http://snomed.info/sct'

        context Patient

        define InInitialPopulation:
          exists
            from [Procedure: Code '431182000' from snomed] P
            where P.performed overlaps Interval[@2020-02-01, @2020-06-01]")]
      (with-system [system mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library {})]
          (given expression-defs
            ["InInitialPopulation" :context] := "Patient"
            ["InInitialPopulation" expr-form] :=
            '(exists
              (eduction-query
               (filter
                (fn
                  [P]
                  (overlaps
                   (call
                    "ToInterval"
                    (as
                     fhir/Period
                     (:performed
                      P)))
                   (interval
                    #system/date-time"2020-02-01"
                    #system/date-time"2020-06-01"))))
               (retrieve
                "Procedure"
                [["code"
                  "http://snomed.info/sct|431182000"]]))))))))

  (testing "valueset"
    (let [library (t/translate "library test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        valueset FemaleAdministrativeSex:
          'urn:oid:2.16.840.1.113883.3.560.100.2'

        context Patient

        define InInitialPopulation:
          Patient.gender in FemaleAdministrativeSex")]
      (with-system-data [system mem-node-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "urn:oid:2.16.840.1.113883.3.560.100.2"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://hl7.org/fhir/administrative-gender"
                    :concept
                    [{:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code "female"}]}]}
                 :expansion
                 {:fhir/type :fhir.ValueSet/expansion
                  :identifier #fhir/uri "urn:uuid:b01db38a-3ec8-4167-a279-0bb1200624a8"
                  :timestamp #fhir/dateTime #system/date-time "1970-01-01T00:00:00Z"
                  :contains
                  [{:fhir/type :fhir.ValueSet.expansion/contains
                    :system #fhir/uri "http://hl7.org/fhir/administrative-gender"
                    :code #fhir/code "female"}]}}]]]

        (let [{:keys [expression-defs]} (library/compile-library (compile-context system) library {})]
          (given expression-defs
            ["InInitialPopulation" :context] := "Patient"
            ["InInitialPopulation" expr-form] :=
            '(in-value-set
              (call "ToString" (:gender (singleton-from (retrieve-resource))))
              (value-set "urn:oid:2.16.840.1.113883.3.560.100.2"))))))))
