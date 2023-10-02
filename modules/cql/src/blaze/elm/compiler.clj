(ns blaze.elm.compiler
  "Compiles ELM expressions to expressions defined by the `Expression`
  protocol in this namespace.

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html.

  Regarding time zones:
    We use date and time values with and without time zone information here.
    Every local (without time zone) date or time is meant relative to the time
    zone of the :now timestamp in the evaluation context."
  (:refer-clojure :exclude [compile])
  (:require
   [blaze.elm.boolean]
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
   [blaze.elm.compiler.parameters]
   [blaze.elm.compiler.reusing-logic]
   [blaze.elm.compiler.simple-values]
   [blaze.elm.compiler.string-operators]
   [blaze.elm.compiler.type-operators]
   [blaze.elm.integer]
   [blaze.elm.list]
   [blaze.elm.nil]
   [blaze.elm.spec]
   [blaze.elm.tuple]))

(defn compile
  "Compiles `expression` in `context`.

  Use `compile-library` to compile a whole library."
  [context expression]
  (core/compile* context expression))

(defn attach-cache
  "Attaches expression `cache` to `expression` returning an expression that
  uses `cache` in order to improve evaluation performance.

  Otherwise the semantics of the returned expression have to be the same as that
  of `expression`."
  [expression cache]
  (core/-attach-cache expression cache))

(defn resolve-refs
  "Resolves expressions defined in `expression-defs` in `expression`."
  [expression expression-defs]
  (core/-resolve-refs expression expression-defs))

(defn resolve-params
  "Resolves `parameters` in `expression`."
  [expression parameters]
  (core/-resolve-params expression parameters))

(defn form [expression]
  (core/-form expression))
