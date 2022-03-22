(ns blaze.elm.compiler.reusing-logic-test
  "9. Reusing Logic

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.anomaly :as ba]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.interval :as interval]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [blaze.elm.quantity :as quantity]
    [blaze.fhir.spec.type.system :as system]
    [blaze.test-util :refer [given-thrown]]
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
      (is (= ::result (core/-eval expr {:library-context {"name-170312" ::result}} nil nil)))))

  (testing "form"
    (let [library {:statements {:def [{:name "name-170312"}]}}
          expr (c/compile {:library library} #elm/expression-ref "name-170312")]
      (is (= '(expr-ref "name-170312") (core/-form expr))))))


;; 9.4. FunctionRef
;;
;; The FunctionRef type defines an expression that invokes a previously defined
;; function. The result of evaluating each operand is passed to the function.
(deftest compile-function-ref-test
  (testing "ToQuantity"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm (elm/function-ref "ToQuantity" #elm/parameter-ref "x")
          expr (c/compile compile-ctx elm)]

      (testing "success"
        (are [x res] (= res (core/-eval expr {:parameters {"x" x}} nil nil))
          #fhir/Quantity {:value 23M :code #fhir/code "kg"} (quantity/quantity 23M "kg")
          #fhir/Quantity {:value 42M} (quantity/quantity 42M "1")
          #fhir/Quantity {} nil
          nil nil))

      (testing "failure"
        (given-thrown (core/-eval expr {:parameters {"x" :foo}} nil nil)
          ::anom/category := ::anom/incorrect
          ::anom/message := "Can't convert `:foo` to quantity."))

      (testing "form"
        (is (= '(call "ToQuantity" (param-ref "x")) (core/-form expr))))))

  (testing "ToDateTime"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm (elm/function-ref "ToDateTime" #elm/parameter-ref "x")
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
          elm (elm/function-ref "ToString" #elm/parameter-ref "x")
          expr (c/compile compile-ctx elm)]
      (testing "eval"
        (are [x res] (= res (core/-eval expr {:parameters {"x" x}} nil nil))
          "string-195733" "string-195733"
          #fhir/uri "uri-195924" "uri-195924"))

      (testing "form"
        (is (= '(call "ToString" (param-ref "x")) (core/-form expr))))))

  (testing "ToInterval"
    (let [compile-ctx {:library {:parameters {:def [{:name "x"}]}}}
          elm (elm/function-ref "ToInterval" #elm/parameter-ref "x")
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
