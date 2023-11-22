(ns blaze.elm.compiler.reusing-logic-test
  "9. Reusing Logic

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.anomaly :as ba]
   [blaze.elm.code :as code]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.core-spec]
   [blaze.elm.compiler.function :as function]
   [blaze.elm.compiler.test-util :as ctu :refer [has-form]]
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
(ctu/instrument-compile)

(defn- fixture [f]
  (st/instrument)
  (ctu/instrument-compile)
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

  (testing "form and static"
    (let [library {:statements {:def [{:type "ExpressionDef"
                                       :name "name-170312"}]}}
          expr (c/compile {:library library} #elm/expression-ref "name-170312")]

      (has-form expr '(expr-ref "name-170312"))

      (is (false? (core/-static expr))))))

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

      (testing "static"
        (is (false? (core/-static expr))))

      (testing "form"
        (has-form expr (list 'call function-name)))))

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

      (testing "static"
        (is (false? (core/-static expr))))

      (testing "form"
        (has-form expr (list 'call function-name '(param-ref "a"))))))

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

      (testing "static"
        (is (false? (core/-static expr))))

      (testing "form"
        (has-form expr (list 'call function-name '(param-ref "a") '(param-ref "b"))))))

  (testing "ToQuantity"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/function-ref ["ToQuantity" #elm/parameter-ref "x"]
          expr (c/compile compile-ctx elm)]

      (testing "eval"
        (are [x res] (= res (core/-eval expr {:parameters {"x" x}} nil nil))
          {:value 23M :code "kg"} (quantity/quantity 23M "kg")
          {:value 42M} (quantity/quantity 42M "1")
          {} nil))

      (testing "static"
        (is (false? (core/-static expr))))

      (testing "form"
        (has-form expr '(call "ToQuantity" (param-ref "x"))))))

  (testing "ToDate"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/function-ref ["ToDate" #elm/parameter-ref "x"]
          expr (c/compile compile-ctx elm)
          eval-ctx (fn [x] {:now ctu/now :parameters {"x" x}})]

      (testing "eval"
        (are [x res] (= res (core/-eval expr (eval-ctx x) nil nil))
          #fhir/date{:id "foo"} nil
          #fhir/date{:extension [#fhir/Extension{:url "foo"}]} nil
          #fhir/date"2023" #system/date"2023"
          #fhir/date"2023-05" #system/date"2023-05"
          #fhir/date"2023-05-07" #system/date"2023-05-07"))

      (testing "static"
        (is (false? (core/-static expr))))

      (testing "form"
        (has-form expr '(call "ToDate" (param-ref "x"))))))

  (testing "ToDateTime"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/function-ref ["ToDateTime" #elm/parameter-ref "x"]
          expr (c/compile compile-ctx elm)
          eval-ctx (fn [x] {:now ctu/now :parameters {"x" x}})]

      (testing "eval"
        (are [x res] (= res (core/-eval expr (eval-ctx x) nil nil))
          #fhir/dateTime{:id "foo"} nil
          #fhir/dateTime{:extension [#fhir/Extension{:url "foo"}]} nil
          #fhir/dateTime"2022" #system/date-time"2022"
          #fhir/dateTime"2022-02" #system/date-time"2022-02"
          #fhir/dateTime"2022-02-22" #system/date-time"2022-02-22"
          #fhir/dateTime"2023-05-07T17:39" #system/date-time"2023-05-07T17:39"

          #fhir/instant{:id "foo"} nil
          #fhir/instant{:extension [#fhir/Extension{:url "foo"}]} nil
          #fhir/instant"2021-02-23T15:12:45Z" #system/date-time"2021-02-23T15:12:45"
          #fhir/instant"2021-02-23T15:12:45+01:00" #system/date-time"2021-02-23T14:12:45"))

      (testing "static"
        (is (false? (core/-static expr))))

      (testing "form"
        (has-form expr '(call "ToDateTime" (param-ref "x"))))))

  (testing "ToString"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/function-ref ["ToString" #elm/parameter-ref "x"]
          expr (c/compile compile-ctx elm)]

      (testing "eval"
        (are [x res] (= res (core/-eval expr {:parameters {"x" x}} nil nil))
          "string-195733" "string-195733"
          #fhir/uri"uri-195924" "uri-195924"
          #fhir/code{:id "foo" :value "code-211914"} "code-211914"
          #fhir/code{:id "foo"} nil))

      (testing "static"
        (is (false? (core/-static expr))))

      (testing "form"
        (has-form expr '(call "ToString" (param-ref "x"))))))

  (testing "ToCode"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/function-ref ["ToCode" #elm/parameter-ref "x"]
          expr (c/compile compile-ctx elm)]

      (testing "eval"
        (are [x res] (= res (core/-eval expr {:parameters {"x" x}} nil nil))
          {:system "system-140820"
           :version "version-140924"
           :code "code-140828"}
          (code/to-code "system-140820" "version-140924" "code-140828")))

      (testing "static"
        (is (false? (core/-static expr))))

      (testing "form"
        (has-form expr '(call "ToCode" (param-ref "x"))))))

  (testing "ToInterval"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm #elm/function-ref ["ToInterval" #elm/parameter-ref "x"]
          expr (c/compile compile-ctx elm)
          eval-ctx (fn [x] {:now ctu/now :parameters {"x" x}})]

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

      (testing "static"
        (is (false? (core/-static expr))))

      (testing "form"
        (has-form expr '(call "ToInterval" (param-ref "x")))))))

;; 9.5 OperandRef
;;
;; The OperandRef expression allows the value of an operand to be referenced as
;; part of an expression within the body of a function definition.
(deftest compile-operand-ref-test
  (testing "form and static"
    (let [expr (c/compile {} #elm/operand-ref"x")]
      (has-form expr '(operand-ref "x"))
      (is (false? (core/-static expr))))))
