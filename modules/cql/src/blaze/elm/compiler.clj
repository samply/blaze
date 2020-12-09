(ns blaze.elm.compiler
  "Compiles ELM expressions to expressions defined by the `Expression`
  protocol in this namespace.

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html.

  Regarding time zones:
    We use date and time values with and without time zone information here.
    Every local (without time zone) date or time is meant relative to the time
    zone of the :now timestamp in the evaluation context."
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.db.api-spec]
    [blaze.elm.compiler.aggregate-operators]
    [blaze.elm.compiler.clinical-operators]
    [blaze.elm.compiler.clinical-values]
    [blaze.elm.compiler.comparison-operators]
    [blaze.elm.compiler.conditional-operators]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.date-time-operators]
    [blaze.elm.compiler.external-data]
    [blaze.elm.compiler.interval-operators]
    [blaze.elm.compiler.list-operators]
    [blaze.elm.compiler.logical-operators]
    [blaze.elm.compiler.nullological-operators]
    [blaze.elm.compiler.reusing-logic]
    [blaze.elm.compiler.simple-values]
    [blaze.elm.compiler.string-operators]
    [blaze.elm.compiler.type-operators]
    [blaze.elm.deps-infer :as deps-infer]
    [blaze.elm.equiv-relationships :as equiv-relationships]
    [blaze.elm.integer]
    [blaze.elm.list]
    [blaze.elm.nil]
    [blaze.elm.normalizer :as normalizer]
    [blaze.elm.spec]
    [blaze.elm.tuple]
    [blaze.elm.type-infer :as type-infer]
    [cognitect.anomalies :as anom])
  (:refer-clojure :exclude [comparator compile]))


(set! *warn-on-reflection* true)


(defn compile
  "Compiles `expression` in `context`.

  Use `compile-library` to compile a whole library."
  [context expression]
  (core/compile* context expression))


(defn- compile-expression-def
  "Compiles the expression of `expression-def` in `context` and assocs the
  resulting compiled expression under :life/expression to the `expression-def`
  which itself is returned.

  Returns an anomaly on errors."
  {:arglists '([context expression-def])}
  [context {:keys [expression] :as expression-def}]
  (let [context (assoc context :eval-context (:context expression-def))]
    (try
      (assoc expression-def
        :blaze.elm.compiler/expression (compile context expression))
      (catch Exception e
        (let [ex-data (ex-data e)]
          (if (::anom/category ex-data)
            (assoc ex-data
              :context context
              :elm/expression expression)
            {::anom/category ::anom/fault
             ::anom/message (ex-message e)
             :e e
             :context context
             :elm/expression expression}))))))


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
    (when-ok [expr-defs
              (transduce
                (map #(compile-expression-def context %))
                (completing
                  (fn [r {:keys [name] :blaze.elm.compiler/keys [expression]
                          :as compiled-expression-def}]
                    (if (::anom/category compiled-expression-def)
                      (reduced compiled-expression-def)
                      (assoc r name expression))))
                {}
                (-> library :statements :def))]
      {:life/compiled-expression-defs expr-defs})))
