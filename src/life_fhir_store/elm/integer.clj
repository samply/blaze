(ns life-fhir-store.elm.integer
  "Implementation of the integer type.

  Some operations are not implemented here because they are only defined on
  decimals.

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [life-fhir-store.elm.protocols :as p]))


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
    (try
      (/ x y)
      (catch Exception _))))


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
  Number
  (subtract [a b]
    (some->> b (- a))))


;; 16.18. Successor
(extend-protocol p/Successor
  Long
  (successor [x]
    (inc x)))


;; 16.19. Truncate
(extend-protocol p/Truncate
  Long
  (truncate [x] x))


;; 16.20. TruncatedDivide
(extend-protocol p/TruncatedDivide
  Number
  (truncated-divide [num div]
    (when (and div (not (zero? div)))
      (quot num div))))
