(ns blaze.elm.compiler.logical-operators-test
  "13. Logical Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.logical-operators]
    [blaze.elm.compiler.test-util :as ctu]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]))


(st/instrument)
(ctu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (ctu/instrument-compile)
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
    (are [x y pred] (pred (c/compile {} (elm/and [x y])))
      #elm/boolean "true" #elm/boolean "true" true?
      #elm/boolean "true" #elm/boolean "false" false?
      #elm/boolean "true" {:type "Null"} nil?

      #elm/boolean "false" #elm/boolean "true" false?
      #elm/boolean "false" #elm/boolean "false" false?
      #elm/boolean "false" {:type "Null"} false?

      {:type "Null"} #elm/boolean "true" nil?
      {:type "Null"} #elm/boolean "false" false?
      {:type "Null"} {:type "Null"} nil?))

  (testing "Dynamic"
    (are [x y pred] (pred (ctu/dynamic-compile-eval (elm/and [x y])))
      #elm/boolean "true" #elm/parameter-ref "true" true?
      #elm/parameter-ref "true" #elm/boolean "true" true?
      #elm/parameter-ref "true" #elm/parameter-ref "true" true?
      #elm/parameter-ref "true" {:type "Null"} nil?
      {:type "Null"} #elm/parameter-ref "true" nil?

      #elm/boolean "true" #elm/parameter-ref "false" false?
      #elm/parameter-ref "false" #elm/boolean "true" false?
      #elm/parameter-ref "false" #elm/parameter-ref "false" false?
      #elm/parameter-ref "false" {:type "Null"} false?
      {:type "Null"} #elm/parameter-ref "false" false?

      #elm/boolean "false" #elm/parameter-ref "nil" false?
      #elm/parameter-ref "nil" #elm/boolean "false" false?
      #elm/boolean "true" #elm/parameter-ref "nil" nil?
      #elm/parameter-ref "nil" #elm/boolean "true" nil?
      #elm/parameter-ref "nil" #elm/parameter-ref "nil" nil?))

  (testing "form"
    (are [x y form] (= form (c/form (ctu/dynamic-compile (elm/and [x y]))))
      #elm/boolean "true" #elm/boolean "true" true
      #elm/boolean "true" #elm/boolean "false" false
      #elm/boolean "true" {:type "Null"} nil
      #elm/boolean "true" #elm/parameter-ref "b" '(param-ref "b")

      #elm/boolean "false" #elm/boolean "true" false
      #elm/boolean "false" #elm/boolean "false" false
      #elm/boolean "false" {:type "Null"} false
      #elm/boolean "false" #elm/parameter-ref "b" false

      {:type "Null"} #elm/boolean "true" nil
      {:type "Null"} #elm/boolean "false" false
      {:type "Null"} {:type "Null"} nil
      {:type "Null"} #elm/parameter-ref "b" '(and nil (param-ref "b"))

      #elm/parameter-ref "a" #elm/boolean "true" '(param-ref "a")
      #elm/parameter-ref "a" #elm/boolean "false" false
      #elm/parameter-ref "a" {:type "Null"} '(and nil (param-ref "a"))
      #elm/parameter-ref "a" #elm/parameter-ref "b" '(and (param-ref "a") (param-ref "b"))))

  (testing "static"
    (are [x y pred] (pred (core/-static (ctu/dynamic-compile (elm/and [x y]))))
      #elm/boolean "true" #elm/boolean "true" true?
      #elm/boolean "true" #elm/boolean "false" true?
      #elm/boolean "true" {:type "Null"} true?
      #elm/boolean "true" #elm/parameter-ref "b" false?

      #elm/boolean "false" #elm/boolean "true" true?
      #elm/boolean "false" #elm/boolean "false" true?
      #elm/boolean "false" {:type "Null"} true?
      #elm/boolean "false" #elm/parameter-ref "b" true?

      {:type "Null"} #elm/boolean "true" true?
      {:type "Null"} #elm/boolean "false" true?
      {:type "Null"} {:type "Null"} true?
      {:type "Null"} #elm/parameter-ref "b" false?

      #elm/parameter-ref "a" #elm/boolean "true" false?
      #elm/parameter-ref "a" #elm/boolean "false" true?
      #elm/parameter-ref "a" {:type "Null"} false?
      #elm/parameter-ref "a" #elm/parameter-ref "b" false?)))


;; 13.2. Implies
;;
;; Normalized to (Or (Not x) y)
(deftest compile-implies-test
  (ctu/unsupported-binary-operand "Implies"))


;; 13.3. Not
;;
;; The Not operator returns the logical negation of its argument. If the
;; argument is true, the result is false; if the argument is false, the result
;; is true; otherwise, the result is null.
(deftest compile-not-test
  (testing "Static"
    (are [x pred] (pred (c/compile {} (elm/not x)))
      #elm/boolean "true" false?
      #elm/boolean "false" true?))

  (testing "Dynamic"
    (are [x pred] (pred (ctu/dynamic-compile-eval (elm/not x)))
      #elm/parameter-ref "true" false?
      #elm/parameter-ref "false" true?))

  (ctu/testing-unary-null elm/not)

  (ctu/testing-unary-dynamic elm/not)

  (ctu/testing-unary-form elm/not))


;; 13.4. Or
;;
;; The Or operator returns the logical disjunction of its arguments. Note that
;; this operator is defined using 3-valued logic semantics. This means that if
;; either argument is true, the result is true; if both arguments are false, the
;; result is false; otherwise, the result is null. Note also that ELM does not
;; prescribe short-circuit evaluation.
(deftest compile-or-test
  (testing "Static"
    (are [x y pred] (pred (c/compile {} (elm/or [x y])))
      #elm/boolean "true" #elm/boolean "true" true?
      #elm/boolean "true" #elm/boolean "false" true?
      #elm/boolean "true" {:type "Null"} true?

      #elm/boolean "false" #elm/boolean "true" true?
      #elm/boolean "false" #elm/boolean "false" false?
      #elm/boolean "false" {:type "Null"} nil?

      {:type "Null"} #elm/boolean "true" true?
      {:type "Null"} #elm/boolean "false" nil?
      {:type "Null"} {:type "Null"} nil?))

  (testing "Dynamic"
    (are [x y pred] (pred (ctu/dynamic-compile-eval (elm/or [x y])))
      #elm/boolean "false" #elm/parameter-ref "true" true?
      #elm/parameter-ref "true" #elm/boolean "false" true?
      #elm/parameter-ref "true" #elm/parameter-ref "true" true?
      #elm/parameter-ref "true" {:type "Null"} true?
      {:type "Null"} #elm/parameter-ref "true" true?

      #elm/boolean "false" #elm/parameter-ref "false" false?
      #elm/parameter-ref "false" #elm/boolean "false" false?
      #elm/parameter-ref "false" #elm/parameter-ref "false" false?
      #elm/parameter-ref "false" {:type "Null"} nil?
      {:type "Null"} #elm/parameter-ref "false" nil?

      #elm/boolean "true" #elm/parameter-ref "nil" true?
      #elm/parameter-ref "nil" #elm/boolean "true" true?
      #elm/boolean "false" #elm/parameter-ref "nil" nil?
      #elm/parameter-ref "nil" #elm/boolean "false" nil?
      #elm/parameter-ref "nil" #elm/parameter-ref "nil" nil?))

  (testing "form"
    (are [x y form] (= form (c/form (ctu/dynamic-compile (elm/or [x y]))))
      #elm/boolean "true" #elm/boolean "true" true
      #elm/boolean "true" #elm/boolean "false" true
      #elm/boolean "true" {:type "Null"} true
      #elm/boolean "true" #elm/parameter-ref "b" true

      #elm/boolean "false" #elm/boolean "true" true
      #elm/boolean "false" #elm/boolean "false" false
      #elm/boolean "false" {:type "Null"} nil
      #elm/boolean "false" #elm/parameter-ref "b" '(param-ref "b")

      {:type "Null"} #elm/boolean "true" true
      {:type "Null"} #elm/boolean "false" nil
      {:type "Null"} {:type "Null"} nil
      {:type "Null"} #elm/parameter-ref "b" '(or nil (param-ref "b"))

      #elm/parameter-ref "a" #elm/boolean "true" true
      #elm/parameter-ref "a" #elm/boolean "false" '(param-ref "a")
      #elm/parameter-ref "a" {:type "Null"} '(or nil (param-ref "a"))
      #elm/parameter-ref "a" #elm/parameter-ref "b" '(or (param-ref "a") (param-ref "b"))))

  (testing "static"
    (are [x y pred] (pred (core/-static (ctu/dynamic-compile (elm/or [x y]))))
      #elm/boolean "true" #elm/boolean "true" true?
      #elm/boolean "true" #elm/boolean "false" true?
      #elm/boolean "true" {:type "Null"} true?
      #elm/boolean "true" #elm/parameter-ref "b" true?

      #elm/boolean "false" #elm/boolean "true" true?
      #elm/boolean "false" #elm/boolean "false" true?
      #elm/boolean "false" {:type "Null"} true?
      #elm/boolean "false" #elm/parameter-ref "b" false?

      {:type "Null"} #elm/boolean "true" true?
      {:type "Null"} #elm/boolean "false" true?
      {:type "Null"} {:type "Null"} true?
      {:type "Null"} #elm/parameter-ref "b" false?

      #elm/parameter-ref "a" #elm/boolean "true" true?
      #elm/parameter-ref "a" #elm/boolean "false" false?
      #elm/parameter-ref "a" {:type "Null"} false?
      #elm/parameter-ref "a" #elm/parameter-ref "b" false?)))


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
    (are [x y pred] (pred (c/compile {} (elm/xor [x y])))
      #elm/boolean "true" #elm/boolean "true" false?
      #elm/boolean "true" #elm/boolean "false" true?
      #elm/boolean "true" {:type "Null"} nil?

      #elm/boolean "false" #elm/boolean "true" true?
      #elm/boolean "false" #elm/boolean "false" false?
      #elm/boolean "false" {:type "Null"} nil?

      {:type "Null"} #elm/boolean "true" nil?
      {:type "Null"} #elm/boolean "false" nil?
      {:type "Null"} {:type "Null"} nil?))

  (testing "Dynamic"
    (are [x y pred] (pred (ctu/dynamic-compile-eval (elm/xor [x y])))
      #elm/boolean "true" #elm/parameter-ref "true" false?
      #elm/parameter-ref "true" #elm/boolean "true" false?
      #elm/boolean "false" #elm/parameter-ref "true" true?
      #elm/parameter-ref "true" #elm/boolean "false" true?
      #elm/parameter-ref "true" #elm/parameter-ref "true" false?

      #elm/boolean "true" #elm/parameter-ref "false" true?
      #elm/parameter-ref "false" #elm/boolean "true" true?
      #elm/boolean "false" #elm/parameter-ref "false" false?
      #elm/parameter-ref "false" #elm/boolean "false" false?
      #elm/parameter-ref "false" #elm/parameter-ref "false" false?

      #elm/boolean "true" #elm/parameter-ref "nil" nil?
      #elm/parameter-ref "nil" #elm/boolean "true" nil?
      #elm/boolean "false" #elm/parameter-ref "nil" nil?
      #elm/parameter-ref "nil" #elm/boolean "false" nil?
      {:type "Null"} #elm/parameter-ref "nil" nil?
      #elm/parameter-ref "nil" {:type "Null"} nil?
      #elm/parameter-ref "nil" #elm/parameter-ref "nil" nil?))

  (testing "form"
    (are [x y form] (= form (c/form (ctu/dynamic-compile (elm/xor [x y]))))
      #elm/boolean "true" #elm/boolean "true" false
      #elm/boolean "true" #elm/boolean "false" true
      #elm/boolean "true" {:type "Null"} nil
      #elm/boolean "true" #elm/parameter-ref "b" '(not (param-ref "b"))

      #elm/boolean "false" #elm/boolean "true" true
      #elm/boolean "false" #elm/boolean "false" false
      #elm/boolean "false" {:type "Null"} nil
      #elm/boolean "false" #elm/parameter-ref "b" '(param-ref "b")

      {:type "Null"} #elm/boolean "true" nil
      {:type "Null"} #elm/boolean "false" nil
      {:type "Null"} {:type "Null"} nil
      {:type "Null"} #elm/parameter-ref "b" nil

      #elm/parameter-ref "a" #elm/boolean "true" '(not (param-ref "a"))
      #elm/parameter-ref "a" #elm/boolean "false" '(param-ref "a")
      #elm/parameter-ref "a" {:type "Null"} nil
      #elm/parameter-ref "a" #elm/parameter-ref "b" '(xor (param-ref "a") (param-ref "b"))))

  (testing "static"
    (are [x y pred] (pred (core/-static (ctu/dynamic-compile (elm/xor [x y]))))
      #elm/boolean "true" #elm/boolean "true" true?
      #elm/boolean "true" #elm/boolean "false" true?
      #elm/boolean "true" {:type "Null"} true?
      #elm/boolean "true" #elm/parameter-ref "b" false?

      #elm/boolean "false" #elm/boolean "true" true?
      #elm/boolean "false" #elm/boolean "false" true?
      #elm/boolean "false" {:type "Null"} true?
      #elm/boolean "false" #elm/parameter-ref "b" false?

      {:type "Null"} #elm/boolean "true" true?
      {:type "Null"} #elm/boolean "false" true?
      {:type "Null"} {:type "Null"} true?
      {:type "Null"} #elm/parameter-ref "b" true?

      #elm/parameter-ref "a" #elm/boolean "true" false?
      #elm/parameter-ref "a" #elm/boolean "false" false?
      #elm/parameter-ref "a" {:type "Null"} true?
      #elm/parameter-ref "a" #elm/parameter-ref "b" false?)))
