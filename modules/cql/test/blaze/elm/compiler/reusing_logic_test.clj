(ns blaze.elm.compiler.reusing-logic-test
  "9. Reusing Logic

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.anomaly :as ba]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.function :as function]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


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
    (given (ba/try-anomaly (c/compile {} #elm/expression-ref "name-170312"))
      ::anom/category := ::anom/incorrect
      ::anom/message := "Expression definition `name-170312` not found."
      :context := {}))

  (testing "Result Type"
    (let [library {:statements {:def [{:type "ExpressionDef"
                                       :name "name-170312"
                                       :resultTypeName "result-type-name-173029"}]}}
          expr (c/compile {:library library} #elm/expression-ref "name-170312")]
      (is (= "result-type-name-173029" (:result-type-name (meta expr))))))

  (testing "Eval"
    (let [library {:statements {:def [{:type "ExpressionDef"
                                       :name "name-170312"}]}}
          expr (c/compile {:library library} #elm/expression-ref "name-170312")]
      (is (= ::result (core/-eval expr {:expression-defs {"name-170312" {:expression ::result}}} nil nil)))))

  (testing "form"
    (let [library {:statements {:def [{:type "ExpressionDef"
                                       :name "name-170312"}]}}
          expr (c/compile {:library library} #elm/expression-ref "name-170312")]
      (is (= '(expr-ref "name-170312") (core/-form expr))))))


;; 9.4. FunctionRef
;;
;; The FunctionRef type defines an expression that invokes a previously defined
;; function. The result of evaluating each operand is passed to the function.
(deftest compile-function-ref-test
  (testing "Throws error on missing function"
    (given (ba/try-anomaly (c/compile {} #elm/function-ref ["name-175844"]))
      ::anom/category := ::anom/incorrect
      ::anom/message := "Local function definition `name-175844` not found."
      :context := {}))

  (testing "Local function with arity 0"
    (let [library-name "library-162530"
          function-name "name-210650"
          fn-expr (c/compile {} #elm/integer "1")
          compile-ctx {:function-defs {function-name {:function (partial function/arity-n library-name function-name fn-expr [])}}}
          elm (elm/function-ref [function-name])
          expr (c/compile compile-ctx elm)]
      (testing "eval"
        (is (= 1 (core/-eval expr {} nil nil))))

      (testing "form"
        (is (= `(~'call ~(str library-name "." function-name)) (core/-form expr))))))

  (testing "Local function with arity 1"
    (let [library-name "library-162530"
          function-name "name-180815"
          fn-expr (c/compile {} #elm/negate #elm/operand-ref"x")
          compile-ctx {:library {:parameters {:def [{:name "a"}]}}
                       :function-defs {function-name {:function (partial function/arity-n library-name function-name fn-expr ["x"])}}}
          elm (elm/function-ref [function-name #elm/parameter-ref "a"])
          expr (c/compile compile-ctx elm)]
      (testing "eval"
        (are [a res] (= res (core/-eval expr {:parameters {"a" a}} nil nil))
          1 -1
          -1 1
          0 0))

      (testing "form"
        (is (= `(~'call ~(str library-name "." function-name) (~'param-ref "a")) (core/-form expr))))))

  (testing "Local function with arity 2"
    (let [library-name "library-162530"
          function-name "name-184652"
          fn-expr (c/compile {} #elm/add [#elm/operand-ref"x" #elm/operand-ref"y"])
          compile-ctx {:library {:parameters {:def [{:name "a"} {:name "b"}]}}
                       :function-defs {function-name {:function (partial function/arity-n library-name function-name fn-expr ["x" "y"])}}}
          elm (elm/function-ref [function-name #elm/parameter-ref "a" #elm/parameter-ref "b"])
          expr (c/compile compile-ctx elm)]
      (testing "eval"
        (are [a b res] (= res (core/-eval expr {:parameters {"a" a "b" b}} nil nil))
          1 1 2
          1 0 1
          0 1 1))

      (testing "form"
        (is (= `(~'call ~(str library-name "." function-name) (~'param-ref "a") (~'param-ref "b")) (core/-form expr))))))

  (testing "Included Function with arity 1"
    (let [library-name "library-162530"
          function-name "name-180815"
          fn-expr (c/compile {} #elm/negate #elm/operand-ref"x")
          compile-ctx
          {:includes
           {library-name
            {:function-defs
             {function-name {:function (partial function/arity-n library-name function-name fn-expr ["x"])}}}}
           :library {:parameters {:def [{:name "a"}]}}}
          elm
          {:type "FunctionRef"
           :name function-name
           :libraryName library-name
           :operand [#elm/parameter-ref "a"]}
          expr (c/compile compile-ctx elm)]
      (testing "eval"
        (are [a res] (= res (core/-eval expr {:parameters {"a" a}} nil nil))
          1 -1
          -1 1
          0 0))

      (testing "form"
        (is (= `(~'call ~(str library-name "." function-name) (~'param-ref "a")) (core/-form expr)))))))


;; 9.5 OperandRef
;;
;; The OperandRef expression allows the value of an operand to be referenced as
;; part of an expression within the body of a function definition.
(deftest compile-operand-ref-test
  (testing "form"
    (is (= '(operand-ref "x") (core/-form (c/compile {} #elm/operand-ref"x"))))))
