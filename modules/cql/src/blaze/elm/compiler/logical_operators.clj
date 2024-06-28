(ns blaze.elm.compiler.logical-operators
  "13. Logical Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.logical-operators.util :as u]
   [blaze.elm.compiler.macros :refer [defunop reify-expr]]
   [blaze.elm.expression.cache :as ec]
   [blaze.elm.expression.cache.bloom-filter :as bloom-filter]
   [prometheus.alpha :as prom]))

;; 13.1. And
(defn- and-nil-op [x]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      [(fn [] (and-nil-op ((first (core/-attach-cache x cache)))))])
    (-patient-count [_]
      0)
    (-resolve-refs [_ expression-defs]
      (and-nil-op (core/-resolve-refs x expression-defs)))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper and-nil-op parameters x))
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

(defn- and-list-op [op ops]
  (reify-expr core/Expression
    (-attach-cache [_ _]
      (Exception. "Can't attach a cache to `and-list-op`."))
    (-patient-count [_]
      nil)
    (-resolve-refs [_ _]
      (Exception. "Can't resolve references in `and-list-op`."))
    (-resolve-params [_ _]
      (Exception. "Can't resolve references in `and-list-op`."))
    (-eval [_ context resource scope]
      (reduce
       (fn [a op]
         (if (false? a)
           (reduced false)
           (let [b (core/-eval op context resource scope)]
             (cond
               (false? b) (reduced false)
               (and (true? a) (true? b)) true))))
       (core/-eval op context resource scope)
       ops))
    (-form [_]
      `(~'and ~(core/-form op) ~@(map core/-form ops)))))

(defn- and-cmp [[a-op] [b-op]]
  (let [a-count (or (core/-patient-count a-op) Long/MAX_VALUE)
        b-count (or (core/-patient-count b-op) Long/MAX_VALUE)]
    (- a-count b-count)))

(defn and-op [a b]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (let [[fa a-kind a] (core/-attach-cache a cache)
            [fb b-kind b] (core/-attach-cache b cache)]
        (cond
          (and (identical? :and a-kind) (identical? :and b-kind))
          (u/and-attach-cache-result
           and-list-op
           (u/merge-sorted and-cmp a b))

          (identical? :and a-kind)
          (u/and-attach-cache-result
           and-list-op
           (u/insert-sorted and-cmp a (fb)))

          (identical? :and b-kind)
          (u/and-attach-cache-result
           and-list-op
           (u/insert-sorted and-cmp b (fa)))

          :else
          (let [a (fa)
                b (fb)]
            (if (pos? (and-cmp a b))
              (u/and-attach-cache-result and-list-op [b a])
              (u/and-attach-cache-result and-list-op [a b]))))))
    (-patient-count [_]
      (let [count-a (core/-patient-count a)
            count-b (core/-patient-count b)]
        (when (and count-a count-b)
          (min count-a count-b))))
    (-resolve-refs [_ expression-defs]
      (core/resolve-refs-helper and-op expression-defs a b))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper and-op parameters a b))
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
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      [(fn [] (or-nil-op ((first (core/-attach-cache x cache)))))])
    (-patient-count [_]
      (core/-patient-count x))
    (-resolve-refs [_ expression-defs]
      (or-nil-op (core/-resolve-refs x expression-defs)))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper or-nil-op parameters x))
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

(defn- or-list-op [tuples]
  (reify-expr core/Expression
    (-attach-cache [_ _]
      (Exception. "Can't attach a cache to `or-list-op`."))
    (-patient-count [_]
      nil)
    (-resolve-refs [_ _]
      (Exception. "Can't resolve references in `or-list-op`."))
    (-resolve-params [_ _]
      (Exception. "Can't resolve references in `or-list-op`."))
    (-eval [_ context resource scope]
      (reduce
       (fn [_ [op bf]]
         ;; TODO: handle nil
         (if bf
           (if (bloom-filter/might-contain? bf resource)
             (do (prom/inc! ec/bloom-filter-not-useful-total "or")
                 (if (core/-eval op context resource scope)
                   (reduced true)
                   (do (prom/inc! ec/bloom-filter-false-positive-total "or")
                       false)))
             (do (prom/inc! ec/bloom-filter-useful-total "or")
                 (reduced false)))
           (if (core/-eval op context resource scope)
             (reduced true)
             false)))
       false
       tuples))
    (-form [_]
      `(~'or ~@(map (comp core/-form first) tuples)))))

(defn- or-cmp [[a-op] [b-op]]
  (let [a-count (or (core/-patient-count a-op) Long/MAX_VALUE)
        b-count (or (core/-patient-count b-op) Long/MAX_VALUE)]
    (- b-count a-count)))

(defn or-op [a b]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (let [[fa a-kind a] (core/-attach-cache a cache)
            [fb b-kind b] (core/-attach-cache b cache)]
        (cond
          (and (identical? :or a-kind) (identical? :or b-kind))
          (u/or-attach-cache-result
           or-list-op
           (u/merge-sorted or-cmp a b))

          (identical? :or a-kind)
          (u/or-attach-cache-result
           or-list-op
           (u/insert-sorted or-cmp a (fb)))

          (identical? :or b-kind)
          (u/or-attach-cache-result
           or-list-op
           (u/insert-sorted or-cmp b (fa)))

          :else
          (let [a (fa)
                b (fb)]
            (if (pos? (or-cmp a b))
              (u/or-attach-cache-result or-list-op [b a])
              (u/or-attach-cache-result or-list-op [a b]))))))
    (-patient-count [_]
      (let [count-a (core/-patient-count a)
            count-b (core/-patient-count b)]
        (when (and count-a count-b)
          (max count-a count-b))))
    (-resolve-refs [_ expression-defs]
      (core/resolve-refs-helper or-op expression-defs a b))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper or-op parameters a b))
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
(defn- xor-op [a b]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper xor-op cache a b))
    (-resolve-refs [_ expression-defs]
      (core/resolve-refs-helper xor-op expression-defs a b))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper xor-op parameters a b))
    (-eval [_ context resource scope]
      (when-some [a (core/-eval a context resource scope)]
        (when-some [b (core/-eval b context resource scope)]
          (if a (not b) b))))
    (-form [_]
      (list 'xor (core/-form a) (core/-form b)))))

(defn- dynamic-xor
  "Creates an xor-expression where `a` is known to be dynamic and `b` could be
  static or dynamic."
  [a b]
  (condp identical? b
    true
    (not-op a)
    false a
    nil nil
    (xor-op a b)))

(defmethod core/compile* :elm.compiler.type/xor
  [context {[a b] :operand}]
  (let [a (core/compile* context a)]
    (condp identical? a
      true (core/compile* context {:type "Not" :operand b})
      false (core/compile* context b)
      nil nil
      (dynamic-xor a (core/compile* context b)))))
