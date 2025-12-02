(ns blaze.elm.compiler.function
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.macros :refer [reify-expr]]))

(defn arity-0 [name fn-expr]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper #(arity-0 name %) cache fn-expr))
    (-resolve-refs [_ expression-defs]
      (arity-0 name (core/-resolve-refs fn-expr expression-defs)))
    (-resolve-params [_ parameters]
      (arity-0 name (core/-resolve-params fn-expr parameters)))
    (-optimize [_ db]
      (arity-0 name (core/-optimize fn-expr db)))
    (-eval [_ context resource scope]
      (core/-eval fn-expr context resource scope))
    (-form [_]
      `(~'call ~name))))

(defn arity-1 [name fn-expr op-name op]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper #(arity-1 name %1 op-name %2) cache fn-expr op))
    (-resolve-refs [_ expression-defs]
      (arity-1 name (core/-resolve-refs fn-expr expression-defs) op-name
               (core/-resolve-refs op expression-defs)))
    (-resolve-params [_ parameters]
      (arity-1 name (core/-resolve-params fn-expr parameters) op-name
               (core/-resolve-params op parameters)))
    (-optimize [_ db]
      (arity-1 name (core/-optimize fn-expr db) op-name (core/-optimize op db)))
    (-eval [_ context resource scope]
      (let [op (core/-eval op context resource scope)]
        (core/-eval fn-expr context resource (assoc scope op-name op))))
    (-form [_]
      `(~'call ~name ~(core/-form op)))))

(defn arity-2 [name fn-expr op-name-1 op-name-2 op-1 op-2]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper #(arity-2 name %1 op-name-1 op-name-2 %2 %3)
                                cache fn-expr op-1 op-2))
    (-resolve-refs [_ expression-defs]
      (arity-2 name (core/-resolve-refs fn-expr expression-defs)
               op-name-1 op-name-2
               (core/-resolve-refs op-1 expression-defs)
               (core/-resolve-refs op-2 expression-defs)))
    (-resolve-params [_ parameters]
      (arity-2 name (core/-resolve-params fn-expr parameters)
               op-name-1 op-name-2
               (core/-resolve-params op-1 parameters)
               (core/-resolve-params op-2 parameters)))
    (-optimize [_ db]
      (arity-2 name (core/-optimize fn-expr db) op-name-1 op-name-2
               (core/-optimize op-1 db) (core/-optimize op-2 db)))
    (-eval [_ context resource scope]
      (let [op-1 (core/-eval op-1 context resource scope)
            op-2 (core/-eval op-2 context resource scope)
            scope (-> (assoc scope op-name-1 op-1 op-name-2 op-2)
                      (assoc op-name-2 op-2))]
        (core/-eval fn-expr context resource scope)))
    (-form [_]
      `(~'call ~name ~(core/-form op-1) ~(core/-form op-2)))))

(defn arity-n [name fn-expr op-names & ops]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (apply core/attach-cache-helper
             (fn [fn-expr & ops]
               (apply arity-n name fn-expr op-names ops))
             cache fn-expr ops))
    (-resolve-refs [_ expression-defs]
      (apply arity-n name (core/-resolve-refs fn-expr expression-defs) op-names
             (map #(core/-resolve-refs % expression-defs) ops)))
    (-resolve-params [_ parameters]
      (apply arity-n name (core/-resolve-params fn-expr parameters) op-names
             (map #(core/-resolve-params % parameters) ops)))
    (-optimize [_ db]
      (apply arity-n name (core/-optimize fn-expr db) op-names
             (map #(core/-optimize % db) ops)))
    (-eval [_ context resource scope]
      (let [operands (map #(core/-eval % context resource scope) ops)]
        (core/-eval fn-expr context resource
                    (loop [scope scope
                           op-names (seq op-names)
                           ops (seq operands)]
                      (if (and op-names ops)
                        (recur (assoc scope (first op-names) (first ops))
                               (next op-names) (next ops))
                        scope)))))
    (-form [_]
      `(~'call ~name ~@(map core/-form ops)))))
