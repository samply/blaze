(ns blaze.elm.compiler.macros
  (:require
   [blaze.anomaly :as ba]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.expression.cache :as ec]
   [blaze.elm.expression.cache.bloom-filter :as bloom-filter]
   [blaze.elm.expression.cache.spec]
   [clojure.spec.alpha :as s]
   [prometheus.alpha :as prom]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defn- find-form [form body]
  (some #(when (= form (first %)) %) body))

(def ^:private ^:const unknown nil)

(defmacro reify-expr [_ & body]
  `(reify
     core/Expression
     (~'-static [~'_]
       false)
     ~(if-let [form (find-form '-attach-cache body)]
        form
        (list '-attach-cache ['expr '_] `[(fn [] [~'expr])]))
     ~(if-let [form (find-form '-patient-count body)]
        form
        (list '-patient-count ['_] unknown))
     ~(if-let [form (find-form '-resolve-refs body)]
        form
        (list '-resolve-refs ['expr '_] 'expr))
     ~(if-let [form (find-form '-resolve-params body)]
        form
        (list '-resolve-params ['expr '_] 'expr))
     ~(if-let [form (find-form '-eval body)]
        form
        (list '-eval ['expr '_ '_ '_] 'expr))
     ~(if-let [form (find-form '-form body)]
        form
        (list '-form ['_] 'nil))

     Object
     (~'equals [~'this ~'other]
       (.equals ^Object (core/-form ~'this) (core/-form ~'other)))
     (~'hashCode [~'this]
       (.hashCode ^Object (core/-form ~'this)))))

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
        operand-key (or (:operand-key attr-map) :operand)
        attr-map (dissoc attr-map :operand-key)
        op (symbol (str name "-op"))
        cache-op (symbol (str name "-cache-op"))
        caching-op (symbol (str name "-caching-op"))
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
             (declare ~caching-op)

             (s/fdef ~cache-op
               :args ~(if elm-expr-binding
                        `(s/cat :operand core/expr?
                                :bloom-filter ::ec/bloom-filter
                                :expr :elm/expression)
                        `(s/cat :operand core/expr?
                                :bloom-filter ::ec/bloom-filter))
               :ret core/expr?)

             (defn ~cache-op
               ~(str "Creates a " name " operator with attached Bloom filter that will be used to increase performance of evaluation.")
               ~(cond-> [operand bloom-filter] elm-expr-binding (conj elm-expr))
               (log/trace (format "Create expression `%s` with attached Bloom filter." (list (quote ~name) (core/-form ~operand))))
               (reify-expr core/Expression
                 (~'-attach-cache [~expr ~'cache]
                   (if-let [~bloom-filter (ec/get ~'cache ~expr)]
                     ~(if elm-expr-binding
                        `[(fn []
                            [(~cache-op ~operand ~bloom-filter ~elm-expr)
                             [~bloom-filter]
                             ~bloom-filter])]
                        `[(fn []
                            [(~cache-op ~operand ~bloom-filter)
                             [~bloom-filter]
                             ~bloom-filter])])
                     ~(if elm-expr-binding
                        `[(fn []
                            [(~caching-op ~operand ~elm-expr)
                             [(ba/unavailable "No Bloom filter available.")]])]
                        `[(fn []
                            [(~caching-op ~operand)
                             [(ba/unavailable "No Bloom filter available.")]])])))
                 (~'-patient-count [~'_]
                   (::bloom-filter/patient-count ~bloom-filter))
                 (~'-resolve-refs [~'_ ~'expression-defs]
                   ~(if elm-expr-binding
                      `(~caching-op
                        (core/-resolve-refs ~operand ~'expression-defs)
                        ~elm-expr)
                      `(~caching-op
                        (core/-resolve-refs ~operand ~'expression-defs))))
                 (~'-resolve-params [~'_ ~'parameters]
                   ~(if elm-expr-binding
                      `(~caching-op
                        (core/-resolve-params ~operand ~'parameters)
                        ~elm-expr)
                      `(~caching-op
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
                       (prom/inc! ec/bloom-filter-not-useful-total ~(clojure.core/name name))
                       (when-not res#
                         (prom/inc! ec/bloom-filter-false-positive-total ~(clojure.core/name name)))
                       res#)
                     (do (prom/inc! ec/bloom-filter-useful-total ~(clojure.core/name name))
                         false)))
                 (~'-form [~'_]
                   (list (quote ~name) (core/-form ~operand)))))

             (s/fdef ~caching-op
               :args ~(if elm-expr-binding
                        `(s/cat :operand core/expr? :expr :elm/expression)
                        `(s/cat :operand core/expr?))
               :ret core/expr?)

             (defn ~caching-op
               ~(str "Creates a " name " operator that will handle cache attachment.")
               ~(cond-> [operand] elm-expr-binding (conj elm-expr))
               (reify-expr core/Expression
                 (~'-attach-cache [~expr ~'cache]
                   (if-let [~bloom-filter (ec/get ~'cache ~expr)]
                     ;;TODO: add metric of how many times a bloom filter was available
                     ~(if elm-expr-binding
                        `[(fn []
                            [(~cache-op ~operand ~bloom-filter ~elm-expr)
                             [~bloom-filter]
                             ~bloom-filter])]
                        `[(fn []
                            [(~cache-op ~operand ~bloom-filter)
                             [~bloom-filter]
                             ~bloom-filter])])
                     ~(if elm-expr-binding
                        `[(fn []
                            [(~caching-op ~operand ~elm-expr)
                             [(ba/unavailable "No Bloom filter available.")]])]
                        `[(fn []
                            [(~caching-op ~operand)
                             [(ba/unavailable "No Bloom filter available.")]])])))
                 (~'-resolve-refs [~'_ ~'expression-defs]
                   ~(if elm-expr-binding
                      `(~caching-op
                        (core/-resolve-refs ~operand ~'expression-defs)
                        ~elm-expr)
                      `(~caching-op
                        (core/-resolve-refs ~operand ~'expression-defs))))
                 (~'-resolve-params [~'_ ~'parameters]
                   ~(if elm-expr-binding
                      `(~caching-op
                        (core/-resolve-params ~operand ~'parameters)
                        ~elm-expr)
                      `(~caching-op
                        (core/-resolve-params ~operand ~'parameters))))
                 (~'-eval [~'_ ~context ~resource ~scope]
                   (let ~(generate-binding-vector
                          operand-binding `(core/-eval ~operand ~context ~resource ~scope)
                          elm-expr-binding elm-expr)
                     ~@body))
                 (~'-form [~'_]
                   (list (quote ~name) (core/-form ~operand)))))))

       (defn ~op
         ~(str "Creates a " name " operator that will only delegate cache attachment.")
         ~(cond-> [operand] elm-expr-binding (conj elm-expr))
         (reify-expr core/Expression
           (~'-attach-cache [~'_ ~'cache]
             ~(if elm-expr-binding
                `(core/attach-cache-helper-1 ~op ~'cache ~operand ~elm-expr)
                `(core/attach-cache-helper ~op ~'cache ~operand)))
           (~'-resolve-refs [~'_ ~'expression-defs]
             ~(if elm-expr-binding
                `(~op
                  (core/-resolve-refs ~operand ~'expression-defs)
                  ~elm-expr)
                `(~op
                  (core/-resolve-refs ~operand ~'expression-defs))))
           (~'-resolve-params [~'_ ~'parameters]
             ~(if elm-expr-binding
                `(~op
                  (core/-resolve-params ~operand ~'parameters)
                  ~elm-expr)
                `(~op
                  (core/-resolve-params ~operand ~'parameters))))
           (~'-eval [~'_ ~context ~resource ~scope]
             (let ~(generate-binding-vector
                    operand-binding `(core/-eval ~operand ~context ~resource ~scope)
                    elm-expr-binding elm-expr)
               ~@body))
           (~'-form [~'_]
             (list (quote ~name) (core/-form ~operand)))))

       (defmethod core/compile* ~(compile-kw name)
         [{~eval-context :eval-context :as ~context}
          {~operand ~operand-key :as ~elm-expr}]
         (let [~operand (core/compile* (merge ~context ~(dissoc attr-map :cache)) ~operand)]
           (if (core/static? ~operand)
             (let ~(generate-binding-vector
                    operand-binding operand
                    elm-expr-binding elm-expr)
               ~@body)
             ~(if (:cache attr-map)
                `(if (= "Patient" ~eval-context)
                   ~(if elm-expr-binding
                      `(~caching-op ~operand ~elm-expr)
                      `(~caching-op ~operand))
                   ~(if elm-expr-binding
                      `(~op ~operand ~elm-expr)
                      `(~op ~operand)))
                (if elm-expr-binding
                  `(~op ~operand ~elm-expr)
                  `(~op ~operand)))))))))

(defmacro defbinop
  {:arglists '([name attr-map? bindings & body])}
  [name & more]
  (let [attr-map (when (map? (first more)) (first more))
        more (if (map? (first more)) (next more) more)
        [[op-1-binding op-2-binding] & body] more
        context (gensym "context")
        op (symbol (str name "-op"))
        op-1 (gensym "op-1")
        op-2 (gensym "op-2")]
    `(do
       (defn ~op [~op-1 ~op-2]
         (reify-expr core/Expression
           (~'-attach-cache [~'_ ~'cache]
             (core/attach-cache-helper ~op ~'cache ~op-1 ~op-2))
           (~'-resolve-refs [~'_ ~'expression-defs]
             (~op
              (core/-resolve-refs ~op-1 ~'expression-defs)
              (core/-resolve-refs ~op-2 ~'expression-defs)))
           (~'-resolve-params [~'_ ~'parameters]
             (core/resolve-params-helper ~op ~'parameters ~op-1 ~op-2))
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
             (~op ~op-1 ~op-2)))))))

(defmacro defternop
  {:arglists '([name bindings & body])}
  [name [op-1-binding op-2-binding op-3-binding] & body]
  (let [op (symbol (str name "-op"))
        op-1 (gensym "op-1")
        op-2 (gensym "op-2")
        op-3 (gensym "op-3")]
    `(do
       (defn ~op [~op-1 ~op-2 ~op-3]
         (reify-expr core/Expression
           (~'-attach-cache [~'_ ~'cache]
             (core/attach-cache-helper ~op ~'cache ~op-1 ~op-2 ~op-3))
           (~'-resolve-refs [~'_ ~'expression-defs]
             (~op
              (core/-resolve-refs ~op-1 ~'expression-defs)
              (core/-resolve-refs ~op-2 ~'expression-defs)
              (core/-resolve-refs ~op-3 ~'expression-defs)))
           (~'-resolve-params [~'_ ~'parameters]
             (core/resolve-params-helper ~op ~'parameters ~op-1 ~op-2 ~op-3))
           (~'-eval [~'_ context# resource# scope#]
             (let [~op-1-binding (core/-eval ~op-1 context# resource# scope#)
                   ~op-2-binding (core/-eval ~op-2 context# resource# scope#)
                   ~op-3-binding (core/-eval ~op-3 context# resource# scope#)]
               ~@body))
           (~'-form [~'_]
             (list (quote ~name) (core/-form ~op-1) (core/-form ~op-2)
                   (core/-form ~op-3)))))
       (defmethod core/compile* ~(compile-kw name)
         [context# {[~op-1 ~op-2 ~op-3] :operand}]
         (let [~op-1 (core/compile* context# ~op-1)
               ~op-2 (core/compile* context# ~op-2)
               ~op-3 (core/compile* context# ~op-3)]
           (if (and (core/static? ~op-1) (core/static? ~op-2) (core/static? ~op-3))
             (let [~op-1-binding ~op-1
                   ~op-2-binding ~op-2
                   ~op-3-binding ~op-3]
               ~@body)
             (~op ~op-1 ~op-2 ~op-3)))))))

(defmacro defnaryop
  {:arglists '([name bindings & body])}
  [name [operands-binding] & body]
  (let [op (symbol (str name "-op"))]
    `(do
       (defn ~op [~operands-binding]
         (reify-expr core/Expression
           (~'-attach-cache [~'_ ~'cache]
             (core/attach-cache-helper-list ~op ~'cache ~operands-binding))
           (~'-resolve-refs [~'_ ~'expression-defs]
             (~op
              (mapv #(core/-resolve-refs % ~'expression-defs) ~operands-binding)))
           (~'-resolve-params [~'_ ~'parameters]
             (~op
              (mapv #(core/-resolve-params % ~'parameters) ~operands-binding)))
           (~'-eval [~'_ context# resource# scope#]
             (let [~operands-binding (mapv #(core/-eval % context# resource# scope#) ~operands-binding)]
               ~@body))
           (~'-form [~'_]
             (cons (quote ~name) (map core/-form ~operands-binding)))))

       (defmethod core/compile* ~(compile-kw name)
         [context# {operands# :operand}]
         (~op (mapv #(core/compile* context# %) operands#))))))

(defmacro defaggop
  {:arglists '([name bindings & body])}
  [name [source-binding] & body]
  (let [op (symbol (str name "-op"))]
    `(do
       (defn ~op [~source-binding]
         (reify-expr core/Expression
           (~'-attach-cache [~'_ ~'cache]
             (core/attach-cache-helper ~op ~'cache ~source-binding))
           (~'-resolve-refs [~'_ ~'expression-defs]
             (~op (core/-resolve-refs ~source-binding ~'expression-defs)))
           (~'-resolve-params [~'_ ~'parameters]
             (~op (core/-resolve-params ~source-binding ~'parameters)))
           (~'-eval [~'_ context# resource# scope#]
             (let [~source-binding (core/-eval ~source-binding context# resource# scope#)]
               ~@body))
           (~'-form [~'_]
             (list (quote ~name) (core/-form ~source-binding)))))

       (defmethod core/compile* ~(compile-kw name)
         [context# {source# :source}]
         (~op (core/compile* context# source#))))))

(defmacro defunopp
  {:arglists '([name bindings & body])}
  [name [operand-binding precision-binding] & body]
  (let [op (symbol (str name "-op"))
        operand (gensym "operand")
        precision (gensym "precision")]
    `(do
       (defn ~op [~operand ~precision-binding ~precision]
         (reify-expr core/Expression
           (~'-attach-cache [~'_ ~'cache]
             (core/attach-cache-helper-2 ~op ~'cache ~operand ~precision-binding
                                         ~precision))
           (~'-resolve-refs [~'_ ~'expression-defs]
             (~op
              (core/-resolve-refs ~operand ~'expression-defs)
              ~precision-binding ~precision))
           (~'-resolve-params [~'_ ~'parameters]
             (~op
              (core/-resolve-params ~operand ~'parameters)
              ~precision-binding ~precision))
           (~'-eval [~'_ context# resource# scope#]
             (let [~operand-binding (core/-eval ~operand context# resource# scope#)]
               ~@body))
           (~'-form [~'_]
             (list (quote ~name) (core/-form ~operand) ~precision))))

       (defmethod core/compile* ~(compile-kw name)
         [context# {operand# :operand precision# :precision}]
         (~op
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
        op (symbol (str name "-op"))
        precision-op (symbol (str name "-precision-op"))
        op-1 (gensym "op-1")
        op-2 (gensym "op-2")
        precision (gensym "precision")]
    `(do
       ~(when-not precision-required
          `(defn ~op [~op-1 ~op-2]
             (reify-expr core/Expression
               (~'-attach-cache [~'_ ~'cache]
                 (core/attach-cache-helper ~op ~'cache ~op-1 ~op-2))
               (~'-resolve-refs [~'_ ~'expression-defs]
                 (~op
                  (core/-resolve-refs ~op-1 ~'expression-defs)
                  (core/-resolve-refs ~op-2 ~'expression-defs)))
               (~'-resolve-params [~'_ ~'parameters]
                 (core/resolve-params-helper ~op ~'parameters ~op-1 ~op-2))
               (~'-eval [~'_ context# resource# scope#]
                 (let [~op-1-binding (core/-eval ~op-1 context# resource# scope#)
                       ~op-2-binding (core/-eval ~op-2 context# resource# scope#)
                       ~precision-binding nil]
                   ~@body))
               (~'-form [~'_]
                 (list (quote ~name) (core/-form ~op-1) (core/-form ~op-2))))))

       (defn ~precision-op
         [~op-1 ~op-2 ~precision-binding ~precision]
         (reify-expr core/Expression
           (~'-attach-cache [~'_ ~'cache]
             (core/attach-cache-helper-2 ~precision-op ~'cache ~op-1 ~op-2
                                         ~precision-binding ~precision))
           (~'-resolve-refs [~'_ ~'expression-defs]
             (~precision-op
              (core/-resolve-refs ~op-1 ~'expression-defs)
              (core/-resolve-refs ~op-2 ~'expression-defs)
              ~precision-binding ~precision))
           (~'-resolve-params [~'_ ~'parameters]
             (~precision-op
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
                 (~precision-op ~op-1 ~op-2 ~precision-binding ~precision))))

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
                   (~precision-op ~op-1 ~op-2 ~precision-binding ~precision)
                   (~op ~op-1 ~op-2)))))))))
