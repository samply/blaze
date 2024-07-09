(ns blaze.elm.compiler.library-test
  (:require
   [blaze.cql-translator :as t]
   [blaze.db.api-stub :refer [mem-node-config]]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler-spec]
   [blaze.elm.compiler.library :as library]
   [blaze.elm.compiler.library-spec]
   [blaze.elm.expression.cache :as ec]
   [blaze.fhir.spec.type.system]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private default-opts {})

(def ^:private expr-form (comp c/form :expression))

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
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (given (library/compile-library node library default-opts)
          :expression-defs := {}))))

  (testing "one static expression"
    (let [library (t/translate "library Test define Foo: true")]
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library node library default-opts)]
          (given expression-defs
            ["Foo" :context] := "Patient"
            ["Foo" :expression] := true)))))

  (testing "one dynamic expression"
    (let [library (t/translate "library Test
        using FHIR version '4.0.0'
        context Patient
        define Gender: Patient.gender")]
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library node library default-opts)]
          (given expression-defs
            ["Gender" :context] := "Patient"
            ["Gender" expr-form] := '(:gender (singleton-from (retrieve-resource))))))))

  (testing "one function"
    (let [library (t/translate "library Test
        using FHIR version '4.0.0'
        context Patient
        define function Gender(P Patient): P.gender
        define InInitialPopulation: Gender(Patient)")]
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (let [{:keys [expression-defs function-defs]} (library/compile-library node library default-opts)]
          (given expression-defs
            ["InInitialPopulation" :context] := "Patient"
            ["InInitialPopulation" :resultTypeName] := "{http://hl7.org/fhir}AdministrativeGender"
            ["InInitialPopulation" expr-form] := '(call "Gender" (singleton-from (retrieve-resource))))

          (given function-defs
            ["Gender" :context] := "Patient"
            ["Gender" :resultTypeName] := "{http://hl7.org/fhir}AdministrativeGender"
            ["Gender" :function] :? fn?)))))

  (testing "two functions, one calling the other"
    (let [library (t/translate "library Test
        using FHIR version '4.0.0'
        context Patient
        define function Inc(i System.Integer): i + 1
        define function Inc2(i System.Integer): Inc(i) + 1
        define InInitialPopulation: Inc2(1)")]
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library node library default-opts)]
          (given expression-defs
            ["InInitialPopulation" :context] := "Patient"
            ["InInitialPopulation" expr-form] := '(call "Inc2" 1))))))

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
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library node library default-opts)]
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
               (exists
                (retrieve
                 "Observation")))
              (not
               (exists
                (retrieve
                 "Condition")))))))))

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
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library node library default-opts)]
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
               [["code"
                 "http://fhir.de/CodeSystem/dimdi/atc|L01AX03"]]))

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
                 (fn
                   [M]
                   (contains
                    (expr-ref
                     "TemozolomidRefs")
                    (call
                     "ToString"
                     (:reference
                      (:medication
                       M))))))
                (retrieve
                 "MedicationStatement")))))))))

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
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library node library default-opts)]
          (given expression-defs
            ["Patient" :context] := "Patient"
            ["Patient" expr-form] := '(singleton-from (retrieve-resource))
            ["InInitialPopulation" :context] := "Patient"
            ["InInitialPopulation" expr-form] := true
            ["AllEncounters" :context] := "Patient"
            ["AllEncounters" expr-form] := '(retrieve "Encounter")
            ["Gender" :context] := "Patient"
            ["Gender" expr-form] := '(:gender (singleton-from (retrieve-resource))))))))

  (testing "with compile-time error"
    (testing "function"
      (let [library (t/translate "library Test
          define function Error(): singleton from {1, 2}")]
        (with-system [{:blaze.db/keys [node]} mem-node-config]
          (given (library/compile-library node library default-opts)
            ::anom/category := ::anom/conflict
            ::anom/message := "More than one element in `SingletonFrom` expression."))))

    (testing "expression"
      (let [library (t/translate "library Test
          define Error: singleton from {1, 2}")]
        (with-system [{:blaze.db/keys [node]} mem-node-config]
          (given (library/compile-library node library default-opts)
            ::anom/category := ::anom/conflict
            ::anom/message := "More than one element in `SingletonFrom` expression.")))))

  (testing "with parameter default"
    (let [library (t/translate "library Test
        parameter \"Measurement Period\" Interval<Date> default Interval[@2020-01-01, @2020-12-31]")]
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (given (library/compile-library node library default-opts)
          [:parameter-default-values "Measurement Period" :start] := #system/date"2020-01-01"
          [:parameter-default-values "Measurement Period" :end] := #system/date"2020-12-31"))))

  (testing "with invalid parameter default"
    (let [library (t/translate "library Test
        parameter \"Measurement Start\" Integer default singleton from {1, 2}")]
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (given (library/compile-library node library default-opts)
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
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (given (library/compile-library node library {})
          [:expression-defs "InInitialPopulation" :context] := "Patient"
          [:expression-defs "InInitialPopulation" expr-form] :=
          '(exists
            (eduction-query
             (filter
              (fn
                [O]
                (exists
                 (fn
                   [E]
                   (equal
                    (call
                     "ToString"
                     (:reference
                      (:encounter
                       O)))
                    (concatenate
                     "Encounter/"
                     (call
                      "ToString"
                      (:id
                       E)))))
                 (retrieve
                  "Encounter"))))
             (retrieve
              "Observation")))
          [:function-defs "hasDiagnosis" :function] := nil))))

  (testing "with related context"
    (let [library (t/translate "library test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        context Patient

        define \"name-133756\":
          singleton from ([Patient])

        define InInitialPopulation:
          [\"name-133756\" -> Observation]")]
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (given (library/compile-library node library {})
          [:expression-defs "InInitialPopulation" :context] := "Patient"
          [:expression-defs "InInitialPopulation" expr-form] :=
          '(retrieve
            (singleton-from
             (retrieve-resource))
            "Observation")))))

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
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (given (library/compile-library node library {})
          [:expression-defs "InInitialPopulation" :context] := "Patient"
          [:expression-defs "InInitialPopulation" expr-form] :=
          '(and
            (and
             (and
              (exists
               (retrieve
                "Observation"))
              (exists
               (retrieve
                "Condition")))
             (exists
              (retrieve
               "Encounter")))
            (exists
             (retrieve
              "Specimen")))))))

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
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library node library default-opts)
              in-initial-population (get expression-defs "InInitialPopulation")]

          (testing "after compilation the named expressions are resolved
                    the structure however is retained
                    so the and-expressions are still binary"
            (given in-initial-population
              :context := "Patient"
              expr-form :=
              '(and
                (and
                 (exists
                  (retrieve
                   "Observation"))
                 (exists
                  (retrieve
                   "Condition")))
                (and
                 (exists
                  (retrieve
                   "Encounter"))
                 (exists
                  (retrieve
                   "Specimen"))))))

          (testing "after attaching the cache we get one single, flat and-expression"
            (with-redefs [ec/get (fn [_ _])]
              (let [{:keys [expression]} in-initial-population]
                (given (st/with-instrument-disabled (c/attach-cache expression ::cache))
                  [0 c/form] :=
                  '(and
                    (exists
                     (retrieve
                      "Observation"))
                    (exists
                     (retrieve
                      "Condition"))
                    (exists
                     (retrieve
                      "Encounter"))
                    (exists
                     (retrieve
                      "Specimen")))))))))))

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
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (given (library/compile-library node library {})
          [:expression-defs "InInitialPopulation" :context] := "Patient"
          [:expression-defs "InInitialPopulation" expr-form] :=
          '(or
            (or
             (or
              (exists
               (retrieve
                "Observation"))
              (exists
               (retrieve
                "Condition")))
             (exists
              (retrieve
               "Encounter")))
            (exists
             (retrieve
              "Specimen")))))))

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
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library node library default-opts)
              in-initial-population (get expression-defs "InInitialPopulation")]

          (testing "after compilation the named expressions are resolved
                    the structure however is retained
                    so the or-expressions are still binary"
            (given in-initial-population
              :context := "Patient"
              expr-form :=
              '(or
                (or
                 (exists
                  (retrieve
                   "Observation"))
                 (exists
                  (retrieve
                   "Condition")))
                (or
                 (exists
                  (retrieve
                   "Encounter"))
                 (exists
                  (retrieve
                   "Specimen"))))))

          (testing "after attaching the cache we get one single, flat or-expression"
            (with-redefs [ec/get (fn [_ _])]
              (let [{:keys [expression]} in-initial-population]
                (given (st/with-instrument-disabled (c/attach-cache expression ::cache))
                  [0 c/form] :=
                  '(or
                    (exists
                     (retrieve
                      "Observation"))
                    (exists
                     (retrieve
                      "Condition"))
                    (exists
                     (retrieve
                      "Encounter"))
                    (exists
                     (retrieve
                      "Specimen")))))))))))

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
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (let [{:keys [expression-defs]} (library/compile-library node library default-opts)
              in-initial-population (get expression-defs "InInitialPopulation")]

          (testing "after compilation the named expressions are resolved
                    the structure however is retained
                    so the or-expressions are still binary"
            (given in-initial-population
              :context := "Patient"
              expr-form :=
              '(and
                (or
                 (exists
                  (retrieve
                   "Observation"))
                 (exists
                  (retrieve
                   "Condition")))
                (or
                 (exists
                  (retrieve
                   "Encounter"))
                 (exists
                  (retrieve
                   "Specimen"))))))

          (testing "after attaching the cache the expressions don't change"
            (with-redefs [ec/get (fn [_ _])]
              (let [{:keys [expression]} in-initial-population]
                (given (st/with-instrument-disabled (c/attach-cache expression ::cache))
                  [0 c/form] :=
                  '(and
                    (or
                     (exists
                      (retrieve
                       "Observation"))
                     (exists
                      (retrieve
                       "Condition")))
                    (or
                     (exists
                      (retrieve
                       "Encounter"))
                     (exists
                      (retrieve
                       "Specimen"))))))))))))

  (let [library (t/translate "library test
        using FHIR version '4.0.0'
        include FHIRHelpers version '4.0.0'

        codesystem IdentifierType: 'http://fhir.de/CodeSystem/identifier-type-de-basis'

        context Patient

        define InInitialPopulation:
          Patient.identifier.where(type ~ Code 'GKV' from IdentifierType).exists()")]
    (with-system [{:blaze.db/keys [node]} mem-node-config]
      (given (library/compile-library node library {})
        [:expression-defs "InInitialPopulation" :context] := "Patient"
        [:expression-defs "InInitialPopulation" expr-form] :=
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
             (retrieve-resource)))))))))
