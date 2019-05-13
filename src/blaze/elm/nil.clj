(ns blaze.elm.nil
  "Implementation of protocols for nil.

  By dispatching on nil, returning nil is very effective and implementations
  on other types don't have to check for nil."
  (:require
    [blaze.elm.protocols :as p]))


(extend-protocol p/Equal
  nil
  (equal [_ _]))


(extend-protocol p/Equivalent
  nil
  (equivalent [_ y]
    (nil? y)))


(extend-protocol p/Greater
  nil
  (greater [_ _]))


(extend-protocol p/GreaterOrEqual
  nil
  (greater-or-equal [_ _]))


(extend-protocol p/Less
  nil
  (less [_ _]))


(extend-protocol p/LessOrEqual
  nil
  (less-or-equal [_ _]))


(extend-protocol p/Abs
  nil
  (abs [_]))


(extend-protocol p/Add
  nil
  (add [_ _]))


(extend-protocol p/Ceiling
  nil
  (ceiling [_]))


(extend-protocol p/Divide
  nil
  (divide [_ _]))


(extend-protocol p/Exp
  nil
  (exp [_]))


(extend-protocol p/Floor
  nil
  (floor [_]))


(extend-protocol p/Log
  nil
  (log [_ _]))


(extend-protocol p/Ln
  nil
  (ln [_]))


(extend-protocol p/Modulo
  nil
  (modulo [_ _]))


(extend-protocol p/Multiply
  nil
  (multiply [_ _]))


(extend-protocol p/Negate
  nil
  (negate [_]))


(extend-protocol p/Power
  nil
  (power [_ _]))


(extend-protocol p/Predecessor
  nil
  (predecessor [_]))


(extend-protocol p/Round
  nil
  (round [_ _]))


(extend-protocol p/Subtract
  nil
  (subtract [_ _]))


(extend-protocol p/Successor
  nil
  (successor [_]))


(extend-protocol p/Truncate
  nil
  (truncate [_]))


;; 16.20. TruncatedDivide
(extend-protocol p/TruncatedDivide
  nil
  (truncated-divide [_ _]))



;; 17. String Operators

;; 17.6. Indexer
(extend-protocol p/Indexer
  nil
  (indexer [_ _]))



;; 18. Date and Time Operators

;; 18.7. DateFrom
(extend-protocol p/DateFrom
  nil
  (date-from [_]))


;; 18.9. DateTimeComponentFrom
(extend-protocol p/DateTimeComponentFrom
  nil
  (date-time-component-from [_ _]))


;; 18.10. DifferenceBetween
(extend-protocol p/DifferenceBetween
  nil
  (difference-between [_ _ _]))


;; 18.11. DurationBetween
(extend-protocol p/DurationBetween
  nil
  (duration-between [_ _ _]))


;; 18.14. SameAs
(extend-protocol p/SameAs
  nil
  (same-as [_ _ _]))


;; 18.15. SameOrBefore
(extend-protocol p/SameOrBefore
  nil
  (same-or-before [_ _ _]))


;; 18.16. SameOrAfter
(extend-protocol p/SameOrAfter
  nil
  (same-or-after [_ _ _]))


;; 19.2. After
(extend-protocol p/After
  nil
  (after [_ _ _]))


;; 19.3. Before
(extend-protocol p/Before
  nil
  (before [_ _ _]))


;; 19.5. Contains
(extend-protocol p/Contains
  nil
  (contains [_ _ _]))


;; 19.10. Except
(extend-protocol p/Except
  nil
  (except [_ _]))


;; 19.13. Includes
(extend-protocol p/Includes
  nil
  (includes [_ _ _]))


;; 19.15. Intersect
(extend-protocol p/Intersect
  nil
  (intersect [_ _]))


;; 19.24. ProperContains
(extend-protocol p/ProperContains
  nil
  (proper-contains [_ _ _]))


;; 19.26. ProperIncludes
(extend-protocol p/ProperIncludes
  nil
  (proper-includes [_ _ _]))


;; 19.31. Union
(extend-protocol p/Union
  nil
  (union [_ _]))


;; 22.23. ToDecimal
(extend-protocol p/ToDecimal
  nil
  (to-decimal [_]))


;; 22.24. ToInteger
(extend-protocol p/ToInteger
  nil
  (to-integer [_]))


;; 22.26. ToQuantity
(extend-protocol p/ToQuantity
  nil
  (to-quantity [_]))


;; 22.28. ToString
(extend-protocol p/ToString
  nil
  (to-string [_]))
