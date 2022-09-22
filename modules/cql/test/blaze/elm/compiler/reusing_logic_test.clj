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
    [blaze.elm.interval :as interval]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [blaze.elm.quantity :as quantity]
    [blaze.fhir.spec.type.system :as system]
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
    (let [library {:statements {:def [{:name "name-170312" :resultTypeName "result-type-name-173029"}]}}
          expr (c/compile {:library library} #elm/expression-ref "name-170312")]
      (is (= "result-type-name-173029" (:result-type-name (meta expr))))))

  (testing "Eval"
    (let [library {:statements {:def [{:name "name-170312"}]}}
          expr (c/compile {:library library} #elm/expression-ref "name-170312")]
      (is (= ::result (core/-eval expr {:expression-defs {"name-170312" {:expression ::result}}} nil nil)))))

  (testing "form"
    (let [library {:statements {:def [{:name "name-170312"}]}}
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
      ::anom/message := "Function definition `name-175844` not found."
      :context := {}))

  (testing "Custom function with arity 0"
    (let [function-name "name-210650"
          fn-expr (c/compile {} #elm/integer "1")
          compile-ctx {:function-defs {function-name {:function (partial function/arity-n function-name fn-expr [])}}}
          elm (elm/function-ref [function-name])
          expr (c/compile compile-ctx elm)]
      (testing "eval"
        (is (= 1 (core/-eval expr {} nil nil))))

      (testing "form"
        (is (= `(~'call ~function-name) (core/-form expr))))))

  (testing "Custom function with arity 1"
    (let [function-name "name-180815"
          fn-expr (c/compile {} #elm/negate #elm/operand-ref"x")
          compile-ctx {:library {:parameters {:def [{:name "a"}]}}
                       :function-defs {function-name {:function (partial function/arity-n function-name fn-expr ["x"])}}}
          elm (elm/function-ref [function-name #elm/parameter-ref "a"])
          expr (c/compile compile-ctx elm)]
      (testing "eval"
        (are [a res] (= res (core/-eval expr {:parameters {"a" a}} nil nil))
          1 -1
          -1 1
          0 0))

      (testing "form"
        (is (= `(~'call ~function-name (~'param-ref "a")) (core/-form expr))))))

  (testing "Custom function with arity 2"
    (let [function-name "name-184652"
          fn-expr (c/compile {} #elm/add [#elm/operand-ref"x" #elm/operand-ref"y"])
          compile-ctx {:library {:parameters {:def [{:name "a"} {:name "b"}]}}
                       :function-defs {function-name {:function (partial function/arity-n function-name fn-expr ["x" "y"])}}}
          elm (elm/function-ref [function-name #elm/parameter-ref "a" #elm/parameter-ref "b"])
          expr (c/compile compile-ctx elm)]
      (testing "eval"
        (are [a b res] (= res (core/-eval expr {:parameters {"a" a "b" b}} nil nil))
          1 1 2
          1 0 1
          0 1 1))

      (testing "form"
        (is (= `(~'call ~function-name (~'param-ref "a") (~'param-ref "b")) (core/-form expr))))))

  (testing "ToQuantity"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/function-ref ["ToQuantity" #elm/parameter-ref "x"]
          expr (c/compile compile-ctx elm)]
      (testing "eval"
        (are [x res] (= res (core/-eval expr {:parameters {"x" x}} nil nil))
          {:value 23M :code "kg"} (quantity/quantity 23M "kg")
          {:value 42M} (quantity/quantity 42M "1")
          {} nil))

      (testing "form"
        (is (= '(call "ToQuantity" (param-ref "x")) (core/-form expr))))))

  (testing "ToDateTime"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/function-ref ["ToDateTime" #elm/parameter-ref "x"]
          expr (c/compile compile-ctx elm)
          eval-ctx (fn [x] {:now tu/now :parameters {"x" x}})]
      (testing "eval"
        (are [x res] (= res (core/-eval expr (eval-ctx x) nil nil))
          #fhir/dateTime"2022-02-22"
          (system/date-time 2022 2 22)
          #fhir/instant"2021-02-23T15:12:45Z"
          (system/date-time 2021 2 23 15 12 45)
          #fhir/instant"2021-02-23T15:12:45+01:00"
          (system/date-time 2021 2 23 14 12 45)))

      (testing "form"
        (is (= '(call "ToDateTime" (param-ref "x")) (core/-form expr))))))

  (testing "ToString"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/function-ref ["ToString" #elm/parameter-ref "x"]
          expr (c/compile compile-ctx elm)]
      (testing "eval"
        (are [x res] (= res (core/-eval expr {:parameters {"x" x}} nil nil))
          "string-195733" "string-195733"
          #fhir/uri"uri-195924" "uri-195924"))

      (testing "form"
        (is (= '(call "ToString" (param-ref "x")) (core/-form expr))))))

  (testing "ToInterval"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/function-ref ["ToInterval" #elm/parameter-ref "x"]
          expr (c/compile compile-ctx elm)
          eval-ctx (fn [x] {:now tu/now :parameters {"x" x}})]
      (testing "eval"
        (are [x res] (= res (core/-eval expr (eval-ctx x) nil nil))
          #fhir/Period
                  {:start #fhir/dateTime"2021-02-23T15:12:45+01:00"
                   :end #fhir/dateTime"2021-02-23T16:00:00+01:00"}
          (interval/interval
            (system/date-time 2021 2 23 14 12 45)
            (system/date-time 2021 2 23 15 0 0))
          #fhir/Period
                  {:start nil
                   :end #fhir/dateTime"2021-02-23T16:00:00+01:00"}
          (interval/interval
            nil
            (system/date-time 2021 2 23 15 0 0))
          #fhir/Period
                  {:start #fhir/dateTime"2021-02-23T15:12:45+01:00"
                   :end nil}
          (interval/interval
            (system/date-time 2021 2 23 14 12 45)
            nil)))

      (testing "form"
        (is (= '(call "ToInterval" (param-ref "x")) (core/-form expr)))))))


;; 9.5 OperandRef
;;
;; The OperandRef expression allows the value of an operand to be referenced as
;; part of an expression within the body of a function definition.
(deftest compile-operand-ref-test
  (testing "form"
    (is (= '(operand-ref "x") (core/-form (c/compile {} #elm/operand-ref"x"))))))
