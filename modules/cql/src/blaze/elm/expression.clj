(ns blaze.elm.expression
  (:require
    [blaze.elm.compiler.protocols :as p])
  (:refer-clojure :exclude [eval]))


(defn expr? [x]
  (satisfies? p/Expression x))


(defn eval [expression context resource scope]
  (p/-eval expression context resource scope))
