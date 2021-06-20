(ns blaze.elm.compiler.nullological-operators-test
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]))


(st/instrument)
(tu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (tu/instrument-compile)
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
    [#elm/boolean"false" #elm/boolean"true"] false
    [{:type "Null"} #elm/integer"1" #elm/integer"2"] 1
    [#elm/integer"2"] 2
    [#elm/list []] nil
    [{:type "Null"} #elm/list [#elm/string "a"]] ["a"]
    [#elm/list [{:type "Null"} #elm/string "a"]] "a"))


;; 14.3. IsFalse
;;
;; The IsFalse operator determines whether or not its argument evaluates to
;; false. If the argument evaluates to false, the result is true; if the
;; argument evaluates to true or null, the result is false.
(deftest compile-is-false-test
  (testing "Static"
    (are [x res] (= res (c/compile {} (elm/is-false x)))
      #elm/boolean"true" false
      #elm/boolean"false" true
      {:type "Null"} false))

  (with-open [node (mem-node-with [])]
    (let [context {:eval-context "Patient" :node node}]
      (testing "Dynamic"
        ;; dynamic-resource will evaluate to true
        (are [x res] (= res (core/-eval (c/compile context (elm/is-false x)) {} true nil))
          tu/dynamic-resource false)

        ;; dynamic-resource will evaluate to false
        (are [x res] (= res (core/-eval (c/compile context (elm/is-false x)) {} false nil))
          tu/dynamic-resource true)

        ;; dynamic-resource will evaluate to nil
        (are [x res] (= res (core/-eval (c/compile context (elm/is-false x)) {} nil nil))
          tu/dynamic-resource false)))))


;; 14.4. IsNull
;;
;; The IsNull operator determines whether or not its argument evaluates to null.
;; If the argument evaluates to null, the result is true; otherwise, the result
;; is false.
(deftest compile-is-null-test
  (testing "Static"
    (are [x res] (= res (c/compile {} (elm/is-null x)))
      #elm/boolean"true" false
      #elm/boolean"false" false
      {:type "Null"} true))

  (with-open [node (mem-node-with [])]
    (let [context {:eval-context "Patient" :node node}]
      (testing "Dynamic"
        ;; dynamic-resource will evaluate to true
        (are [x res] (= res (core/-eval (c/compile context (elm/is-null x)) {} true nil))
          tu/dynamic-resource false)

        ;; dynamic-resource will evaluate to false
        (are [x res] (= res (core/-eval (c/compile context (elm/is-null x)) {} false nil))
          tu/dynamic-resource false)

        ;; dynamic-resource will evaluate to nil
        (are [x res] (= res (core/-eval (c/compile context (elm/is-null x)) {} nil nil))
          tu/dynamic-resource true)))))


;; 14.5. IsTrue
;;
;; The IsTrue operator determines whether or not its argument evaluates to true.
;; If the argument evaluates to true, the result is true; if the argument
;; evaluates to false or null, the result is false.
(deftest compile-is-true-test
  (testing "Static"
    (are [x res] (= res (c/compile {} (elm/is-true x)))
      #elm/boolean"true" true
      #elm/boolean"false" false
      {:type "Null"} false))

  (with-open [node (mem-node-with [])]
    (let [context {:eval-context "Patient" :node node}]
      (testing "Dynamic"
        ;; dynamic-resource will evaluate to true
        (are [x res] (= res (core/-eval (c/compile context (elm/is-true x)) {} true nil))
          tu/dynamic-resource true)

        ;; dynamic-resource will evaluate to false
        (are [x res] (= res (core/-eval (c/compile context (elm/is-true x)) {} false nil))
          tu/dynamic-resource false)

        ;; dynamic-resource will evaluate to nil
        (are [x res] (= res (core/-eval (c/compile context (elm/is-true x)) {} nil nil))
          tu/dynamic-resource false)))))
