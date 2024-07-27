(ns blaze.elm.compiler.function
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.macros :refer [reify-expr]]))

(defn arity-n [name fn-expr operand-names operands]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (let [[fn-expr fn-expr-bfs] ((first (core/-attach-cache fn-expr cache)))
            [operands operands-bfs] (core/attach-cache-expressions cache operands)]
        [(fn [] [(arity-n name fn-expr operand-names operands) (into (or fn-expr-bfs []) operands-bfs)])]))
    (-resolve-refs [_ expression-defs]
      (arity-n name (core/-resolve-refs fn-expr expression-defs) operand-names
               (map #(core/-resolve-refs % expression-defs) operands)))
    (-resolve-params [_ parameters]
      (arity-n name (core/-resolve-params fn-expr parameters) operand-names
               (map #(core/-resolve-params % parameters) operands)))
    (-optimize [_ node]
      (arity-n name (core/-optimize fn-expr node) operand-names
               (map #(core/-optimize % node) operands)))
    (-eval [_ context resource scope]
      (let [values (map #(core/-eval % context resource scope) operands)]
        (core/-eval fn-expr context resource (merge scope (zipmap operand-names values)))))
    (-form [_]
      `(~'call ~name ~@(map core/-form operands)))))
