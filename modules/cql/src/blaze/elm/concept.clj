(ns blaze.elm.concept
  "Implementation of the concept type."
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.protocols :as p]
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

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
    `(~'concept ~@(map core/-form codes)))

  Object
  (toString [_]
    (str "Concept {" (str/join ", " (map str codes)) "}")))

(defn concept? [x]
  (instance? Concept x))

(defn concept
  "Returns a CQL concept"
  [codes]
  (->Concept codes))
