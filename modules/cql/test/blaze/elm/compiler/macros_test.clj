(ns blaze.elm.compiler.macros-test
  (:require
   [blaze.elm.compiler.macros :refer [reify-expr]]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest reify-expr-test
  (testing "the -form method is mandatory"
    (given-thrown (macroexpand-1 `(reify-expr core/Expression))
      [:cause-data :message] := "The `-form` method is missing in `reify-expr`.")))
