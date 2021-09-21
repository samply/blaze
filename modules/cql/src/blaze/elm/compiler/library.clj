(ns blaze.elm.compiler.library
  (:require
    [blaze.anomaly :as ba :refer [when-ok]]
    [blaze.anomaly-spec]
    [blaze.elm.compiler :as compiler]
    [blaze.elm.deps-infer :as deps-infer]
    [blaze.elm.equiv-relationships :as equiv-relationships]
    [blaze.elm.normalizer :as normalizer]
    [blaze.elm.type-infer :as type-infer]
    [cognitect.anomalies :as anom]))


(defn- compile-expression-def
  "Compiles the expression of `expression-def` in `context` and assocs the
  resulting compiled expression under :life/expression to the `expression-def`
  which itself is returned.

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
    (map #(compile-expression-def context %))
    (completing
      (fn [r {:keys [name] :blaze.elm.compiler/keys [expression]
              :as compiled-expression-def}]
        (if (::anom/category compiled-expression-def)
          (reduced compiled-expression-def)
          (assoc r name expression))))
    {}
    (-> library :statements :def)))


(defn compile-library
  "Compiles `library` using `node`.

  There are currently no options."
  [node library opts]
  (let [library (-> library
                    normalizer/normalize-library
                    equiv-relationships/find-equiv-rels-library
                    deps-infer/infer-library-deps
                    type-infer/infer-library-types)
        context (assoc opts :node node :library library)]
    (when-ok [expr-defs (expr-defs context library)]
      {:life/compiled-expression-defs expr-defs})))
