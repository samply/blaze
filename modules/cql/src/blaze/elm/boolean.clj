(ns blaze.elm.boolean
  "Implementation of the boolean type.

  Some operations are not implemented here because they are only defined on
  decimals.

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.protocols :as p]))


;; 22.25. ToInteger
(extend-protocol p/ToInteger
  Boolean
  (to-integer [x]
    (if (true? x) 1 0)))


;; 22.27. ToLong
(extend-protocol p/ToLong
  Boolean
  (to-long [x]
    (if (true? x) 1 0)))
