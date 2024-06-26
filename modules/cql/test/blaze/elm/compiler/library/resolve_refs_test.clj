(ns blaze.elm.compiler.library.resolve-refs-test
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.library-spec]
   [blaze.elm.compiler.library.resolve-refs :refer [resolve-refs]]
   [blaze.elm.compiler.macros :refer [reify-expr]]
   [blaze.fhir.spec.type.system]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private expr-a-ref-b
  (reify-expr core/Expression
    (-form [_]
      (list 'a (list 'expr-ref "b")))))

(deftest resolve-refs-test
  (testing "with one unresolvable ref"
    (given (resolve-refs #{} {"a" {:expression expr-a-ref-b}})
      ::anom/category := ::anom/incorrect
      ::anom/message := "The following expression definitions contain unresolvable references: a.")))
