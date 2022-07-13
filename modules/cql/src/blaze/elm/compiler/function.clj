(ns blaze.elm.compiler.function
  (:require
    [blaze.elm.compiler.core :as core]))


(defn arity-n [name fn-expr operand-names operands]
  (reify core/Expression
    (-eval [_ context resource scope]
      (let [values (map #(core/-eval % context resource scope) operands)]
        (core/-eval fn-expr context resource (merge scope (zipmap operand-names values)))))
    (-form [_]
      `(~'call ~name ~@(map core/-form operands)))))
