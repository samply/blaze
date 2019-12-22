(ns blaze.elm.decimal
  "Implementation of the decimal type.

  Operations not implemented here are often implemented in the integer namespace
  because it uses Clojure functions also implementing the functionality for
  decimal.

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.protocols :as p])
  (:import
    [java.math RoundingMode])
  (:refer-clojure :exclude [min max]))


(set! *warn-on-reflection* true)


(def ^:const ^long max-precision
  "The maximum allowed precision of a decimal."
  28)


(def ^:const ^long max-scale
  "The maximum allowed scale of a decimal."
  8)


(def ^:const ^long max-integral-digits
  "Maximum number of integral digits of a decimal."
  (- max-precision max-scale))


(def ^:const ^BigDecimal min-step-size
  "The minimum difference between one decimal and it's successor."
  (.scaleByPowerOfTen 1M (- max-scale)))


(def ^:const ^BigDecimal min
  "Minimum decimal (-10^28 + 1) / 10^8"
  (* (+ (- (.scaleByPowerOfTen 1M max-precision)) 1) min-step-size))


(def ^:const ^BigDecimal max
  "Maximum decimal (10^28 - 1) / 10^8"
  (* (- (.scaleByPowerOfTen 1M max-precision) 1) min-step-size))


(defn within-bounds?
  "Returns true iff `x` is withing the bounds of `min` and `max`."
  [^BigDecimal x]
  (<= (- (.precision x) (.scale x)) max-integral-digits))


(defn- check-overflow
  "Checks that `x` is withing the bounds of `min` and `max`. Returns nil if not."
  [x]
  (when (within-bounds? x)
    x))

(defn- constrain-scale
  "Rounds `x` if it's scale exceeds 8."
  [^BigDecimal x]
  (if (> (.scale x) max-scale)
    (.setScale x max-scale RoundingMode/HALF_UP)
    x))


;; 12.1. Equal
;;
;; For decimal values, trailing zeroes are ignored.
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


;; 16.2. Add
(extend-protocol p/Add
  BigDecimal
  (add [x y]
    (when y
      ;; the scale isn't increased here, so it's sufficient to check for
      ;; overflow. using a MathContext doesn't help here, because we need to
      ;; implement fixed-point arithmetic.
      (check-overflow (.add x (p/to-decimal y))))))


;; 16.3. Ceiling
(extend-protocol p/Ceiling
  BigDecimal
  (ceiling [x]
    (.longValueExact (.setScale x 0 RoundingMode/CEILING))))


;; 16.4. Divide
(extend-protocol p/Divide
  BigDecimal
  (divide [x y]
    (when-let [y (p/to-decimal y)]
      (when-not (zero? y)
        (-> (try
              ;; First try to perform an exact division because if we specify a
              ;; scale it's taken literally and not as maximum.
              (.divide x y)
              (catch ArithmeticException _
                (.divide x y max-scale RoundingMode/HALF_UP)))
            (constrain-scale)
            (check-overflow))))))


;; 16.5. Exp
;;
;; When invoked with an Integer argument, the argument will be implicitly
;; converted to Decimal.
(extend-protocol p/Exp
  Number
  (exp [x]
    (-> (BigDecimal/valueOf (Math/exp x))
        (constrain-scale)
        (check-overflow))))


;; 16.6. Floor
;;
;; The Floor operator returns the first integer less than or equal to the
;; argument.
;;
;; If the argument is null, the result is null.
(extend-protocol p/Floor
  BigDecimal
  (floor [x]
    (.longValueExact (.setScale x 0 RoundingMode/FLOOR))))


;; 16.7. Log
;;
;; When invoked with Integer arguments, the arguments will be implicitly
;; converted to Decimal.
;;
;; TODO: can we compute log of certain bases exactly?
(extend-protocol p/Log
  Number
  (log [x base]
    (when (and (pos? x) (some? base) (pos? base) (not (== 1 base)))
      (-> (BigDecimal/valueOf (/ (Math/log x) (Math/log base)))
          (constrain-scale)
          (check-overflow)))))


;; 16.8. Ln
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
      (-> (BigDecimal/valueOf (Math/log x))
          (constrain-scale)
          (check-overflow)))))


;; 16.11. Modulo
;;
;; See integer implementation


;; 16.12. Multiply
(extend-protocol p/Multiply
  BigDecimal
  (multiply [x y]
    (when y
      (-> (.multiply x (p/to-decimal y))
          (constrain-scale)
          (check-overflow)))))


;; 16.13. Negate
(extend-protocol p/Negate
  BigDecimal
  (negate [x]
    ;; no overflow checking necessary
    (.negate x)))


;; 16.14. Power
(extend-protocol p/Power
  BigDecimal
  (power [x exp]
    (when exp
      (-> (BigDecimal/valueOf (Math/pow x exp))
          (constrain-scale)
          (check-overflow)))))


;; 16.15. Predecessor
(extend-protocol p/Predecessor
  BigDecimal
  (predecessor [x]
    (let [x (.subtract x min-step-size)]
      (if (within-bounds? x)
        x
        ;; TODO: throwing an exception this is inconsistent with subtract
        (throw (ex-info "Predecessor: argument is already the minimum value."
                        {:x x}))))))


;; 16.16. Round
(extend-protocol p/Round
  BigDecimal
  (round [x precision]
    (.setScale x ^long precision RoundingMode/HALF_UP)))


;; 16.17. Subtract
(extend-protocol p/Subtract
  BigDecimal
  (subtract [x y]
    (when y
      ;; the scale isn't increased here, so it's sufficient to check for
      ;; overflow. using a MathContext doesn't help here, because we need to
      ;; implement fixed-point arithmetic.
      (check-overflow (.subtract x (p/to-decimal y))))))


;; 16.18. Successor
(extend-protocol p/Successor
  BigDecimal
  (successor [x]
    (let [x (.add x min-step-size)]
      (if (within-bounds? x)
        x
        ;; TODO: throwing an exception this is inconsistent with add
        (throw (ex-info "Successor: argument is already the maximum value."
                        {:x x}))))))


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
  Integer
  (to-decimal [x]
    (BigDecimal/valueOf (long x)))

  Long
  (to-decimal [x]
    (BigDecimal/valueOf x))

  BigDecimal
  (to-decimal [x] x)

  String
  (to-decimal [s]
    (when-let [d (try (BigDecimal. s) (catch Exception _))]
      (-> d constrain-scale check-overflow))))


(defn from-literal [s]
  (if-let [d (p/to-decimal s)]
    d
    (throw (Exception. (str "Invalid decimal literal `" s "`.")))))


;; 22.28. ToString
(extend-protocol p/ToString
  BigDecimal
  (to-string [x]
    (.toPlainString x)))
