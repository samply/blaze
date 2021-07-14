(ns blaze.elm.compiler.library-test
  (:require
    [blaze.cql-translator :as t]
    [blaze.db.api-stub :refer [mem-node]]
    [blaze.elm.compiler.library :as library]
    [blaze.elm.compiler.library-spec]
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


(deftest compile-library-test
  (testing "empty library"
    (let [library (t/translate "library Test")]
      (with-open [node (mem-node)]
        (given (library/compile-library node library {})
          :life/compiled-expression-defs := {}))))

  (testing "one static expression"
    (let [library (t/translate "library Test define Foo: true")]
      (with-open [node (mem-node)]
        (given (library/compile-library node library {})
          :life/compiled-expression-defs := {"Foo" true}))))

  (testing "one static expression"
    (let [library (t/translate "library Test
    using FHIR version '4.0.0'
    context Patient
    define Gender: Patient.gender")]
      (with-open [node (mem-node)]
        (given (library/compile-library node library {})
          [:life/compiled-expression-defs "Gender" :key] := :gender
          [:life/compiled-expression-defs "Gender" :source :name] := "Patient"))))

  (testing "one static expression"
    (let [library (t/translate "library Test
    define Error: singleton from {1, 2}")]
      (with-open [node (mem-node)]
        (given (library/compile-library node library {})
          ::anom/category := ::anom/conflict
          ::anom/message := "More than one element in `SingletonFrom` expression.")))))
