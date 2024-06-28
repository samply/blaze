(ns blaze.elm.ratio
  "Implementation of the ratio type.
  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.protocols :as p]
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

(defrecord Ratio [numerator denominator]
  p/Equal
  (equal [_ other]
    (and (p/equal numerator (:numerator other))
         (p/equal denominator (:denominator other))))

  p/Equivalent
  (equivalent [_ other]
    (if other
      (p/equivalent (p/divide numerator denominator)
                    (p/divide (:numerator other) (:denominator other)))
      false))

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
  (-eval [this _ _ _]
    this)
  (-form [_]
    (list 'ratio (core/-form numerator) (core/-form denominator))))

(defn ratio
  "Creates a ratio between two quantities."
  [numerator denominator]
  (->Ratio numerator denominator))

;; 22.28. ToQuantity
(extend-protocol p/ToQuantity
  Ratio
  (to-quantity [x]
    (p/divide (:denominator x) (:numerator x))))

;; 22.29. ToRatio
(extend-protocol p/ToRatio
  String
  (to-ratio [s]
    (let [[numerator denominator] (str/split s #":" 2)]
      (when-let [numerator (p/to-quantity numerator)]
        (when-let [denominator (p/to-quantity denominator)]
          (ratio numerator denominator))))))

;; 22.30. ToString
(extend-protocol p/ToString
  Ratio
  (to-string [x]
    (str (p/to-string (:numerator x)) ":" (p/to-string (:denominator x)))))
