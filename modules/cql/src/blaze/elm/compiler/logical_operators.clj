(ns blaze.elm.compiler.logical-operators
  "13. Logical Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.macros :refer [defunop]]))


;; 13.1. And
(defn- nil-and-expr [x]
  (reify core/Expression
    (-eval [_ context resource scope]
      (when (false? (core/-eval x context resource scope))
        false))
    (-form [_]
      (list 'and nil (core/-form x)))))


(defn- nil-and
  "Creates an and-expression where one operand is known to be nil."
  [x]
  (condp identical? x
    true nil
    false false
    nil nil
    (nil-and-expr x)))


(defn- dynamic-and
  "Creates an and-expression where `a` is known to be dynamic and `b` could be
  static or dynamic."
  [a b]
  (condp identical? b
    true a
    false false
    nil (nil-and-expr a)
    (reify core/Expression
      (-eval [_ context resource scope]
        (let [a (core/-eval a context resource scope)]
          (if (false? a)
            false
            (let [b (core/-eval b context resource scope)]
              (cond
                (false? b) false
                (and (true? a) (true? b)) true)))))
      (-form [_]
        (list 'and (core/-form a) (core/-form b))))))


(defmethod core/compile* :elm.compiler.type/and
  [context {[a b] :operand}]
  (let [a (core/compile* context a)]
    (condp identical? a
      true (core/compile* context b)
      false false
      nil (nil-and (core/compile* context b))
      (dynamic-and a (core/compile* context b)))))


;; 13.2 Implies
(defmethod core/compile* :elm.compiler.type/implies
  [_ _]
  (throw (Exception. "Unsupported Implies expression. Please normalize the ELM tree before compiling.")))


;; 13.3 Not
(defunop not [operand]
  (when (some? operand)
    (not operand)))


;; 13.4. Or
(defn- nil-or-expr [x]
  (reify core/Expression
    (-eval [_ context resource scope]
      (when (true? (core/-eval x context resource scope))
        true))
    (-form [_]
      (list 'or nil (core/-form x)))))


(defn- nil-or
  "Creates an or-expression where one operand is known to be nil."
  [x]
  (condp identical? x
    true true
    false nil
    nil nil
    (nil-or-expr x)))


(defn- dynamic-or
  "Creates an or-expression where `a` is known to be dynamic and `b` could be
  static or dynamic."
  [a b]
  (condp identical? b
    true true
    false a
    nil (nil-or-expr a)
    (reify core/Expression
      (-eval [_ context resource scope]
        (let [a (core/-eval a context resource scope)]
          (if (true? a)
            true
            (let [b (core/-eval b context resource scope)]
              (cond
                (true? b) true
                (and (false? a) (false? b)) false)))))
      (-form [_]
        (list 'or (core/-form a) (core/-form b))))))


(defmethod core/compile* :elm.compiler.type/or
  [context {[a b] :operand}]
  (let [a (core/compile* context a)]
    (condp identical? a
      true true
      false (core/compile* context b)
      nil (nil-or (core/compile* context b))
      (dynamic-or a (core/compile* context b)))))


;; 13.5 Xor
(defn- dynamic-xor
  "Creates an xor-expression where `a` is known to be dynamic and `b` could be
  static or dynamic."
  [a b]
  (condp identical? b
    true
    (reify core/Expression
      (-eval [_ context resource scope]
        (let [a (core/-eval a context resource scope)]
          (when (some? a)
            (not a))))
      (-form [_]
        (list 'not (core/-form a))))
    false a
    nil nil
    (reify core/Expression
      (-eval [_ context resource scope]
        (when-some [a (core/-eval a context resource scope)]
          (when-some [b (core/-eval b context resource scope)]
            (if a (not b) b))))
      (-form [_]
        (list 'xor (core/-form a) (core/-form b))))))


(defmethod core/compile* :elm.compiler.type/xor
  [context {[a b] :operand}]
  (let [a (core/compile* context a)]
    (condp identical? a
      true (core/compile* context {:type "Not" :operand b})
      false (core/compile* context b)
      nil nil
      (dynamic-xor a (core/compile* context b)))))
