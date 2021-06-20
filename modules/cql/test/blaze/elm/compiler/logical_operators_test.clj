(ns blaze.elm.compiler.logical-operators-test
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.logical-operators]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]))


(st/instrument)
(tu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (tu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; 13. Logical Operators

;; 13.1. And
;;
;; The And operator returns the logical conjunction of its arguments. Note that
;; this operator is defined using 3-valued logic semantics. This means that if
;; either argument is false, the result is false; if both arguments are true,
;; the result is true; otherwise, the result is null. Note also that ELM does
;; not prescribe short-circuit evaluation.
(deftest compile-and-test
  (testing "Static"
    (are [x y res] (= res (c/compile {} (elm/and [x y])))
      #elm/boolean"true" #elm/boolean"true" true
      #elm/boolean"true" #elm/boolean"false" false
      #elm/boolean"true" {:type "Null"} nil

      #elm/boolean"false" #elm/boolean"true" false
      #elm/boolean"false" #elm/boolean"false" false
      #elm/boolean"false" {:type "Null"} false

      {:type "Null"} #elm/boolean"true" nil
      {:type "Null"} #elm/boolean"false" false
      {:type "Null"} {:type "Null"} nil))

  (testing "Dynamic"
    (with-open [node (mem-node-with [])]
      (let [context {:eval-context "Patient" :node node}]
        ;; dynamic-resource will evaluate to true
        (are [x y res] (= res (core/-eval (c/compile context (elm/and [x y])) {} true nil))
          #elm/boolean"true" tu/dynamic-resource true
          tu/dynamic-resource #elm/boolean"true" true
          tu/dynamic-resource tu/dynamic-resource true

          tu/dynamic-resource {:type "Null"} nil
          {:type "Null"} tu/dynamic-resource nil)

        ;; dynamic-resource will evaluate to false
        (are [x y res] (= res (core/-eval (c/compile context (elm/and [x y])) {} false nil))
          #elm/boolean"true" tu/dynamic-resource false
          tu/dynamic-resource #elm/boolean"true" false
          tu/dynamic-resource tu/dynamic-resource false

          tu/dynamic-resource {:type "Null"} false
          {:type "Null"} tu/dynamic-resource false)

        ;; dynamic-resource will evaluate to nil
        (are [x y res] (= res (core/-eval (c/compile context (elm/and [x y])) {} nil nil))
          #elm/boolean"false" tu/dynamic-resource false
          tu/dynamic-resource #elm/boolean"false" false
          #elm/boolean"true" tu/dynamic-resource nil
          tu/dynamic-resource #elm/boolean"true" nil
          tu/dynamic-resource tu/dynamic-resource nil)))))


;; 13.2. Implies
;;
;; The Implies operator returns the logical implication of its arguments. Note
;; that this operator is defined using 3-valued logic semantics. This means that
;; if the left operand evaluates to true, this operator returns the boolean
;; evaluation of the right operand. If the left operand evaluates to false, this
;; operator returns true. Otherwise, this operator returns true if the right
;; operand evaluates to true, and null otherwise.
;;
;; Note that implies may use short-circuit evaluation in the case that the first
;; operand evaluates to false.
(deftest compile-implies-test
  (testing "Static"
    (are [x y res] (= res (c/compile {} (elm/or [(elm/not x) y])))
      #elm/boolean"true" #elm/boolean"true" true
      #elm/boolean"true" #elm/boolean"false" false
      #elm/boolean"true" {:type "Null"} nil

      #elm/boolean"false" #elm/boolean"true" true
      #elm/boolean"false" #elm/boolean"false" true
      #elm/boolean"false" {:type "Null"} true

      {:type "Null"} #elm/boolean"true" true
      {:type "Null"} #elm/boolean"false" nil
      {:type "Null"} {:type "Null"} nil)))


;; 13.3. Not
;;
;; The Not operator returns the logical negation of its argument. If the
;; argument is true, the result is false; if the argument is false, the result
;; is true; otherwise, the result is null.
(deftest compile-not-test
  (testing "Static"
    (are [x res] (= res (c/compile {} (elm/not x)))
      #elm/boolean"true" false
      #elm/boolean"false" true
      {:type "Null"} nil))

  (testing "Dynamic"
    (with-open [node (mem-node-with [])]
      (let [context {:eval-context "Patient" :node node}]
        ;; dynamic-resource will evaluate to true
        (are [x res] (= res (core/-eval (c/compile context (elm/not x)) {} true nil))
          tu/dynamic-resource false)

        ;; dynamic-resource will evaluate to false
        (are [x res] (= res (core/-eval (c/compile context (elm/not x)) {} false nil))
          tu/dynamic-resource true)

        ;; dynamic-resource will evaluate to nil
        (are [x res] (= res (core/-eval (c/compile context (elm/not x)) {} nil nil))
          tu/dynamic-resource nil)))))


;; 13.4. Or
;;
;; The Or operator returns the logical disjunction of its arguments. Note that
;; this operator is defined using 3-valued logic semantics. This means that if
;; either argument is true, the result is true; if both arguments are false, the
;; result is false; otherwise, the result is null. Note also that ELM does not
;; prescribe short-circuit evaluation.
(deftest compile-or-test
  (testing "Static"
    (are [x y res] (= res (c/compile {} (elm/or [x y])))
      #elm/boolean"true" #elm/boolean"true" true
      #elm/boolean"true" #elm/boolean"false" true
      #elm/boolean"true" {:type "Null"} true

      #elm/boolean"false" #elm/boolean"true" true
      #elm/boolean"false" #elm/boolean"false" false
      #elm/boolean"false" {:type "Null"} nil

      {:type "Null"} #elm/boolean"true" true
      {:type "Null"} #elm/boolean"false" nil
      {:type "Null"} {:type "Null"} nil))

  (testing "Dynamic"
    (with-open [node (mem-node-with [])]
      (let [context {:eval-context "Patient" :node node}]
        ;; dynamic-resource will evaluate to true
        (are [x y res] (= res (core/-eval (c/compile context (elm/or [x y])) {} true nil))
          #elm/boolean"false" tu/dynamic-resource true
          tu/dynamic-resource #elm/boolean"false" true
          tu/dynamic-resource tu/dynamic-resource true

          tu/dynamic-resource {:type "Null"} true
          {:type "Null"} tu/dynamic-resource true)

        ;; dynamic-resource will evaluate to false
        (are [x y res] (= res (core/-eval (c/compile context (elm/or [x y])) {} false nil))
          #elm/boolean"false" tu/dynamic-resource false
          tu/dynamic-resource #elm/boolean"false" false
          tu/dynamic-resource tu/dynamic-resource false

          tu/dynamic-resource {:type "Null"} nil
          {:type "Null"} tu/dynamic-resource nil)

        ;; dynamic-resource will evaluate to nil
        (are [x y res] (= res (core/-eval (c/compile context (elm/or [x y])) {} nil nil))
          #elm/boolean"true" tu/dynamic-resource true
          tu/dynamic-resource #elm/boolean"true" true
          #elm/boolean"false" tu/dynamic-resource nil
          tu/dynamic-resource #elm/boolean"false" nil
          tu/dynamic-resource tu/dynamic-resource nil)))))


;; 13.5. Xor
;;
;; The Xor operator returns the exclusive or of its arguments. Note that this
;; operator is defined using 3-valued logic semantics. This means that the
;; result is true if and only if one argument is true and the other is false,
;; and that the result is false if and only if both arguments are true or both
;; arguments are false. If either or both arguments are null, the result is
;; null.
(deftest compile-xor-test
  (testing "Static"
    (are [x y res] (= res (c/compile {} (elm/xor [x y])))
      #elm/boolean"true" #elm/boolean"true" false
      #elm/boolean"true" #elm/boolean"false" true
      #elm/boolean"true" {:type "Null"} nil

      #elm/boolean"false" #elm/boolean"true" true
      #elm/boolean"false" #elm/boolean"false" false
      #elm/boolean"false" {:type "Null"} nil

      {:type "Null"} #elm/boolean"true" nil
      {:type "Null"} #elm/boolean"false" nil
      {:type "Null"} {:type "Null"} nil))

  (testing "Dynamic"
    (with-open [node (mem-node-with [])]
      (let [context {:eval-context "Patient" :node node}]
        ;; dynamic-resource will evaluate to true
        (are [x y res] (= res (core/-eval (c/compile context (elm/xor [x y])) {} true nil))
          #elm/boolean"true" tu/dynamic-resource false
          tu/dynamic-resource #elm/boolean"true" false

          #elm/boolean"false" tu/dynamic-resource true
          tu/dynamic-resource #elm/boolean"false" true

          tu/dynamic-resource tu/dynamic-resource false)

        ;; dynamic-resource will evaluate to false
        (are [x y res] (= res (core/-eval (c/compile context (elm/xor [x y])) {} false nil))
          #elm/boolean"true" tu/dynamic-resource true
          tu/dynamic-resource #elm/boolean"true" true

          #elm/boolean"false" tu/dynamic-resource false
          tu/dynamic-resource #elm/boolean"false" false

          tu/dynamic-resource tu/dynamic-resource false)

        ;; dynamic-resource will evaluate to nil
        (are [x y res] (= res (core/-eval (c/compile context (elm/xor [x y])) {} nil nil))
          #elm/boolean"true" tu/dynamic-resource nil
          tu/dynamic-resource #elm/boolean"true" nil

          #elm/boolean"false" tu/dynamic-resource nil
          tu/dynamic-resource #elm/boolean"false" nil

          {:type "Null"} tu/dynamic-resource nil
          tu/dynamic-resource {:type "Null"} nil

          tu/dynamic-resource tu/dynamic-resource nil)))))
