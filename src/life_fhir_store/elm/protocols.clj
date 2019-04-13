(ns life-fhir-store.elm.protocols
  "Protocols for operations on the different data types of ELM.

  Implementations can be found in the integer, decimal, quantity and date-time
  namespaces.

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html.")



;; 12. Comparison Operators

;; 12.1. Equal
(defprotocol Equal
  (equal [x y]))


;; 12.2. Equivalent
(defprotocol Equivalent
  (equivalent [x y]))


;; 12.3. Greater
(defprotocol Greater
  (greater [x y]))


;; 12.4. GreaterOrEqual
(defprotocol GreaterOrEqual
  (greater-or-equal [x y]))


;; 12.5. Less
(defprotocol Less
  (less [x y]))


;; 12.6. LessOrEqual
(defprotocol LessOrEqual
  (less-or-equal [x y]))


;; 12.7. NotEqual
(defprotocol NotEqual
  (not-equal [x y]))



;; 16. Arithmetic Operators

;; 16.1. Abs
(defprotocol Abs
  (abs [x]))


;; 16.2 Add
(defprotocol Add
  (add [x y]))


;; 16.3 Ceiling
(defprotocol Ceiling
  (ceiling [x]))


;; 16.4. Divide
(defprotocol Divide
  (divide [x y]))


;; 16.5. Exp
(defprotocol Exp
  (exp [x]))


;; 16.6. Floor
(defprotocol Floor
  (floor [x]))


;; 16.7. Log
(defprotocol Log
  (log [x base]))


;; 16.8. Ln
(defprotocol Ln
  (ln [x]))


;; 16.11. Modulo
(defprotocol Modulo
  (modulo [num div]))


;; 16.12. Multiply
(defprotocol Multiply
  (multiply [x y]))


;; 16.13. Negate
(defprotocol Negate
  (negate [x]))


;; 16.14. Power
(defprotocol Power
  (power [x exp]))


;; 16.15. Predecessor
(defprotocol Predecessor
  (predecessor [x]))


;; 16.16. Round
(defprotocol Round
  (round [x precision]))


;; 16.17. Subtract
(defprotocol Subtract
  (subtract [x y]))


;; 16.18. Successor
(defprotocol Successor
  (successor [x]))


;; 16.19. Truncate
(defprotocol Truncate
  (truncate [x]))


;; 16.20. TruncatedDivide
(defprotocol TruncatedDivide
  (truncated-divide [num div]))


;; 18.11. DurationBetween
(defprotocol DurationBetween
  "Returns the duration in `chrono-unit` between `this` and `other` if the
  precisions are sufficient."
  (duration-between [this other chrono-unit]))


;; 22.19. ToDate
(defprotocol ToDate
  "Converts an object into something usable as Date relative to `now`.

  Converts OffsetDateTime and Instant to LocalDate so that we can compare
  temporal fields directly."
  (to-date [x now]))


;; 22.20. ToDateTime
(defprotocol ToDateTime
  "Converts an object into something usable as DateTime relative to `now`.

  Converts OffsetDateTime and Instant to LocalDateTime so that we can compare
  temporal fields directly."
  (to-date-time [x now]))


;; 22.23. ToDecimal
(defprotocol ToDecimal
  (to-decimal [x]))
