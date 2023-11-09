(ns blaze.elm.compiler.nullological-operators-test
  "14. Nullological Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.core-spec]
    [blaze.elm.compiler.test-util :as ctu]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]))


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
      [{:type "Null"}]
      [#elm/list []])))


;; 14.3. IsFalse
;;
;; The IsFalse operator determines whether or not its argument evaluates to
;; false. If the argument evaluates to false, the result is true; if the
;; argument evaluates to true or null, the result is false.
(deftest compile-is-false-test
  (testing "Static"
    (are [x res] (= res (c/compile {} (elm/is-false x)))
      #elm/boolean "true" false
      #elm/boolean "false" true
      {:type "Null"} false))

  (testing "Dynamic"
    (are [x res] (= res (ctu/dynamic-compile-eval (elm/is-false x)))
      #elm/parameter-ref "true" false
      #elm/parameter-ref "false" true
      #elm/parameter-ref "nil" false))

  (ctu/testing-unary-dynamic elm/is-false)

  (ctu/testing-unary-form elm/is-false))


;; 14.4. IsNull
;;
;; The IsNull operator determines whether or not its argument evaluates to null.
;; If the argument evaluates to null, the result is true; otherwise, the result
;; is false.
(deftest compile-is-null-test
  (testing "Static"
    (are [x res] (= res (c/compile {} (elm/is-null x)))
      #elm/boolean "true" false
      #elm/boolean "false" false
      {:type "Null"} true))

  (testing "Dynamic"
    (are [x res] (= res (ctu/dynamic-compile-eval (elm/is-null x)))
      #elm/parameter-ref "true" false
      #elm/parameter-ref "false" false
      #elm/parameter-ref "nil" true))

  (ctu/testing-unary-dynamic elm/is-null)

  (ctu/testing-unary-form elm/is-null))


;; 14.5. IsTrue
;;
;; The IsTrue operator determines whether or not its argument evaluates to true.
;; If the argument evaluates to true, the result is true; if the argument
;; evaluates to false or null, the result is false.
(deftest compile-is-true-test
  (testing "Static"
    (are [x res] (= res (c/compile {} (elm/is-true x)))
      #elm/boolean "true" true
      #elm/boolean "false" false
      {:type "Null"} false))

  (testing "Dynamic"
    (are [x res] (= res (ctu/dynamic-compile-eval (elm/is-true x)))
      #elm/parameter-ref "true" true
      #elm/parameter-ref "false" false
      #elm/parameter-ref "nil" false))

  (ctu/testing-unary-dynamic elm/is-true)

  (ctu/testing-unary-form elm/is-true))
