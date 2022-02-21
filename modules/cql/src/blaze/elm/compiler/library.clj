(ns blaze.elm.compiler.library
  (:require
    [blaze.anomaly :as ba :refer [when-ok]]
    [blaze.elm.compiler :as compiler]
    [blaze.elm.deps-infer :as deps-infer]
    [blaze.elm.equiv-relationships :as equiv-relationships]
    [blaze.elm.normalizer :as normalizer]))


(defn- compile-expression-def
  "Compiles the expression of `expression-def` in `context` and associates the
  resulting compiled expression under ::compiler/expression to the
  `expression-def` which itself is returned.

  Returns an anomaly on errors."
  {:arglists '([context expression-def])}
  [context {:keys [expression] :as expression-def}]
  (let [context (assoc context :eval-context (:context expression-def))]
    (-> (ba/try-anomaly
          (assoc expression-def
            ::compiler/expression (compiler/compile context expression)))
        (ba/exceptionally
          #(assoc % :context context :elm/expression expression)))))


(defn- expr-defs [context library]
  (transduce
    (comp (map (partial compile-expression-def context))
          (halt-when ba/anomaly?))
    (completing
      (fn [r {:keys [name] ::compiler/keys [expression]}]
        (assoc r name expression)))
    {}
    (-> library :statements :def)))


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
    (when-ok [expr-defs (expr-defs context library)
              parameter-default-values (parameter-default-values context library)]
      {:compiled-expression-defs expr-defs
       :parameter-default-values parameter-default-values})))
