(ns blaze.elm.concept
  "Implementation of the concept type."
  (:require
   [blaze.elm.protocols :as p]))

(defrecord Concept [codes]
  p/Equivalent
  ; todo
  )
(defn to-concept
  "Returns a CQL concept"
  [codes]
  (->Concept codes))
