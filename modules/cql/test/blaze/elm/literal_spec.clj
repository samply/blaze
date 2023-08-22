(ns blaze.elm.literal-spec
  (:require
    [blaze.elm.literal :as elm]
    [blaze.elm.spec]
    [clojure.spec.alpha :as s]))



;; 1. Simple Values

;; 1.1. Literal
(s/fdef elm/boolean
  :args (s/cat :s string?)
  :ret :elm/expression)


(s/fdef elm/decimal
  :args (s/cat :s string?)
  :ret :elm/expression)


(s/fdef elm/integer
  :args (s/cat :s string?)
  :ret :elm/expression)


(s/fdef elm/string
  :args (s/cat :s string?)
  :ret :elm/expression)



;; 2. Structured Values

;; 2.1. Tuple
(s/fdef elm/tuple
  :args (s/cat :arg (s/map-of string? :elm/expression))
  :ret :elm/expression)


;; 2.2. Instance
(s/fdef elm/instance
  :args (s/cat :arg (s/tuple string? (s/map-of string? :elm/expression)))
  :ret :elm/expression)



;; 3. Clinical Values

;; 3.1 Code
(s/fdef elm/code
  :args
  (s/cat
    :args
    (s/spec (s/cat :system-name string? :code string? :display (s/? string?))))
  :ret :elm/expression)


;; 3.3. CodeRef
(s/fdef elm/code-ref
  :args (s/cat :name string?)
  :ret :elm/expression)


;; 3.6. Concept
(s/fdef elm/concept
  :args (s/cat
          :args
          (s/spec (s/cat
                    :codes (s/coll-of :elm/code)
                    :display (s/? string?))))
  :ret :elm/expression)


;; 3.8. ConceptRef
(s/fdef elm/concept-ref
  :args (s/cat :name string?)
  :ret :elm/expression)


;; 3.9. Quantity
(s/fdef elm/quantity
  :args (s/cat :args (s/spec (s/cat :value number? :unit (s/? string?))))
  :ret :elm/expression)


;; 3.10. Ratio
(s/fdef elm/ratio
          :args
          (s/cat
            :args
            (s/spec
              (s/cat
                :numerator
                (s/spec (s/cat :numerator-value number? :numerator-unit (s/? string?)))
                :denominator
                (s/spec (s/cat :denominator-value number? :denominator-unit (s/? string?))))))
          :ret :elm/expression)


;; 9. Reusing Logic

;; 9.2. ExpressionRef
(s/fdef elm/expression-ref
  :args (s/cat :name string?)
  :ret :elm/expression)


(s/fdef elm/equal
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef elm/equivalent
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef elm/greater
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef elm/greater-or-equal
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef elm/less
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef elm/less-or-equal
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef elm/and
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef elm/not
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef elm/or
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef elm/xor
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef elm/is-false
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef elm/is-null
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef elm/is-true
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef elm/list
  :args (s/cat :elements (s/coll-of :elm/expression))
  :ret :elm/expression)


(s/fdef elm/if-expr
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef elm/abs
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef elm/add
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef elm/ceiling
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef elm/divide
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef elm/exp
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef elm/floor
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef elm/log
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef elm/ln
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef elm/modulo
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef elm/multiply
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef elm/negate
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef elm/power
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef elm/predecessor
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef elm/round
  :args (s/cat :ops (s/alt :single :elm/expression
                           :multi (s/spec (s/cat :x :elm/expression
                                                 :precision (s/? :elm/expression)))))
  :ret :elm/expression)


(s/fdef elm/subtract
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef elm/successor
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef elm/truncate
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef elm/truncated-divide
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


(s/fdef elm/date
  :args (s/cat :arg (s/alt :str string? :exprs (s/coll-of :elm/expression)))
  :ret :elm/expression)


(s/fdef elm/date-from
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)


(s/fdef elm/date-time
  :args (s/cat :arg (s/alt :str string? :exprs (s/coll-of :elm/expression)))
  :ret :elm/expression)


(s/fdef elm/time
  :args (s/cat :arg (s/alt :str string? :exprs (s/coll-of :elm/expression)))
  :ret :elm/expression)


;; 19.1. Interval
(s/def ::interval-arg
  (s/cat :low-open (s/? #{:<})
         :low :elm/expression
         :high :elm/expression
         :high-open (s/? #{:>})))

(s/fdef elm/interval
  :args (s/cat :arg (s/spec ::interval-arg))
  :ret :elm/expression)


;; 19.2. After
(s/fdef elm/after
  :args (s/cat :ops (s/spec (s/cat :x :elm/expression :y :elm/expression
                                   :precision (s/? string?))))
  :ret :elm/expression)


;; 19.3. Before
(s/fdef elm/before
  :args (s/cat :ops (s/spec (s/cat :x :elm/expression :y :elm/expression
                                   :precision (s/? string?))))
  :ret :elm/expression)


;; 19.4. Collapse
(s/fdef elm/collapse
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


;; 19.5. Contains
(s/fdef elm/contains
  :args (s/cat :ops (s/spec (s/cat :x :elm/expression :y :elm/expression
                                   :precision (s/? string?))))
  :ret :elm/expression)


;; 19.6. End
(s/fdef elm/end
  :args (s/cat :interval :elm/expression)
  :ret :elm/expression)


;; 19.7. Ends
(s/fdef elm/ends
  :args (s/cat :ops (s/spec (s/cat :x :elm/expression :y :elm/expression
                                   :precision (s/? string?))))
  :ret :elm/expression)


;; 19.10. Except
(s/fdef elm/except
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


;; 19.13. Includes
(s/fdef elm/includes
  :args (s/cat :ops (s/spec (s/cat :x :elm/expression :y :elm/expression
                                   :precision (s/? string?))))
  :ret :elm/expression)


;; 19.15. Intersect
(s/fdef elm/intersect
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


;; 19.17. MeetsBefore
(s/fdef elm/meets-before
  :args (s/cat :ops (s/spec (s/cat :x :elm/expression :y :elm/expression
                                   :precision (s/? string?))))
  :ret :elm/expression)


;; 19.18. MeetsAfter
(s/fdef elm/meets-after
  :args (s/cat :ops (s/spec (s/cat :x :elm/expression :y :elm/expression
                                   :precision (s/? string?))))
  :ret :elm/expression)


;; 19.20. Overlaps
(s/fdef elm/overlaps
  :args (s/cat :ops (s/spec (s/cat :x :elm/expression :y :elm/expression
                                   :precision (s/? string?))))
  :ret :elm/expression)


;; 19.24. ProperContains
(s/fdef elm/proper-contains
  :args (s/cat :ops (s/spec (s/cat :x :elm/expression :y :elm/expression
                                   :precision (s/? string?))))
  :ret :elm/expression)


;; 19.26. ProperIncludes
(s/fdef elm/proper-includes
  :args (s/cat :ops (s/spec (s/cat :x :elm/expression :y :elm/expression
                                   :precision (s/? string?))))
  :ret :elm/expression)


;; 19.30. ProperIncludes
(s/fdef elm/starts
  :args (s/cat :ops (s/spec (s/cat :x :elm/expression :y :elm/expression
                                   :precision (s/? string?))))
  :ret :elm/expression)


;; 19.31. Union
(s/fdef elm/union
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


;; 20.3. Current
(s/fdef elm/current
  :args (s/cat :scope (s/nilable string?))
  :ret :elm/expression)


;; 20.4. Distinct
(s/fdef elm/distinct
  :args (s/cat :list :elm/expression)
  :ret :elm/expression)


;; 20.8. Exists
(s/fdef elm/exists
  :args (s/cat :list :elm/expression)
  :ret :elm/expression)


;; 20.10. First
(s/fdef elm/first
  :args (s/cat :source :elm/expression)
  :ret :elm/expression)


;; 20.11. Flatten
(s/fdef elm/flatten
  :args (s/cat :list :elm/expression)
  :ret :elm/expression)


;; 20.25. SingletonFrom
(s/fdef elm/singleton-from
  :args (s/cat :list :elm/expression)
  :ret :elm/expression)


;; 20.28. Times
(s/fdef elm/times
  :args (s/cat :lists (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)



;; 22. Type Operators

;; 22.1. As
(s/fdef elm/as
  :args (s/cat :arg (s/tuple string? :elm/expression))
  :ret :elm/expression)


;; 22.6. ConvertQuantity
(s/fdef elm/convert-quantity
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)


;; 22.7. ConvertsToBoolean
(s/fdef elm/converts-to-boolean
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.8. ConvertsToDate
(s/fdef elm/converts-to-date
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.9. ConvertsToDateTime
(s/fdef elm/converts-to-date-time
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.10. ConvertsToDecimal
(s/fdef elm/converts-to-decimal
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.11. ConvertsToLong
(s/fdef elm/converts-to-long
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.12. ConvertsToInteger
(s/fdef elm/converts-to-integer
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.13. ConvertsToQuantity
(s/fdef elm/converts-to-quantity
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.14. ConvertsToRatio
(s/fdef elm/converts-to-ratio
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.15. ConvertsToString
(s/fdef elm/converts-to-string
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.16. ConvertsToTime
(s/fdef elm/converts-to-time
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.17. Descendents
(s/fdef elm/descendents
  :args (s/cat :source :elm/expression)
  :ret :elm/expression)


;; 22.21. ToConcept
(s/fdef elm/to-concept
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.22. ToDate
(s/fdef elm/to-date
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.23. ToDateTime
(s/fdef elm/to-date-time
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.24. ToDecimal
(s/fdef elm/to-decimal
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.25. ToInteger
(s/fdef elm/to-integer
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.26. ToList
(s/fdef elm/to-list
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.27. ToLong
(s/fdef elm/to-long
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.28. ToQuantity
(s/fdef elm/to-quantity
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.29. ToRatio
(s/fdef elm/to-ratio
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.30. ToString
(s/fdef elm/to-string
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 22.31. ToTime
(s/fdef elm/to-time
  :args (s/cat :operand :elm/expression)
  :ret :elm/expression)


;; 23. Clinical Operators

;; 23.4. CalculateAgeAt
(s/fdef elm/calculate-age-at
  :args (s/cat :ops (s/spec (s/cat :x :elm/expression :y :elm/expression
                                   :precision (s/? string?))))
  :ret :elm/expression)
