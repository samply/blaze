(ns blaze.elm.compiler.logical-operators
  "13. Logical Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.macros :refer [defunop]]))


;; 13.1. And
(defn- and-nil-op [x]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (and-nil-op (core/-attach-cache x cache)))
    (-patient-count [_]
      0)
    (-resolve-refs [_ expression-defs]
      (and-nil-op (core/-resolve-refs x expression-defs)))
    (-resolve-params [_ parameters]
      (and-nil-op (core/-resolve-params x parameters)))
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
    (and-nil-op x)))


(defn and-op [a b]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (and-op (core/-attach-cache a cache) (core/-attach-cache b cache)))
    (-patient-count [_]
      (min (core/-patient-count a) (core/-patient-count b)))
    (-resolve-refs [_ expression-defs]
      (and-op (core/-resolve-refs a expression-defs)
              (core/-resolve-refs b expression-defs)))
    (-resolve-params [_ parameters]
      (and-op (core/-resolve-params a parameters)
              (core/-resolve-params b parameters)))
    (-eval [_ context resource scope]
      (let [a (core/-eval a context resource scope)]
        (if (false? a)
          false
          (let [b (core/-eval b context resource scope)]
            (cond
              (false? b) false
              (and (true? a) (true? b)) true)))))
    (-form [_]
      (list 'and (core/-form a) (core/-form b)))))


(defn- dynamic-and
  "Creates an and-expression where `a` is known to be dynamic and `b` could be
  static or dynamic."
  [a b]
  (condp identical? b
    true a
    false false
    nil (and-nil-op a)
    (and-op a b)))


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
(declare not-op)

(defunop not [operand]
  (when (some? operand)
    (not operand)))


;; 13.4. Or
(defn- or-nil-op [x]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (or-nil-op (core/-attach-cache x cache)))
    (-patient-count [_]
      (core/-patient-count x))
    (-resolve-refs [_ expression-defs]
      (or-nil-op (core/-resolve-refs x expression-defs)))
    (-resolve-params [_ parameters]
      (or-nil-op (core/-resolve-params x parameters)))
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
    (or-nil-op x)))


(defn or-op [a b]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (let [a (core/-attach-cache a cache)
            b (core/-attach-cache b cache)
            a-count (core/-patient-count a)
            b-count (core/-patient-count b)]
        (if (< a-count b-count)
          (or-op b a)
          (or-op a b))))
    (-patient-count [_]
      (max (core/-patient-count a) (core/-patient-count b)))
    (-resolve-refs [_ expression-defs]
      (or-op (core/-resolve-refs a expression-defs)
             (core/-resolve-refs b expression-defs)))
    (-resolve-params [_ parameters]
      (or-op (core/-resolve-params a parameters)
             (core/-resolve-params b parameters)))
    (-eval [_ context resource scope]
      (let [a (core/-eval a context resource scope)]
        (if (true? a)
          true
          (let [b (core/-eval b context resource scope)]
            (cond
              (true? b) true
              (and (false? a) (false? b)) false)))))
    (-form [_]
      (list 'or (core/-form a) (core/-form b)))))


(defn- dynamic-or
  "Creates an or-expression where `a` is known to be dynamic and `b` could be
  static or dynamic."
  [a b]
  (condp identical? b
    true true
    false a
    nil (or-nil-op a)
    (or-op a b)))


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
    (not-op a)
    false a
    nil nil
    (reify core/Expression
      (-static [_]
        false)
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
