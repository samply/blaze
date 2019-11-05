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
  Long
  (equal [x y]
    (some->> y (p/equivalent x))))


;; 12.2. Equivalent
(extend-protocol p/Equivalent
  Long
  (equivalent [x y]
    (if (number? y)
      (== x y)
      false)))


;; 16.1. Abs
(extend-protocol p/Abs
  Long
  (abs [x]
    (Math/abs ^long x)))


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
  Long
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


;; 16.7. Log
;;
;; See decimal implementation


;; 16.8. Ln
;;
;; See decimal implementation


;; 16.11. Modulo
(extend-protocol p/Modulo
  nil
  (modulo [_ _])

  Number
  (modulo [x div]
    (try
      (rem x div)
      (catch Exception _))))


;; 16.12. Multiply
(extend-protocol p/Multiply
  Long
  (multiply [x y]
    (try
      (* x y)
      (catch Exception _))))


;; 16.13. Negate
(extend-protocol p/Negate
  Number
  (negate [x]
    (- x)))


;; 16.14. Power
(extend-protocol p/Power
  Long
  (power [x exp]
    (when-let [res (p/power (BigDecimal/valueOf x) exp)]
      (try
        (.intValueExact ^BigDecimal res)
        (catch ArithmeticException _ res)))))


;; 16.15. Predecessor
(extend-protocol p/Predecessor
  Long
  (predecessor [x]
    (dec x)))


;; 16.16. Round
;;
;; Round returns always a decimal.
(extend-protocol p/Round
  Long
  (round [x _]
    (BigDecimal/valueOf x)))


;; 16.17. Subtract
(extend-protocol p/Subtract
  Integer
  (subtract [a b]
    (some->> b (- a)))

  Long
  (subtract [a b]
    (some->> b (- a))))


;; 16.18. Successor
(extend-protocol p/Successor
  Integer
  (successor [x]
    (inc x))

  Long
  (successor [x]
    (inc x)))


;; 16.19. Truncate
(extend-protocol p/Truncate
  Integer
  (truncate [x] x)

  Long
  (truncate [x] x))


;; 16.20. TruncatedDivide
(extend-protocol p/TruncatedDivide
  Number
  (truncated-divide [num div]
    (when (and div (not (zero? div)))
      (quot num div))))


;; 22.24. ToInteger
(extend-protocol p/ToInteger
  Integer
  (to-integer [x] x)

  Long
  (to-integer [x] x)

  String
  (to-integer [s]
    (try
      (long (Integer/parseInt s))
      (catch Exception _))))


;; 22.28. ToString
(extend-protocol p/ToString
  Number
  (to-string [x]
    (str x)))
