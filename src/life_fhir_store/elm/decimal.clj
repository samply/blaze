(ns life-fhir-store.elm.decimal
  "Implementation of the decimal type.

  Operations not implemented here are often implemented in the integer namespace
  because it uses Clojure functions also implementing the functionality for
  decimal.

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [life-fhir-store.elm.protocols :as p])
  (:import
    [java.math RoundingMode])
  (:refer-clojure :exclude [min max]))


(set! *warn-on-reflection* true)


(def ^:const min
  "Minimum decimal (-10^28 + 1) / 10^8"
  (/ (+ -1E28M 1) 1E8M))


(def ^:const max
  "Maximum decimal (10^28 - 1) / 10^8"
  (/ (- 1E28M 1) 1E8M))


;; 12.1. Equal
(extend-protocol p/Equal
  BigDecimal
  (equal [x y]
    (some->> y (p/equivalent x))))


;; 12.2. Equivalent
(extend-protocol p/Equivalent
  BigDecimal
  (equivalent [x y]
    (if (number? y)
      (== x y)
      false)))


;; 16.1. Abs
(extend-protocol p/Abs
  BigDecimal
  (abs [x]
    (.abs x)))


;; 16.3. Ceiling
;;
;; Ceiling(argument Decimal) Integer
(extend-protocol p/Ceiling
  BigDecimal
  (ceiling [x]
    (.longValueExact (.setScale x 0 RoundingMode/CEILING))))


;; 16.4. Divide
;;
;; Using `.divide` over Clojure `/` is twice as fast.
(extend-protocol p/Divide
  BigDecimal
  (divide [x y]
    (let [y (if (int? y) (BigDecimal/valueOf ^long y) y)]
      (try
        (.divide x ^BigDecimal y 8 RoundingMode/HALF_UP)
        (catch Exception _)))))


;; 16.5. Exp
;;
;; Exp(argument Decimal) Decimal
;;
;; When invoked with an Integer argument, the argument will be implicitly
;; converted to Decimal.
(extend-protocol p/Exp
  Number
  (exp [x]
    (.setScale (BigDecimal/valueOf (Math/exp x)) 8 RoundingMode/HALF_UP)))


;; 16.6. Floor
;;
;; Floor(argument Decimal) Integer
(extend-protocol p/Floor
  BigDecimal
  (floor [x]
    (.longValueExact (.setScale x 0 RoundingMode/FLOOR))))


;; 16.7. Log
;;
;; Log(argument Decimal, base Decimal) Decimal
;;
;; When invoked with Integer arguments, the arguments will be implicitly
;; converted to Decimal.
;;
;; TODO: can we compute log of certain bases exactly?
(extend-protocol p/Log
  Number
  (log [x base]
    (when (and (pos? x) (some? base) (pos? base))
      (try
        (-> (BigDecimal/valueOf (/ (Math/log x) (Math/log base)))
            (.setScale 8 RoundingMode/HALF_UP))
        (catch Exception _)))))


;; 16.8. Ln
;;
;; Ln(argument Decimal) Decimal
;;
;; When invoked with Integer arguments, the arguments will be implicitly
;; converted to Decimal.
;;
;; It's not possible to implement ln on a decimal. Instead the decimal is
;; converted to double and back again.
(extend-protocol p/Ln
  Number
  (ln [x]
    (when (pos? x)
      (.setScale (BigDecimal/valueOf (Math/log x)) 8 RoundingMode/HALF_UP))))


;; 16.11. Modulo
;;
;; See integer implementation


;; 16.12. Multiply
(extend-protocol p/Multiply
  BigDecimal
  (multiply [x y]
    (let [y (if (int? y) (BigDecimal/valueOf ^long y) y)]
      (some->> y (.multiply x)))))


;; 16.13. Negate
;;
;; See integer implementation


;; 16.14. Power
(extend-protocol p/Power
  BigDecimal
  (power [x exp]
    (when exp
      (BigDecimal/valueOf (Math/pow x exp)))))


;; 16.15. Predecessor
(extend-protocol p/Predecessor
  BigDecimal
  (predecessor [x]
    (.subtract x 1E-8M)))


;; 16.16. Round
(extend-protocol p/Round
  BigDecimal
  (round [x precision]
    (.setScale x ^long precision RoundingMode/HALF_UP)))


;; 16.17. Subtract
;;
;; See integer implementation


;; 16.18. Successor
(extend-protocol p/Successor
  BigDecimal
  (successor [x]
    (.add x 1E-8M)))


;; 16.19. Truncate
(extend-protocol p/Truncate
  BigDecimal
  (truncate [x]
    (.intValueExact (.toBigInteger x))))


;; 16.20. TruncatedDivide
;;
;; See integer implementation


;; 22.23. ToDecimal
(extend-protocol p/ToDecimal
  Long
  (to-decimal [x]
    (BigDecimal/valueOf x))

  BigDecimal
  (to-decimal [x] x)

  String
  (to-decimal [x]
    (when-let [d (try (BigDecimal. x) (catch Exception _))]
      (when (<= min d max)
        (.setScale ^BigDecimal d 8 RoundingMode/HALF_UP)))))


(defn from-literal [s]
  (if-let [d (p/to-decimal s)]
    d
    (throw (Exception. (str "Invalid decimal literal `" s "`.")))))
