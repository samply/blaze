(ns blaze.elm.value-set
  (:require
   [blaze.elm.compiler.core :as core]))

;; A code without it's version and display so that it's Clojure equal operation
;; will be the CQL equivalence operation.
(defrecord Code [system code]
  core/Expression
  (-static [_]
    true)
  (-attach-cache [expr _]
    [(fn [] [expr])])
  (-patient-count [_]
    nil)
  (-resolve-refs [expr _]
    expr)
  (-resolve-params [expr _]
    expr)
  (-optimize [expr _]
    expr)
  (-eval [this _ _ _]
    this)
  (-form [_]
    `(~'code ~system ~code)))

(defn from-code [{:keys [system code]}]
  (->Code system code))
