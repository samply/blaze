(ns blaze.elm.literal-spec
  (:require
    [blaze.elm.literal :as literal]
    [blaze.elm.spec]
    [clojure.spec.alpha :as s]))



;; 1. Simple Values

;; 1.1. Literal
(s/fdef literal/boolean
  :args (s/cat :s string?)
  :ret :elm/expression)


(s/fdef literal/decimal
  :args (s/cat :s string?)
  :ret :elm/expression)


(s/fdef literal/integer
  :args (s/cat :s string?)
  :ret :elm/expression)


(s/fdef literal/string
  :args (s/cat :s string?)
  :ret :elm/expression)



;; 2. Structured Values

;; 2.1. Tuple
(s/fdef literal/tuple
  :args (s/cat :arg (s/map-of string? :elm/expression))
  :ret :elm/expression)


;; 2.1. Instance
(s/fdef literal/instance
  :args (s/cat :arg (s/tuple string? (s/map-of string? :elm/expression)))
  :ret :elm/expression)



;; 3. Clinical Values

;; 3.1 Code
(s/fdef literal/code
  :args
  (s/cat
    :args
    (s/spec (s/cat :system-name string? :code string? :display (s/? string?))))
  :ret :elm/expression)


;; 3.3. CodeRef
(s/fdef literal/code
  :args (s/cat :name string?)
  :ret :elm/expression)


;; 3.9. Quantity
(s/fdef literal/quantity
  :args (s/cat :args (s/spec (s/cat :value number? :unit (s/? string?))))
  :ret :elm/expression)



;; 9. Reusing Logic

;; 9.2. ExpressionRef
(s/fdef literal/expression-ref
  :args (s/cat :name string?)
  :ret :elm/expression)


(s/fdef literal/equal
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef literal/equivalent
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef literal/greater
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef literal/greater-or-equal
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef literal/less
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef literal/less-or-equal
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef literal/and
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef literal/not
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef literal/or
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef literal/xor
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef literal/is-false
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef literal/is-null
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef literal/is-true
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef literal/list
  :args (s/cat :elements (s/coll-of :elm/expression))
  :ret :elm/expression)


(s/fdef literal/if-expr
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef literal/abs
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef literal/add
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef literal/ceiling
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef literal/divide
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef literal/exp
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef literal/floor
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef literal/log
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef literal/ln
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef literal/modulo
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef literal/multiply
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef literal/negate
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef literal/power
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef literal/predecessor
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef literal/round
  :args (s/cat :arg (s/coll-of :elm/expression))
  :ret :elm/expression)


(s/fdef literal/subtract
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef literal/successor
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef literal/truncate
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef literal/truncated-divide
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef literal/date
  :args (s/cat :arg (s/alt :str string? :exprs (s/coll-of :elm/expression)))
  :ret :elm/expression)


(s/fdef literal/date-from
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef literal/date-time
  :args (s/cat :arg (s/alt :str string? :exprs (s/coll-of :elm/expression)))
  :ret :elm/expression)


(s/fdef literal/time
  :args (s/cat :arg (s/alt :str string? :exprs (s/coll-of :elm/expression)))
  :ret :elm/expression)


;; 19.1. Interval
(s/def ::interval-arg
  (s/cat :low-open (s/? #{:<})
         :low :elm/expression
         :high :elm/expression
         :high-open (s/? #{:>})))

(s/fdef literal/interval
  :args (s/cat :arg (s/spec ::interval-arg))
  :ret :elm/expression)


;; 19.2. After
(s/fdef literal/after
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


;; 19.3. Before
(s/fdef literal/before
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


;; 19.4. Collapse
(s/fdef literal/collapse
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


;; 19.5. Contains
(s/fdef literal/contains
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


;; 19.13. Except
(s/fdef literal/except
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


;; 19.13. Includes
(s/fdef literal/includes
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


;; 19.15. Intersect
(s/fdef literal/intersect
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


;; 19.17. MeetsBefore
(s/fdef literal/meets-before
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


;; 19.18. MeetsAfter
(s/fdef literal/meets-after
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


;; 19.24. ProperContains
(s/fdef literal/proper-contains
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


;; 19.26. ProperIncludes
(s/fdef literal/proper-includes
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


;; 19.31. Union
(s/fdef literal/union
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


;; 20.3. Current
(s/fdef literal/current
  :args (s/cat :scope string?)
  :ret :elm/expression)


;; 20.4. Distinct
(s/fdef literal/distinct
  :args (s/cat :list :elm/expression)
  :ret :elm/expression)


;; 20.8. Exists
(s/fdef literal/exists
  :args (s/cat :list :elm/expression)
  :ret :elm/expression)


;; 20.11. Flatten
(s/fdef literal/flatten
  :args (s/cat :list :elm/expression)
  :ret :elm/expression)


;; 20.25. SingletonFrom
(s/fdef literal/singleton-from
  :args (s/cat :list :elm/expression)
  :ret :elm/expression)


;; 20.28. Times
(s/fdef literal/times
  :args (s/cat :lists (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)



;; 22. Type Operators

;; 22.1. As
(s/fdef literal/as
  :args (s/cat :arg (s/tuple string? :elm/expression))
  :ret :elm/expression)


;; 22.6. ConvertQuantity
(s/fdef literal/convert-quantity
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


;; 22.16. Descendents
(s/fdef literal/descendents
  :args (s/cat :source :elm/expression)
  :ret :elm/expression)


;; 22.21. ToDate
(s/fdef literal/to-date
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.22. ToDateTime
(s/fdef literal/to-date-time
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.23. ToDecimal
(s/fdef literal/to-decimal
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.24. ToInteger
(s/fdef literal/to-integer
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.25. ToList
(s/fdef literal/to-list
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.26. ToQuantity
(s/fdef literal/to-quantity
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.28. ToString
(s/fdef literal/to-string
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)



;; 23. Clinical Operators

;; 23.4. CalculateAgeAt
(s/fdef literal/calculate-age-at
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)
