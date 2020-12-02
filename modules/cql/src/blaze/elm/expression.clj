(ns blaze.elm.expression
  (:require
    [blaze.elm.compiler.core :as core])
  (:refer-clojure :exclude [eval]))


(defn eval [expression context resource scope]
  (core/-eval expression context resource scope))
