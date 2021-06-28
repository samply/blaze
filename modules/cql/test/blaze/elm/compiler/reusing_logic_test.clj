(ns blaze.elm.compiler.reusing-logic-test
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [blaze.elm.quantity :as quantity]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]))


(st/instrument)
(tu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (tu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; 9.2. ExpressionRef
;;
;; The ExpressionRef type defines an expression that references a previously
;; defined NamedExpression. The result of evaluating an ExpressionReference is
;; the result of evaluating the referenced NamedExpression.
(deftest compile-expression-ref-test
  (testing "Throws error on missing expression"
    (is (thrown-anom? ::anom/incorrect (c/compile {} #elm/expression-ref "name-170312"))))

  (testing "Result Type"
    (let [library {:statements {:def [{:name "name-170312" :resultTypeName "result-type-name-173029"}]}}
          expr (c/compile {:library library} #elm/expression-ref "name-170312")]
      (is (= "result-type-name-173029" (:result-type-name (meta expr))))))

  (testing "Eval"
    (let [library {:statements {:def [{:name "name-170312"}]}}
          expr (c/compile {:library library} #elm/expression-ref "name-170312")]
      (is (= ::result (core/-eval expr {:library-context {"name-170312" ::result}} nil nil))))))


;; 9.4. FunctionRef
(deftest compile-function-ref-test
  (testing "ToString"
    (are [elm res]
      (= res (core/-eval (c/compile {} elm) {} nil nil))
      {:type "FunctionRef"
       :libraryName "FHIRHelpers"
       :name "ToString"
       :operand [#elm/string "foo"]}
      "foo"))

  (testing "ToQuantity"
    (with-open [node (mem-node-with [])]
      (let [context {:eval-context "Patient" :node node}
            elm {:type "FunctionRef"
                 :libraryName "FHIRHelpers"
                 :name "ToQuantity"
                 :operand [(elm/singleton-from tu/patient-retrieve-elm)]}]
        (are [resource res]
          (= res (core/-eval (c/compile context elm) {} resource nil))
          {:value 23M :code "kg"} (quantity/quantity 23M "kg")
          {:value 42M} (quantity/quantity 42M "1")
          {} nil)))))
