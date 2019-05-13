(ns blaze.elm.protocols
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
  (exp [x] "Calculates e raised to the power of `x`."))


;; 16.6. Floor
(defprotocol Floor
  (floor [x]))


;; 16.7. Log
(defprotocol Log
  (log [x base]))


;; 16.8. Ln
(defprotocol Ln
  (ln [x] "Calculates the natural logarithm of `x`."))


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



;; 17. String Operators

;; 17.6. Indexer
(defprotocol Indexer
  (indexer [x index]))



;; 18. Date and Time Operators

;; 18.7. DateFrom
(defprotocol DateFrom
  (date-from [x]))


;; 18.9. DateTimeComponentFrom
(defprotocol DateTimeComponentFrom
  (date-time-component-from [x precision]))


;; 18.10. DifferenceBetween
(defprotocol DifferenceBetween
  (difference-between [x y precision]))


;; 18.11. DurationBetween
(defprotocol DurationBetween
  (duration-between [x y precision]))


;; 18.14. SameAs
(defprotocol SameAs
  (same-as [x y precision]))


;; 18.15. SameOrBefore
(defprotocol SameOrBefore
  (same-or-before [x y precision]))


;; 18.16. SameOrAfter
(defprotocol SameOrAfter
  (same-or-after [x y precision]))


;; 19.2. After
(defprotocol After
  (after [x y precision]))


;; 19.3. Before
(defprotocol Before
  (before [x y precision]))


;; 19.5. Contains
(defprotocol Contains
  (contains [interval-or-list x precision]))


;; 19.10. Except
(defprotocol Except
  (except [x y]))


;; 19.13. Includes
(defprotocol Includes
  (includes [x y precision]))


;; 19.15. Intersect
(defprotocol Intersect
  (intersect [a b]))


;; 19.24. ProperContains
(defprotocol ProperContains
  (proper-contains [interval-or-list x precision]))


;; 19.26. ProperIncludes
(defprotocol ProperIncludes
  (proper-includes [x y precision]))


;; 19.31. Union
(defprotocol Union
  (union [a b]))


;; 22.21. ToDate
(defprotocol ToDate
  "Converts an object into something usable as Date relative to `now`.

  Converts OffsetDateTime and Instant to LocalDate so that we can compare
  temporal fields directly."
  (to-date [x now]))


;; 22.22. ToDateTime
(defprotocol ToDateTime
  "Converts an object into something usable as DateTime relative to `now`.

  Converts OffsetDateTime and Instant to LocalDateTime so that we can compare
  temporal fields directly."
  (to-date-time [x now]))


;; 22.23. ToDecimal
(defprotocol ToDecimal
  (to-decimal ^BigDecimal [x]))


;; 22.24. ToInteger
(defprotocol ToInteger
  (to-integer [x]))


;; 22.26. ToQuantity
(defprotocol ToQuantity
  (to-quantity [x]))


;; 22.28. ToString
(defprotocol ToString
  (to-string [x]))
