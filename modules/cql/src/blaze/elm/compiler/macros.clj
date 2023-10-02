(ns blaze.elm.compiler.macros
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.expression.cache :as ec]
   [blaze.elm.expression.cache.bloom-filter :as bloom-filter]
   [blaze.elm.expression.cache.spec]
   [clojure.spec.alpha :as s]
   [prometheus.alpha :as prom]))

(set! *warn-on-reflection* true)

(defn- generate-binding-vector
  "Creates a binding vector of at least `[operand-binding operand]` and
  optionally `[operand-binding operand elm-elm-expr-binding elm-expr]` if
  `elm-elm-expr-binding` is given."
  [operand-binding operand elm-expr-binding elm-expr]
  (cond-> [operand-binding operand] elm-expr-binding (conj elm-expr-binding elm-expr)))

(defn- compile-kw [name]
  (keyword "elm.compiler.type" (clojure.core/name name)))

(defmacro defunop
  {:arglists '([name attr-map? bindings & body])}
  [name & more]
  (let [attr-map (when (map? (first more)) (first more))
        more (if (map? (first more)) (next more) more)
        [[operand-binding elm-expr-binding] & body] more
        elm-expr (gensym "elm-expr")
        operand (gensym "operand")
        context (gensym "context")
        eval-context (gensym "eval-context")
        resource (gensym "resource")
        scope (gensym "scope")
        bloom-filter (gensym "bloom-filter")
        expr (gensym "expr")]
    `(do
       ~(when (:cache attr-map)
          `(do
             (declare ~(symbol (str name "-caching-op")))

             (s/fdef ~(symbol (str name "-cache-op"))
               :args ~(if elm-expr-binding
                        `(s/cat :operand core/expr?
                                :bloom-filter ::ec/bloom-filter
                                :expr :elm/expression)
                        `(s/cat :operand core/expr?
                                :bloom-filter ::ec/bloom-filter))
               :ret core/expr?)

             (defn ~(symbol (str name "-cache-op"))
               ~(cond-> [operand bloom-filter] elm-expr-binding (conj elm-expr))
               (reify
                 core/Expression
                 (~'-static [~'_]
                   false)
                 (~'-attach-cache [~expr ~'cache]
                   (if-let [~bloom-filter (ec/get ~'cache ~expr)]
                     ~(if elm-expr-binding
                        `(~(symbol (str name "-cache-op"))
                          ~operand ~bloom-filter ~elm-expr)
                        `(~(symbol (str name "-cache-op"))
                          ~operand ~bloom-filter))
                     ~(if elm-expr-binding
                        `(~(symbol (str name "-caching-op"))
                          ~operand ~elm-expr)
                        `(~(symbol (str name "-caching-op"))
                          ~operand))))
                 (~'-patient-count [~'_]
                   (::bloom-filter/patient-count ~bloom-filter))
                 (~'-resolve-refs [~'_ ~'expression-defs]
                   ~(if elm-expr-binding
                      `(~(symbol (str name "-caching-op"))
                        (core/-resolve-refs ~operand ~'expression-defs)
                        ~elm-expr)
                      `(~(symbol (str name "-caching-op"))
                        (core/-resolve-refs ~operand ~'expression-defs))))
                 (~'-resolve-params [~'_ ~'parameters]
                   ~(if elm-expr-binding
                      `(~(symbol (str name "-caching-op"))
                        (core/-resolve-params ~operand ~'parameters)
                        ~elm-expr)
                      `(~(symbol (str name "-caching-op"))
                        (core/-resolve-params ~operand ~'parameters))))
                 (~'-eval [~'_ ~context ~resource ~scope]
                   (if (bloom-filter/might-contain? ~bloom-filter ~resource)
                     (let [res# (let ~(generate-binding-vector
                                       operand-binding `(core/-eval ~operand
                                                                    ~context
                                                                    ~resource
                                                                    ~scope)
                                       elm-expr-binding elm-expr)
                                  ~@body)]
                       (prom/inc! ec/bloom-filter-not-useful-total)
                       (when-not res#
                         (prom/inc! ec/bloom-filter-false-positive-total))
                       res#)
                     (do (prom/inc! ec/bloom-filter-useful-total)
                         false)))
                 (~'-form [~'_]
                   (list (quote ~name) (core/-form ~operand)))

                 Object
                 (~'equals [~'this ~'other]
                   (.equals ^Object (core/-form ~'this) (core/-form ~'other)))
                 (~'hashCode [~'this]
                   (.hashCode ^Object (core/-form ~'this)))))

             (s/fdef ~(symbol (str name "-caching-op"))
               :args ~(if elm-expr-binding
                        `(s/cat :operand core/expr? :expr :elm/expression)
                        `(s/cat :operand core/expr?))
               :ret core/expr?)

             (defn ~(symbol (str name "-caching-op"))
               ~(cond-> [operand] elm-expr-binding (conj elm-expr))
               (reify
                 core/Expression
                 (~'-static [~'_]
                   false)
                 (~'-attach-cache [~expr ~'cache]
                   (if-let [~bloom-filter (ec/get ~'cache ~expr)]
                     ~(if elm-expr-binding
                        `(~(symbol (str name "-cache-op"))
                          ~operand ~bloom-filter ~elm-expr)
                        `(~(symbol (str name "-cache-op"))
                          ~operand ~bloom-filter))
                     ~(if elm-expr-binding
                        `(~(symbol (str name "-caching-op"))
                          ~operand ~elm-expr)
                        `(~(symbol (str name "-caching-op"))
                          ~operand))))
                 (~'-patient-count [~'_]
                   0)
                 (~'-resolve-refs [~'_ ~'expression-defs]
                   ~(if elm-expr-binding
                      `(~(symbol (str name "-caching-op"))
                        (core/-resolve-refs ~operand ~'expression-defs)
                        ~elm-expr)
                      `(~(symbol (str name "-caching-op"))
                        (core/-resolve-refs ~operand ~'expression-defs))))
                 (~'-resolve-params [~'_ ~'parameters]
                   ~(if elm-expr-binding
                      `(~(symbol (str name "-caching-op"))
                        (core/-resolve-params ~operand ~'parameters)
                        ~elm-expr)
                      `(~(symbol (str name "-caching-op"))
                        (core/-resolve-params ~operand ~'parameters))))
                 (~'-eval [~'_ ~context ~resource ~scope]
                   (let ~(generate-binding-vector
                          operand-binding `(core/-eval ~operand ~context ~resource ~scope)
                          elm-expr-binding elm-expr)
                     ~@body))
                 (~'-form [~'_]
                   (list (quote ~name) (core/-form ~operand)))

                 Object
                 (~'equals [~'this ~'other]
                   (.equals ^Object (core/-form ~'this) (core/-form ~'other)))
                 (~'hashCode [~'this]
                   (.hashCode ^Object (core/-form ~'this)))))))

       (defn ~(symbol (str name "-op"))
         ~(cond-> [operand] elm-expr-binding (conj elm-expr))
         (reify
           core/Expression
           (~'-static [~'_]
             false)
           (~'-attach-cache [~'_ ~'cache]
             ~(if elm-expr-binding
                `(~(symbol (str name "-op"))
                  (core/-attach-cache ~operand ~'cache) ~elm-expr)
                `(~(symbol (str name "-op"))
                  (core/-attach-cache ~operand ~'cache))))
           (~'-patient-count [~'_]
             0)
           (~'-resolve-refs [~'_ ~'expression-defs]
             ~(if elm-expr-binding
                `(~(symbol (str name "-op"))
                  (core/-resolve-refs ~operand ~'expression-defs)
                  ~elm-expr)
                `(~(symbol (str name "-op"))
                  (core/-resolve-refs ~operand ~'expression-defs))))
           (~'-resolve-params [~'_ ~'parameters]
             ~(if elm-expr-binding
                `(~(symbol (str name "-op"))
                  (core/-resolve-params ~operand ~'parameters)
                  ~elm-expr)
                `(~(symbol (str name "-op"))
                  (core/-resolve-params ~operand ~'parameters))))
           (~'-eval [~'_ ~context ~resource ~scope]
             (let ~(generate-binding-vector
                    operand-binding `(core/-eval ~operand ~context ~resource ~scope)
                    elm-expr-binding elm-expr)
               ~@body))
           (~'-form [~'_]
             (list (quote ~name) (core/-form ~operand)))

           Object
           (~'equals [~'this ~'other]
             (.equals ^Object (core/-form ~'this) (core/-form ~'other)))
           (~'hashCode [~'this]
             (.hashCode ^Object (core/-form ~'this)))))

       (defmethod core/compile* ~(compile-kw name)
         [{~eval-context :eval-context :as ~context}
          {~operand :operand :as ~elm-expr}]
         (let [~operand (core/compile* (merge ~context ~(dissoc attr-map :cache)) ~operand)]
           (if (core/static? ~operand)
             (let ~(generate-binding-vector
                    operand-binding operand
                    elm-expr-binding elm-expr)
               ~@body)
             ~(if (:cache attr-map)
                `(if (= "Patient" ~eval-context)
                   ~(if elm-expr-binding
                      `(~(symbol (str name "-caching-op")) ~operand ~elm-expr)
                      `(~(symbol (str name "-caching-op")) ~operand))
                   ~(if elm-expr-binding
                      `(~(symbol (str name "-op")) ~operand ~elm-expr)
                      `(~(symbol (str name "-op")) ~operand)))
                (if elm-expr-binding
                  `(~(symbol (str name "-op")) ~operand ~elm-expr)
                  `(~(symbol (str name "-op")) ~operand)))))))))

(defmacro defbinop
  {:arglists '([name attr-map? bindings & body])}
  [name & more]
  (let [attr-map (when (map? (first more)) (first more))
        more (if (map? (first more)) (next more) more)
        [[op-1-binding op-2-binding] & body] more
        context (gensym "context")
        op-1 (gensym "op-1")
        op-2 (gensym "op-2")]
    `(do
       (defn ~(symbol (str name "-op")) [~op-1 ~op-2]
         (reify core/Expression
           (~'-static [~'_]
             false)
           (~'-attach-cache [~'_ ~'cache]
             (~(symbol (str name "-op"))
              (core/-attach-cache ~op-1 ~'cache)
              (core/-attach-cache ~op-2 ~'cache)))
           (~'-resolve-refs [~'_ ~'expression-defs]
             (~(symbol (str name "-op"))
              (core/-resolve-refs ~op-1 ~'expression-defs)
              (core/-resolve-refs ~op-2 ~'expression-defs)))
           (~'-resolve-params [~'_ ~'parameters]
             (~(symbol (str name "-op"))
              (core/-resolve-params ~op-1 ~'parameters)
              (core/-resolve-params ~op-2 ~'parameters)))
           (~'-eval [~'_ context# resource# scope#]
             (let [~op-1-binding (core/-eval ~op-1 context# resource# scope#)
                   ~op-2-binding (core/-eval ~op-2 context# resource# scope#)]
               ~@body))
           (~'-form [~'_]
             (list (quote ~name) (core/-form ~op-1) (core/-form ~op-2)))))
       (defmethod core/compile* ~(compile-kw name)
         [~context {[~op-1 ~op-2] :operand}]
         (let [context# ~(if attr-map `(merge ~context ~attr-map) context)
               ~op-1 (core/compile* context# ~op-1)
               ~op-2 (core/compile* context# ~op-2)]
           (if (and (core/static? ~op-1) (core/static? ~op-2))
             (let [~op-1-binding ~op-1
                   ~op-2-binding ~op-2]
               ~@body)
             (~(symbol (str name "-op")) ~op-1 ~op-2)))))))

(defmacro defternop
  {:arglists '([name bindings & body])}
  [name [op-1-binding op-2-binding op-3-binding] & body]
  `(defmethod core/compile* ~(compile-kw name)
     [context# {[operand-1# operand-2# operand-3#] :operand}]
     (let [operand-1# (core/compile* context# operand-1#)
           operand-2# (core/compile* context# operand-2#)
           operand-3# (core/compile* context# operand-3#)]
       (reify core/Expression
         (~'-static [~'_]
           false)
         ;;TODO: attach-cache, resolve-refs and resolve-param-refs
         (~'-eval [~'_ context# resource# scope#]
           (let [~op-1-binding (core/-eval operand-1# context# resource# scope#)
                 ~op-2-binding (core/-eval operand-2# context# resource# scope#)
                 ~op-3-binding (core/-eval operand-3# context# resource# scope#)]
             ~@body))
         (~'-form [~'_]
           (list (quote ~name) (core/-form operand-1#) (core/-form operand-2#)
                 (core/-form operand-3#)))))))

(defmacro defnaryop
  {:arglists '([name bindings & body])}
  [name [operands-binding] & body]
  `(do
     (defn ~(symbol (str name "-op")) [~operands-binding]
       (reify core/Expression
         (~'-static [~'_]
           false)
         (~'-attach-cache [~'_ ~'cache]
           (~(symbol (str name "-op"))
            (mapv #(core/-attach-cache % ~'cache) ~operands-binding)))
         (~'-resolve-refs [~'_ ~'expression-defs]
           (~(symbol (str name "-op"))
            (mapv #(core/-resolve-refs % ~'expression-defs) ~operands-binding)))
         (~'-resolve-params [~'_ ~'parameters]
           (~(symbol (str name "-op"))
            (mapv #(core/-resolve-params % ~'parameters) ~operands-binding)))
         (~'-eval [~'_ context# resource# scope#]
           (let [~operands-binding (mapv #(core/-eval % context# resource# scope#) ~operands-binding)]
             ~@body))
         (~'-form [~'_]
           (cons (quote ~name) (map core/-form ~operands-binding)))))
     (defmethod core/compile* ~(compile-kw name)
       [context# {operands# :operand}]
       (~(symbol (str name "-op")) (mapv #(core/compile* context# %) operands#)))))

(defmacro defaggop
  {:arglists '([name bindings & body])}
  [name [source-binding] & body]
  `(do
     (defn ~(symbol (str name "-op")) [~source-binding]
       (reify core/Expression
         (~'-static [~'_]
           false)
         (~'-attach-cache [~'_ ~'cache]
           (~(symbol (str name "-op")) (core/-attach-cache ~source-binding ~'cache)))
         (~'-resolve-refs [~'_ ~'expression-defs]
           (~(symbol (str name "-op")) (core/-resolve-refs ~source-binding ~'expression-defs)))
         (~'-resolve-params [~'_ ~'parameters]
           (~(symbol (str name "-op")) (core/-resolve-params ~source-binding ~'parameters)))
         (~'-eval [~'_ context# resource# scope#]
           (let [~source-binding (core/-eval ~source-binding context# resource# scope#)]
             ~@body))
         (~'-form [~'_]
           (list (quote ~name) (core/-form ~source-binding)))))

     (defmethod core/compile* ~(compile-kw name)
       [context# {source# :source}]
       (~(symbol (str name "-op")) (core/compile* context# source#)))))

(defmacro defunopp
  {:arglists '([name bindings & body])}
  [name [operand-binding precision-binding] & body]
  (let [operand (gensym "operand")
        precision (gensym "precision")]
    `(do
       (defn ~(symbol (str name "-op")) [~operand ~precision-binding ~precision]
         (reify core/Expression
           (~'-static [~'_]
             false)
           (~'-attach-cache [~'_ ~'cache]
             (~(symbol (str name "-op"))
              (core/-attach-cache ~operand ~'cache)
              ~precision-binding ~precision))
           (~'-resolve-refs [~'_ ~'expression-defs]
             (~(symbol (str name "-op"))
              (core/-resolve-refs ~operand ~'expression-defs)
              ~precision-binding ~precision))
           (~'-resolve-params [~'_ ~'parameters]
             (~(symbol (str name "-op"))
              (core/-resolve-params ~operand ~'parameters)
              ~precision-binding ~precision))
           (~'-eval [~'_ context# resource# scope#]
             (let [~operand-binding (core/-eval ~operand context# resource# scope#)]
               ~@body))
           (~'-form [~'_]
             (list (quote ~name) (core/-form ~operand) ~precision))))

       (defmethod core/compile* ~(compile-kw name)
         [context# {operand# :operand precision# :precision}]
         (~(symbol (str name "-op"))
          (core/compile* context# operand#)
          (some-> precision# core/to-chrono-unit)
          precision#)))))

(defmacro defbinopp
  {:arglists '([name attr-map? bindings & body])}
  [name & more]
  (let [attr-map (when (map? (first more)) (first more))
        more (if (map? (first more)) (next more) more)
        [[op-1-binding op-2-binding precision-binding] & body] more
        precision-required (:required (meta precision-binding))
        context (gensym "context")
        op-1 (gensym "op-1")
        op-2 (gensym "op-2")
        precision (gensym "precision")]
    `(do
       ~(when-not precision-required
          `(defn ~(symbol (str name "-op")) [~op-1 ~op-2]
             (reify core/Expression
               (~'-static [~'_]
                 false)
               (~'-attach-cache [~'_ ~'cache]
                 (~(symbol (str name "-op"))
                  (core/-attach-cache ~op-1 ~'cache)
                  (core/-attach-cache ~op-2 ~'cache)))
               (~'-resolve-refs [~'_ ~'expression-defs]
                 (~(symbol (str name "-op"))
                  (core/-resolve-refs ~op-1 ~'expression-defs)
                  (core/-resolve-refs ~op-2 ~'expression-defs)))
               (~'-resolve-params [~'_ ~'parameters]
                 (~(symbol (str name "-op"))
                  (core/-resolve-params ~op-1 ~'parameters)
                  (core/-resolve-params ~op-2 ~'parameters)))
               (~'-eval [~'_ context# resource# scope#]
                 (let [~op-1-binding (core/-eval ~op-1 context# resource# scope#)
                       ~op-2-binding (core/-eval ~op-2 context# resource# scope#)
                       ~precision-binding nil]
                   ~@body))
               (~'-form [~'_]
                 (list (quote ~name) (core/-form ~op-1) (core/-form ~op-2))))))

       (defn ~(symbol (str name "-precision-op"))
         [~op-1 ~op-2 ~precision-binding ~precision]
         (reify core/Expression
           (~'-static [~'_]
             false)
           (~'-attach-cache [~'_ ~'cache]
             (~(symbol (str name "-precision-op"))
              (core/-attach-cache ~op-1 ~'cache)
              (core/-attach-cache ~op-2 ~'cache)
              ~precision-binding ~precision))
           (~'-resolve-refs [~'_ ~'expression-defs]
             (~(symbol (str name "-precision-op"))
              (core/-resolve-refs ~op-1 ~'expression-defs)
              (core/-resolve-refs ~op-2 ~'expression-defs)
              ~precision-binding ~precision))
           (~'-resolve-params [~'_ ~'parameters]
             (~(symbol (str name "-precision-op"))
              (core/-resolve-params ~op-1 ~'parameters)
              (core/-resolve-params ~op-2 ~'parameters)
              ~precision-binding ~precision))
           (~'-eval [~'_ context# resource# scope#]
             (let [~op-1-binding (core/-eval ~op-1 context# resource# scope#)
                   ~op-2-binding (core/-eval ~op-2 context# resource# scope#)]
               ~@body))
           (~'-form [~'_]
             (list (quote ~name) (core/-form ~op-1) (core/-form ~op-2)
                   ~precision))))

       ~(if precision-required
          `(defmethod core/compile* ~(compile-kw name)
             [~context {[~op-1 ~op-2] :operand ~precision :precision}]
             (let [context# ~(if attr-map `(merge ~context ~attr-map) context)
                   ~op-1 (core/compile* context# ~op-1)
                   ~op-2 (core/compile* context# ~op-2)
                   ~precision-binding (core/to-chrono-unit ~precision)]
               (if (and (core/static? ~op-1) (core/static? ~op-2))
                 (let [~op-1-binding ~op-1
                       ~op-2-binding ~op-2]
                   ~@body)
                 (~(symbol (str name "-precision-op")) ~op-1 ~op-2
                                                       ~precision-binding ~precision))))

          `(defmethod core/compile* ~(compile-kw name)
             [~context {[~op-1 ~op-2] :operand ~precision :precision}]
             (let [context# ~(if attr-map `(merge ~context ~attr-map) context)
                   ~op-1 (core/compile* context# ~op-1)
                   ~op-2 (core/compile* context# ~op-2)
                   ~precision-binding (some-> ~precision core/to-chrono-unit)]
               (if (and (core/static? ~op-1) (core/static? ~op-2))
                 (let [~op-1-binding ~op-1
                       ~op-2-binding ~op-2]
                   ~@body)
                 (if ~precision
                   (~(symbol (str name "-precision-op")) ~op-1 ~op-2
                                                         ~precision-binding ~precision)
                   (~(symbol (str name "-op")) ~op-1 ~op-2)))))))))
