(ns blaze.elm.expression
  (:refer-clojure :exclude [eval hash])
  (:require
    [blaze.elm.compiler.core :as core]))


(defn eval
  "Evaluates `expression` on `resource` using `context`.

  Throws an Exception on errors."
  [context expression resource]
  (core/-eval expression context resource nil))


(defn hash
  "Hashes `expression`."
  [expression]
  (clojure.core/hash (core/-form expression)))
