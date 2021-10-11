(ns blaze.elm.expression
  (:refer-clojure :exclude [eval])
  (:require
    [blaze.elm.compiler.core :as core]))


(defn eval
  "Evaluates `expression` on `resource` using `context`."
  [context expression resource]
  (core/-eval expression context resource nil))
