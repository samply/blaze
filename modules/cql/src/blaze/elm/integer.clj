(ns blaze.elm.integer
  "Implementation of the integer type.

  Some operations are not implemented here because they are only defined on
  decimals.

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.protocols :as p]))


(set! *warn-on-reflection* true)


;; 12.1. Equal
(extend-protocol p/Equal
  Number
  (equal [x y]
    (some->> y (p/equivalent x))))


;; 12.2. Equivalent
(extend-protocol p/Equivalent
  Number
  (equivalent [x y]
    (if (number? y)
      (== x y)
      false)))


;; 16.1. Abs
(extend-protocol p/Abs
  Integer
  (abs [x]
    (abs (.longValue x)))
  Long
  (abs [x]
    (abs (.longValue x))))


;; 16.2. Add
(extend-protocol p/Add
  Number
  (add [x y]
    (try
      (+ x y)
      (catch Exception _))))


;; 16.3. Ceiling
(extend-protocol p/Ceiling
  Long
  (ceiling [x]
    x))


;; 16.4. Divide
(extend-protocol p/Divide
  Number
  (divide [x y]
    (p/divide (p/to-decimal x) y)))


;; 16.5. Exp
;;
;; See decimal implementation


;; 16.6. Floor
(extend-protocol p/Floor
  Long
  (floor [x]
    x))


;; 16.8. Log
;;
;; See decimal implementation


;; 16.10. Ln
;;
;; See decimal implementation


;; 16.13. Modulo
(extend-protocol p/Modulo
  Number
  (modulo [x div]
    (try
      (rem x div)
      (catch Exception _))))


;; 16.14. Multiply
(extend-protocol p/Multiply
  Number
  (multiply [x y]
    (try
      (* x y)
      (catch Exception _))))


;; 16.15. Negate
(extend-protocol p/Negate
  Number
  (negate [x]
    (- x)))


;; 16.16. Power
(extend-protocol p/Power
  Long
  (power [x exp]
    (when-let [res (p/power (BigDecimal/valueOf x) exp)]
      (try
        (.intValueExact ^BigDecimal res)
        (catch ArithmeticException _ res)))))


;; 16.18. Predecessor
(extend-protocol p/Predecessor
  Number
  (predecessor [x]
    (dec x)))


;; 16.19. Round
;;
;; Round returns always a decimal.
(extend-protocol p/Round
  Long
  (round [x _]
    (BigDecimal/valueOf x)))


;; 16.20. Subtract
(extend-protocol p/Subtract
  Number
  (subtract [a b]
    (some->> b (- a))))


;; 16.21. Successor
(extend-protocol p/Successor
  Number
  (successor [x]
    (inc x)))


;; 16.22. Truncate
(extend-protocol p/Truncate
  Integer
  (truncate [x] x)

  Long
  (truncate [x] x))


;; 16.23. TruncatedDivide
(extend-protocol p/TruncatedDivide
  Number
  (truncated-divide [num div]
    (when (and div (not (zero? div)))
      (quot num div))))


;; 22.19. ToBoolean
(extend-protocol p/ToBoolean
  Integer
  (to-boolean [x]
    (p/to-boolean (long x)))

  Long
  (to-boolean [x]
    (condp = x
      1 true
      0 false
      nil)))


;; 22.24. ToDecimal
(extend-protocol p/ToDecimal
  Integer
  (to-decimal [x]
    (BigDecimal/valueOf (long x)))

  Long
  (to-decimal [x]
    (BigDecimal/valueOf x)))


;; 22.25. ToInteger
(extend-protocol p/ToInteger
  Integer
  (to-integer [x]
    (.longValue x))

  Long
  (to-integer [x] x)

  String
  (to-integer [s]
    (try
      (.longValue (Integer/valueOf s))
      (catch NumberFormatException _))))


;; 22.27. ToLong
(extend-protocol p/ToLong
  Integer
  (to-long [x]
    (.longValue x))

  Long
  (to-long [x] x)

  String
  (to-long [s]
    (try
      (Long/valueOf s)
      (catch NumberFormatException _))))


;; 22.30. ToString
(extend-protocol p/ToString
  Number
  (to-string [x]
    (str x)))
