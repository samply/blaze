(ns blaze.elm.boolean
  "Implementation of the boolean type.

  Some operations are not implemented here because they are only defined on
  decimals.

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.protocols :as p]))


;; 22.19. ToBoolean
(extend-protocol p/ToBoolean
  Boolean
  (to-boolean [x] x))


;; 22.22. ToDate
(extend-protocol p/ToDate
  Boolean
  (to-date [_ _]))


;; 22.23. ToDateTime
(extend-protocol p/ToDateTime
  Boolean
  (to-date-time [_ _]))


;; 22.24. ToDecimal
(extend-protocol p/ToDecimal
  Boolean
  (to-decimal [x]
    (if (true? x) 1.0 0.0)))


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


;; 22.28. ToQuantity
(extend-protocol p/ToQuantity
  Boolean
  (to-quantity [_]))


;; 22.30. ToString
(extend-protocol p/ToString
  Boolean
  (to-string [x]
    (str x)))


;; 22.31. ToTime
(extend-protocol p/ToTime
  Boolean
  (to-time [_ _]))
