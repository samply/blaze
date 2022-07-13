(ns blaze.elm.compiler.library
  (:require
    [blaze.anomaly :as ba :refer [if-ok when-ok]]
    [blaze.elm.compiler :as compiler]
    [blaze.elm.compiler.function :as function]
    [blaze.elm.deps-infer :as deps-infer]
    [blaze.elm.equiv-relationships :as equiv-relationships]
    [blaze.elm.normalizer :as normalizer]))


(defn- compile-expression-def
  "Compiles the expression of `def` in `context` and returns a tuple of
  `[name compiled-expression]` or an anomaly on errors."
  [context {:keys [name expression] :as def}]
  (let [context (assoc context :eval-context (:context def))]
    (-> (ba/try-anomaly [name (compiler/compile context expression)])
        (ba/exceptionally
          #(assoc % :context context :elm/expression expression)))))


(defn- compile-function-def
  "Compiles the function of `def` in `context`.

  Returns the compiled function or an anomaly on errors."
  [context {:keys [name operand] :as def}]
  (when-ok [[_ expression] (compile-expression-def context def)]
    (partial function/arity-n name expression (mapv :name operand))))


(defn- compile-function-defs [context library]
  (transduce
    (filter (comp #{"FunctionDef"} :type))
    (completing
      (fn [context {:keys [name] :as def}]
        (if-ok [function (compile-function-def context def)]
          (assoc-in context [:functions name] function)
          reduced)))
    context
    (-> library :statements :def)))


(defn- expression-defs [context library]
  (when-ok [context (compile-function-defs context library)]
    (transduce
      (comp (filter (comp nil? :type))
            (map (partial compile-expression-def context))
            (halt-when ba/anomaly?))
      (completing
        (fn [r [name expression]]
          (assoc r name expression)))
      {}
      (-> library :statements :def))))


(defn- compile-parameter-def
  "Compiles the default value of `parameter-def` in `context` and associates the
  resulting compiled default value under :default to the `parameter-def` which
  itself is returned.

  Returns an anomaly on errors."
  {:arglists '([context expression-def])}
  [context {:keys [default] :as parameter-def}]
  (if (some? default)
    (-> (ba/try-anomaly
          (assoc parameter-def :default (compiler/compile context default)))
        (ba/exceptionally
          #(assoc % :context context :elm/expression default)))
    parameter-def))


(defn- parameter-default-values [context library]
  (transduce
    (comp (map (partial compile-parameter-def context))
          (halt-when ba/anomaly?))
    (completing
      (fn [r {:keys [name default]}]
        (assoc r name default)))
    {}
    (-> library :parameters :def)))


(defn compile-library
  "Compiles `library` using `node`.

  There are currently no options."
  [node library opts]
  (let [library (-> library
                    normalizer/normalize-library
                    equiv-relationships/find-equiv-rels-library
                    deps-infer/infer-library-deps)
        context (assoc opts :node node :library library)]
    (when-ok [expression-defs (expression-defs context library)
              parameter-default-values (parameter-default-values context library)]
      {:compiled-expression-defs expression-defs
       :parameter-default-values parameter-default-values})))
