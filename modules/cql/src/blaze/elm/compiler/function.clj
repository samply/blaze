(ns blaze.elm.compiler.function
  (:require
    [blaze.elm.compiler.core :as core]))


(defn arity-n [library-name name fn-expr operand-names operands]
  (reify core/Expression
    (-eval [_ context resource scope]
      #p name
      #p (core/-form fn-expr)
      (let [values (map #(core/-eval % context resource scope) operands)]
        #p (core/-eval fn-expr context resource #p (merge scope (zipmap operand-names values)))))
    (-form [_]
      `(~'call ~(str library-name "." name) ~@(map core/-form operands)))))
