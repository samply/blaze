(ns blaze.elm.expression
  (:refer-clojure :exclude [eval])
  (:require
    [blaze.elm.compiler.core :as core]))


(defn eval [expression context resource scope]
  (core/-eval expression context resource scope))
