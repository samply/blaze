(ns blaze.elm.compiler.function
  (:require
   [blaze.elm.compiler.core :as core]))

(defn arity-n [name fn-expr operand-names operands]
  (reify core/Expression
    (-static [_]
      false)
    (-attach-cache [_ cache]
      (arity-n name (core/-attach-cache fn-expr cache) operand-names
               (map #(core/-attach-cache % cache) operands)))
    (-resolve-refs [_ expression-defs]
      (arity-n name (core/-resolve-refs fn-expr expression-defs) operand-names
               (map #(core/-resolve-refs % expression-defs) operands)))
    (-resolve-params [_ parameters]
      (arity-n name (core/-resolve-params fn-expr parameters) operand-names
               (map #(core/-resolve-params % parameters) operands)))
    (-eval [_ context resource scope]
      (let [values (map #(core/-eval % context resource scope) operands)]
        (core/-eval fn-expr context resource (merge scope (zipmap operand-names values)))))
    (-form [_]
      `(~'call ~name ~@(map core/-form operands)))))
