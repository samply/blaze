(ns blaze.elm.compiler.nullological-operators-test
  "14. Nullological Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.db.api-stub :as api-stub]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.core-spec]
   [blaze.elm.compiler.nullological-operators]
   [blaze.elm.compiler.test-util :as ctu :refer [has-form]]
   [blaze.elm.expression.cache :as ec]
   [blaze.elm.literal :as elm]
   [blaze.elm.literal-spec]
   [blaze.module.test-util :refer [with-system]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [juxt.iota :refer [given]]))

(set! *warn-on-reflection* true)
(st/instrument)
(ctu/instrument-compile)

(defn- fixture [f]
  (st/instrument)
  (ctu/instrument-compile)
  (f)
  (st/unstrument))

(test/use-fixtures :each fixture)

;; 14.1. Null
;;
;; The Null operator returns a null, or missing information marker. To avoid the
;; need to cast this result, the operator is allowed to return a typed null.
(deftest compile-null-test
  (is (nil? (c/compile {} {:type "Null"}))))

;; 14.2. Coalesce
;;
;; The Coalesce operator returns the first non-null result in a list of
;; arguments. If all arguments evaluate to null the result is null. The static
;; type of the first argument determines the type of the result, and all
;; subsequent arguments must be of that same type.
(deftest compile-coalesce-test
  (are [elm res] (= res (core/-eval (c/compile {} (elm/coalesce elm)) {} nil nil))
    [] nil
    [{:type "Null"}] nil
    [#elm/boolean "false" #elm/boolean "true"] false
    [{:type "Null"} #elm/integer "1" #elm/integer "2"] 1
    [#elm/integer "2"] 2
    [#elm/list []] nil
    [{:type "Null"} #elm/list [#elm/string "a"]] ["a"]
    [#elm/list [{:type "Null"} #elm/string "a"]] "a")

  (testing "expression is dynamic"
    (are [elm] (false? (core/-static (ctu/dynamic-compile (elm/coalesce elm))))
      []
      [#elm/list []]))

  (ctu/testing-binary-op elm/coalesce)

  (testing "list operand"
    (testing "attach cache"
      (with-redefs [ec/get #(do (assert (= ::cache %1)) (c/form %2))]
        (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
          (let [elm (elm/coalesce [#elm/list [#elm/exists #elm/retrieve{:type "Observation"}]])
                expr (c/compile {:node node :eval-context "Patient"} elm)]
            (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
              count := 2
              [0] := expr
              [1 count] := 1
              [1 0] := '(exists (retrieve "Observation")))))))

    (testing "resolve expression references"
      (let [elm (elm/coalesce [#elm/list [#elm/expression-ref "x"]])
            expr-def {:type "ExpressionDef" :name "x" :expression "y"
                      :context "Unfiltered"}
            ctx {:library {:statements {:def [expr-def]}}}
            expr (c/resolve-refs (c/compile ctx elm) {"x" expr-def})]
        (has-form expr '(coalesce (list "y")))
        (is (= "y" (core/-eval expr {} nil nil)))))

    (testing "resolve parameters"
      (let [elm (elm/coalesce [#elm/list [{:type "Null"} #elm/parameter-ref "x"]])
            ctx {:library {:parameters {:def [{:name "x"}]}}}
            expr (c/resolve-params (c/compile ctx elm) {"x" "y"})]
        (has-form expr '(coalesce (list nil "y")))
        (is (= "y" (core/-eval expr {} nil nil)))))

    (testing "optimize"
      (let [elm (elm/coalesce [#elm/list [#ctu/optimizeable "x"]])
            expr (st/with-instrument-disabled (c/optimize (c/compile {} elm) nil))]
        (has-form expr '(coalesce (list (optimized "x"))))))

    (testing "equals/hashCode"
      (let [elm (elm/coalesce [#elm/list [#elm/parameter-ref "x"]])
            ctx {:library {:parameters {:def [{:name "x"}]}}}
            expr-1 (c/compile ctx elm)
            expr-2 (c/compile ctx elm)]
        (is (.equals ^Object expr-1 expr-2))
        (is (= (.hashCode ^Object expr-1) (.hashCode ^Object expr-2)))))

    (testing "form"
      (let [expr (ctu/dynamic-compile (elm/coalesce [#elm/list [#elm/parameter-ref "x"]]))]
        (has-form expr '(coalesce (list (param-ref "x"))))))))

;; 14.3. IsFalse
;;
;; The IsFalse operator determines whether or not its argument evaluates to
;; false. If the argument evaluates to false, the result is true; if the
;; argument evaluates to true or null, the result is false.
(deftest compile-is-false-test
  (testing "Static"
    (are [x pred] (pred (c/compile {} (elm/is-false x)))
      #elm/boolean "true" false?
      #elm/boolean "false" true?
      {:type "Null"} false?))

  (testing "Dynamic"
    (are [x pred] (pred (ctu/dynamic-compile-eval (elm/is-false x)))
      #elm/parameter-ref "true" false?
      #elm/parameter-ref "false" true?
      #elm/parameter-ref "nil" false?))

  (ctu/testing-unary-op elm/is-false)

  (ctu/testing-optimize elm/is-false
    (testing "false"
      #ctu/optimize-to true
      #ctu/optimize-to nil
      false)

    (testing "true"
      #ctu/optimize-to false
      true)))

;; 14.4. IsNull
;;
;; The IsNull operator determines whether or not its argument evaluates to null.
;; If the argument evaluates to null, the result is true; otherwise, the result
;; is false.
(deftest compile-is-null-test
  (testing "Static"
    (are [x pred] (pred (c/compile {} (elm/is-null x)))
      #elm/boolean "true" false?
      #elm/boolean "false" false?
      {:type "Null"} true?))

  (testing "Dynamic"
    (are [x pred] (pred (ctu/dynamic-compile-eval (elm/is-null x)))
      #elm/parameter-ref "true" false?
      #elm/parameter-ref "false" false?
      #elm/parameter-ref "nil" true?))

  (ctu/testing-unary-op elm/is-null)

  (ctu/testing-optimize elm/is-null
    (testing "false"
      #ctu/optimize-to true
      #ctu/optimize-to false
      false)

    (testing "true"
      #ctu/optimize-to nil
      true)))

;; 14.5. IsTrue
;;
;; The IsTrue operator determines whether or not its argument evaluates to true.
;; If the argument evaluates to true, the result is true; if the argument
;; evaluates to false or null, the result is false.
(deftest compile-is-true-test
  (testing "Static"
    (are [x pred] (pred (c/compile {} (elm/is-true x)))
      #elm/boolean "true" true?
      #elm/boolean "false" false?
      {:type "Null"} false?))

  (testing "Dynamic"
    (are [x pred] (pred (ctu/dynamic-compile-eval (elm/is-true x)))
      #elm/parameter-ref "true" true?
      #elm/parameter-ref "false" false?
      #elm/parameter-ref "nil" false?))

  (ctu/testing-unary-op elm/is-true)

  (ctu/testing-optimize elm/is-true
    (testing "false"
      #ctu/optimize-to false
      #ctu/optimize-to nil
      false)

    (testing "true"
      #ctu/optimize-to true
      true)))
