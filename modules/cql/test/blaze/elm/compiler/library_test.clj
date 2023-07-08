(ns blaze.elm.compiler.library-test
  (:require
    [blaze.cql-translator :as t]
    [blaze.db.api-stub :refer [mem-node-config]]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.library :as library]
    [blaze.elm.compiler.library-spec]
    [blaze.fhir.spec.type.system]
    [blaze.test-util :refer [with-system]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


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
        (given (library/compile-library node library {})
          :expression-defs := {}))))

  (testing "one static expression"
    (let [library (t/translate "library Test define Foo: true")]
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (given (library/compile-library node library {})
          [:expression-defs "Foo" :context] := "Patient"
          [:expression-defs "Foo" :expression] := true))))

  (testing "one dynamic expression"
    (let [library (t/translate "library Test
      using FHIR version '4.0.0'
      context Patient
      define Gender: Patient.gender")]
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (given (library/compile-library node library {})
          [:expression-defs "Gender" :context] := "Patient"
          [:expression-defs "Gender" :expression c/form] := '(:gender (expr-ref "Patient"))))))

  (testing "one function"
    (let [library (t/translate "library Test
      using FHIR version '4.0.0'
      context Patient
      define function Gender(P Patient): P.gender
      define InInitialPopulation: Gender(Patient)")]
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (given (library/compile-library node library {})
          [:expression-defs "InInitialPopulation" :context] := "Patient"
          [:expression-defs "InInitialPopulation" :resultTypeName] := "{http://hl7.org/fhir}AdministrativeGender"
          [:expression-defs "InInitialPopulation" :expression c/form] := '(call "Gender" (expr-ref "Patient"))
          [:function-defs "Gender" :context] := "Patient"
          [:function-defs "Gender" :resultTypeName] := "{http://hl7.org/fhir}AdministrativeGender"
          [:function-defs "Gender" :function] :? fn?))))

  (testing "two functions, one calling the other"
    (let [library (t/translate "library Test
      using FHIR version '4.0.0'
      context Patient
      define function Inc(i System.Integer): i + 1
      define function Inc2(i System.Integer): Inc(i) + 1
      define InInitialPopulation: Inc2(1)")]
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (given (library/compile-library node library {})
          [:expression-defs "InInitialPopulation" :context] := "Patient"
          [:expression-defs "InInitialPopulation" :expression c/form] := '(call "Inc2" 1)))))

  (testing "with compile-time error"
    (testing "function"
      (let [library (t/translate "library Test
          define function Error(): singleton from {1, 2}")]
        (with-system [{:blaze.db/keys [node]} mem-node-config]
          (given (library/compile-library node library {})
            ::anom/category := ::anom/conflict
            ::anom/message := "More than one element in `SingletonFrom` expression."))))

    (testing "expression"
      (let [library (t/translate "library Test
        define Error: singleton from {1, 2}")]
        (with-system [{:blaze.db/keys [node]} mem-node-config]
          (given (library/compile-library node library {})
            ::anom/category := ::anom/conflict
            ::anom/message := "More than one element in `SingletonFrom` expression.")))))

  (testing "with parameter default"
    (let [library (t/translate "library Test
    parameter \"Measurement Period\" Interval<Date> default Interval[@2020-01-01, @2020-12-31]")]
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (given (library/compile-library node library {})
          [:parameter-default-values "Measurement Period" :start] := #system/date"2020-01-01"
          [:parameter-default-values "Measurement Period" :end] := #system/date"2020-12-31"))))

  (testing "with invalid parameter default"
    (let [library (t/translate "library Test
    parameter \"Measurement Start\" Integer default singleton from {1, 2}")]
      (with-system [{:blaze.db/keys [node]} mem-node-config]
        (given (library/compile-library node library {})
          ::anom/category := ::anom/conflict
          ::anom/message "More than one element in `SingletonFrom` expression.")))))
