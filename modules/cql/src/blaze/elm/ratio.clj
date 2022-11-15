(ns blaze.elm.ratio
  "Implementation of the ratio type.
  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.protocols :as p]))


(set! *warn-on-reflection* true)


(defrecord Ratio [numerator denominator]
  p/Equal
  (equal [_ other]
    (and (p/equal numerator (:numerator other))
         (p/equal denominator (:denominator other))))

  p/Equivalent
  (equivalent [_ other]
    (if other
      (p/equal (p/divide numerator denominator)
               (p/divide (:numerator other) (:denominator other)))
      false)))


(defn ratio
  "Creates a ratio between two quantities."
  [numerator denominator]
  (->Ratio numerator denominator))


;; 22.28. ToQuantity
(extend-protocol p/ToQuantity
  Ratio
  (to-quantity [x]
     (p/divide (:denominator x) (:numerator x))))


;; 22.30. ToString
(extend-protocol p/ToString
  Ratio
  (to-string [x]
    (str (p/to-string (:numerator x)) ":" (p/to-string (:denominator x)))))
