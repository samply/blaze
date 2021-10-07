(ns blaze.elm.compiler.logical-operators
  "13. Logical Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.macros :refer [defunop]]))


;; 13.1. And

;; static-a is either true or nil but not false
(defrecord StaticAndOperatorExpression [static-a b]
  core/Expression
  (-eval [_ context resource scope]
    (let [b (core/-eval b context resource scope)]
      (cond
        (false? b) false
        (and (true? static-a) (true? b)) true))))


(defn- and-static [static-a b]
  (if (core/static? b)
    (cond
      (false? b) false
      (and (true? static-a) (true? b)) true)
    (->StaticAndOperatorExpression static-a b)))


(defrecord AndOperatorExpression [a b]
  core/Expression
  (-eval [_ context resource scope]
    (let [a (core/-eval a context resource scope)]
      (if (false? a)
        false
        (let [b (core/-eval b context resource scope)]
          (cond
            (false? b) false
            (and (true? a) (true? b)) true))))))


(defmethod core/compile* :elm.compiler.type/and
  [context {[a b] :operand}]
  (let [a (core/compile* context a)]
    (if (core/static? a)
      (if (false? a)
        false
        (and-static a (core/compile* context b)))
      (let [b (core/compile* context b)]
        (if (core/static? b)
          (if (false? b)
            false
            (and-static b a))
          (->AndOperatorExpression a b))))))


;; 13.2 Implies
(defmethod core/compile* :elm.compiler.type/implies
  [_ _]
  (throw (Exception. "Unsupported Implies expression. Please normalize the ELM tree before compiling.")))


;; 13.3 Not
(defunop not [operand]
  (when (some? operand)
    (not operand)))


;; 13.4. Or

;; static-a is either false or nil but not true
(defrecord StaticOrOperatorExpression [static-a b]
  core/Expression
  (-eval [_ context resource scope]
    (let [b (core/-eval b context resource scope)]
      (cond
        (true? b) true
        (and (false? static-a) (false? b)) false))))


(defn- or-static [static-a b]
  (if (core/static? b)
    (cond
      (true? b) true
      (and (false? static-a) (false? b)) false)
    (->StaticOrOperatorExpression static-a b)))


(defrecord OrOperatorExpression [a b]
  core/Expression
  (-eval [_ context resource scope]
    (let [a (core/-eval a context resource scope)]
      (if (true? a)
        true
        (let [b (core/-eval b context resource scope)]
          (cond
            (true? b) true
            (and (false? a) (false? b)) false))))))


(defmethod core/compile* :elm.compiler.type/or
  [context {[a b] :operand}]
  (let [a (core/compile* context a)]
    (if (core/static? a)
      (if (true? a)
        true
        (or-static a (core/compile* context b)))
      (let [operand-2 (core/compile* context b)]
        (if (core/static? operand-2)
          (if (true? operand-2)
            true
            (or-static operand-2 a))
          (->OrOperatorExpression a operand-2))))))


;; 13.5 Xor
(defrecord StaticXOrOperatorExpression [static-a b]
  core/Expression
  (-eval [_ context resource scope]
    (let [b (core/-eval b context resource scope)]
      (cond
        (or (and (true? static-a) (true? b))
            (and (false? static-a) (false? b)))
        false
        (or (and (true? static-a) (false? b))
            (and (false? static-a) (true? b)))
        true))))


(defn- xor-static [static-a b]
  (if (core/static? b)
    (cond
      (or (and (true? static-a) (true? b))
          (and (false? static-a) (false? b)))
      false
      (or (and (true? static-a) (false? b))
          (and (false? static-a) (true? b)))
      true)
    (->StaticXOrOperatorExpression static-a b)))


(defrecord XOrOperatorExpression [a b]
  core/Expression
  (-eval [_ context resource scope]
    (let [a (core/-eval a context resource scope)]
      (if (nil? a)
        nil
        (let [b (core/-eval b context resource scope)]
          (cond
            (or (and (true? a) (true? b))
                (and (false? a) (false? b)))
            false
            (or (and (true? a) (false? b))
                (and (false? a) (true? b)))
            true))))))


(defmethod core/compile* :elm.compiler.type/xor
  [context {[a b] :operand}]
  (let [a (core/compile* context a)]
    (if (core/static? a)
      (if (nil? a)
        nil
        (xor-static a (core/compile* context b)))
      (let [b (core/compile* context b)]
        (if (core/static? b)
          (if (nil? b)
            nil
            (xor-static b a))
          (->XOrOperatorExpression a b))))))
