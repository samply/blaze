(ns blaze.elm.concept
  "Implementation of the concept type."
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.protocols :as p]))

(defrecord Concept [codes]
  p/Equivalent
  (equivalent [_ other]
    (reduce
     (fn [_ code]
       (if (some (partial p/equivalent code) (:codes other))
         (reduced true)
         false))
     false
     codes))

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
    `(~'concept ~@(map core/-form codes))))

(defn concept
  "Returns a CQL concept"
  [codes]
  (->Concept codes))
