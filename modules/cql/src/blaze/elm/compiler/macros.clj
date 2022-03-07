(ns blaze.elm.compiler.macros
  (:require
    [blaze.elm.compiler.core :as core]))


(defn- compile-kw [name]
  (keyword "elm.compiler.type" (clojure.core/name name)))


(defmacro defunop
  {:arglists '([name attr-map? bindings & body])}
  [name & more]
  (let [attr-map (when (map? (first more)) (first more))
        more (if (map? (first more)) (next more) more)
        [[operand-binding expr-binding] & body] more]
    (if expr-binding
      `(defmethod core/compile* ~(compile-kw name)
         [context# expr#]
         (let [operand# (core/compile* (merge context# ~attr-map) (:operand expr#))]
           (if (core/static? operand#)
             (let [~operand-binding operand#
                   ~expr-binding expr#]
               ~@body)
             (reify core/Expression
               (~'-eval [~'_ context# resource# scope#]
                 (let [~operand-binding (core/-eval operand# context# resource# scope#)
                       ~expr-binding expr#]
                   ~@body))
               (~'-form [~'_]
                 (list (quote ~name) (core/-form operand#)))))))
      `(defmethod core/compile* ~(compile-kw name)
         [context# expr#]
         (let [operand# (core/compile* (merge context# ~attr-map) (:operand expr#))]
           (if (core/static? operand#)
             (let [~operand-binding operand#]
               ~@body)
             (reify core/Expression
               (~'-eval [~'_ context# resource# scope#]
                 (let [~operand-binding (core/-eval operand# context# resource# scope#)]
                   ~@body))
               (~'-form [~'_]
                 (list (quote ~name) (core/-form operand#))))))))))


(defmacro defbinop
  {:arglists '([name attr-map? bindings & body])}
  [name & more]
  (let [attr-map (when (map? (first more)) (first more))
        more (if (map? (first more)) (next more) more)
        [[op-1-binding op-2-binding] & body] more]
    `(defmethod core/compile* ~(compile-kw name)
       [context# {[operand-1# operand-2#] :operand}]
       (let [context# (merge context# ~attr-map)
             operand-1# (core/compile* context# operand-1#)
             operand-2# (core/compile* context# operand-2#)]
         (if (and (core/static? operand-1#) (core/static? operand-2#))
           (let [~op-1-binding operand-1#
                 ~op-2-binding operand-2#]
             ~@body)
           (reify core/Expression
             (~'-eval [~'_ context# resource# scope#]
               (let [~op-1-binding (core/-eval operand-1# context# resource# scope#)
                     ~op-2-binding (core/-eval operand-2# context# resource# scope#)]
                 ~@body))
             (~'-form [~'_]
               (list (quote ~name) (core/-form operand-1#) (core/-form operand-2#)))))))))


(defmacro defternop
  {:arglists '([name bindings & body])}
  [name [op-1-binding op-2-binding op-3-binding] & body]
  `(defmethod core/compile* ~(compile-kw name)
     [context# {[operand-1# operand-2# operand-3#] :operand}]
     (let [operand-1# (core/compile* context# operand-1#)
           operand-2# (core/compile* context# operand-2#)
           operand-3# (core/compile* context# operand-3#)]
       (reify core/Expression
         (~'-eval [~'_ context# resource# scope#]
           (let [~op-1-binding (core/-eval operand-1# context# resource# scope#)
                 ~op-2-binding (core/-eval operand-2# context# resource# scope#)
                 ~op-3-binding (core/-eval operand-3# context# resource# scope#)]
             ~@body))))))


(defmacro defnaryop
  {:arglists '([name bindings & body])}
  [name [operands-binding] & body]
  `(defmethod core/compile* ~(compile-kw name)
     [context# {operands# :operand}]
     (let [operands# (mapv #(core/compile* context# %) operands#)]
       (reify core/Expression
         (~'-eval [~'_ context# resource# scope#]
           (let [~operands-binding (mapv #(core/-eval % context# resource# scope#) operands#)]
             ~@body))))))


(defmacro defaggop
  {:arglists '([name bindings & body])}
  [name [source-binding] & body]
  `(defmethod core/compile* ~(compile-kw name)
     [context# {source# :source}]
     (let [source# (core/compile* context# source#)]
       (reify core/Expression
         (~'-eval [~'_ context# resource# scope#]
           (let [~source-binding (core/-eval source# context# resource# scope#)]
             ~@body))))))


(defmacro defunopp
  {:arglists '([name bindings & body])}
  [name [operand-binding precision-binding expr-binding] & body]
  `(defmethod core/compile* ~(compile-kw name)
     [context# {operand# :operand precision# :precision :as expr#}]
     (let [operand# (core/compile* context# operand#)
           ~precision-binding (some-> precision# core/to-chrono-unit)
           ~(or expr-binding '_) expr#]
       (reify core/Expression
         (~'-eval [~'_ context# resource# scope#]
           (let [~operand-binding (core/-eval operand# context# resource# scope#)]
             ~@body))
         (~'-form [~'_]
           (list (quote ~name) (core/-form operand#) precision#))))))


(defmacro defbinopp
  {:arglists '([name attr-map? bindings & body])}
  [name & more]
  (let [attr-map (when (map? (first more)) (first more))
        more (if (map? (first more)) (next more) more)
        [[op-1-binding op-2-binding precision-binding] & body] more]
    `(defmethod core/compile* ~(compile-kw name)
       [context# {[operand-1# operand-2#] :operand precision# :precision}]
       (let [context# (merge context# ~attr-map)
             operand-1# (core/compile* context# operand-1#)
             operand-2# (core/compile* context# operand-2#)
             chrono-precision# (some-> precision# core/to-chrono-unit)]
         (if (and (core/static? operand-1#) (core/static? operand-2#))
           (let [~op-1-binding operand-1#
                 ~op-2-binding operand-2#
                 ~precision-binding chrono-precision#]
             ~@body)
           (reify core/Expression
             (~'-eval [~'_ context# resource# scope#]
               (let [~op-1-binding (core/-eval operand-1# context# resource# scope#)
                     ~op-2-binding (core/-eval operand-2# context# resource# scope#)
                     ~precision-binding chrono-precision#]
                 ~@body))
             (~'-form [~'_]
               (list (quote ~name) (core/-form operand-1#) (core/-form operand-2#)
                     precision#))))))))
