(ns blaze.elm.literal
  (:require
    [blaze.elm.spec]
    [clojure.spec.alpha :as s]
    [clojure.string :as str])
  (:refer-clojure :exclude [and boolean distinct flatten list not or time]))


;; 1. Simple Values

;; 1.1. Literal
(defn boolean [s]
  {:type "Literal"
   :valueType "{urn:hl7-org:elm-types:r1}Boolean"
   :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"
   :value s})


(defn decimal [s]
  {:type "Literal"
   :valueType "{urn:hl7-org:elm-types:r1}Decimal"
   :resultTypeName "{urn:hl7-org:elm-types:r1}Decimal"
   :value s})


(defn integer [s]
  {:type "Literal"
   :valueType "{urn:hl7-org:elm-types:r1}Integer"
   :resultTypeName "{urn:hl7-org:elm-types:r1}Integer"
   :value s})


(defn string [s]
  {:type "Literal"
   :valueType "{urn:hl7-org:elm-types:r1}String"
   :resultTypeName "{urn:hl7-org:elm-types:r1}String"
   :value s})



;; 2. Structured Values

;; 2.1. Tuple
(defn tuple [elements]
  {:type "Tuple"
   :element (reduce #(conj %1 {:name (key %2) :value (val %2)}) [] elements)})


;; 2.2. Instance
(defn instance [[type elements]]
  {:type "Instance"
   :classType type
   :element (reduce #(conj %1 {:name (key %2) :value (val %2)}) [] elements)})



;; 3. Clinical Values

;; 3.1 Code
(defn code [[system-name code display]]
  (cond->
    {:type "Code"
     :system {:type "CodeSystemRef" :name system-name}
     :code code}
    display
    (assoc :display display)))


;; 3.3. CodeRef
(defn code-ref [name]
  {:type "CodeRef" :name name})


;; 3.9. Quantity
(defn quantity [[value unit]]
  (cond->
    {:type "Quantity"
     :value value}
    unit
    (assoc :unit unit)))



;; 9. Reusing Logic

;; 9.2. ExpressionRef
(defn expression-ref [name]
  {:type "ExpressionRef"
   :name name})


(defn equal [ops]
  {:type "Equal"
   :operand ops})


(defn equivalent [ops]
  {:type "Equivalent"
   :operand ops})


(defn greater [ops]
  {:type "Greater"
   :operand ops})


(defn greater-or-equal [ops]
  {:type "GreaterOrEqual"
   :operand ops})


(defn less [ops]
  {:type "Less"
   :operand ops})


(defn less-or-equal [ops]
  {:type "LessOrEqual"
   :operand ops})


;; 13.1. And
(defn and [ops]
  {:type "And"
   :operand ops})


;; 13.3 Not
(defn not [op]
  {:type "Not"
   :operand op})


;; 13.4. Or
(defn or [ops]
  {:type "Or"
   :operand ops})


;; 13.5. Xor
(defn xor [ops]
  {:type "Xor"
   :operand ops})


;; 14.3. IsFalse
(defn is-false [op]
  {:type "IsFalse"
   :operand op})


;; 14.4. IsNull
(defn is-null [op]
  {:type "IsNull"
   :operand op})


;; 14.5. IsTrue
(defn is-true [op]
  {:type "IsTrue"
   :operand op})


(defn list [elements]
  {:type "List"
   :element elements})


(defn if-expr [[condition then else]]
  {:type "If" :condition condition :then then :else else})


;; 16.1. Abs
(defn abs [op]
  {:type "Abs"
   :operand op})


;; 16.2. Add
(defn add [ops]
  {:type "Add"
   :operand ops})


;; 16.3. Ceiling
(defn ceiling [op]
  {:type "Ceiling"
   :operand op})


;; 16.4. Divide
(defn divide [ops]
  {:type "Divide"
   :operand ops})


;; 16.5. Exp
(defn exp [op]
  {:type "Exp"
   :operand op})


;; 16.6. Floor
(defn floor [op]
  {:type "Floor"
   :operand op})


;; 16.7. Log
(defn log [ops]
  {:type "Log"
   :operand ops})


;; 16.8. Ln
(defn ln [op]
  {:type "Ln"
   :operand op})


;; 16.11. Modulo
(defn modulo [ops]
  {:type "Modulo"
   :operand ops})


;; 16.12. Multiply
(defn multiply [ops]
  {:type "Multiply"
   :operand ops})


;; 16.13. Negate
(defn negate [op]
  {:type "Negate"
   :operand op})


;; 16.14. Power
(defn power [ops]
  {:type "Power"
   :operand ops})


;; 16.15. Predecessor
(defn predecessor [op]
  {:type "Predecessor"
   :operand op})


;; 16.16. Round
(defn round [[operand precision]]
  (cond->
    {:type "Round"
     :operand operand}
    precision
    (assoc :precision precision)))


(defn subtract [ops]
  {:type "Subtract"
   :operand ops})


(defn successor [op]
  {:type "Successor"
   :operand op})


;; 16.22. Truncate
(defn truncate [op]
  {:type "Truncate"
   :operand op})


(defn truncated-divide [ops]
  {:type "TruncatedDivide"
   :operand ops})


;; 18.6. Date
(defn date [arg]
  (if (string? arg)
    (date (map integer (str/split arg #"-")))
    (let [[year month day] arg]
      (cond->
        {:type "Date"
         :year year}
        month (assoc :month month)
        day (assoc :day day)))))


;; 18.7. DateFrom
(defn date-from [op]
  {:type "DateFrom"
   :operand op})


;; 18.7. DateTime
(defn date-time [arg]
  (if (string? arg)
    (date-time (map integer (str/split arg #"[-T:.]")))
    (let [[year month day hour minute second millisecond timezone-offset] arg]
      (cond->
        {:type "DateTime"
         :year year}
        month (assoc :month month)
        day (assoc :day day)
        hour (assoc :hour hour)
        minute (assoc :minute minute)
        second (assoc :second second)
        millisecond (assoc :millisecond millisecond)
        timezone-offset (assoc :timezoneOffset timezone-offset)))))


(defn time [arg]
  (if (string? arg)
    (time (map integer (str/split arg #"[:.]")))
    (let [[hour minute second millisecond] arg]
      (cond->
        {:type "Time"
         :hour hour}
        minute (assoc :minute minute)
        second (assoc :second second)
        millisecond (assoc :millisecond millisecond)))))


(defn duration-between [[a b precision]]
  {:type "DurationBetween" :operand [a b] :precision precision})


;; 19.1. Interval
(s/def ::interval-arg
  (s/cat :low-open (s/? #{:<})
         :low :elm/expression
         :high :elm/expression
         :high-open (s/? #{:>})))


(defn interval [arg]
  (let [{:keys [low-open low high high-open]} (s/conform ::interval-arg arg)]
    {:type "Interval"
     :low low
     :high high
     :lowClosed (nil? low-open)
     :highClosed (nil? high-open)
     :resultTypeSpecifier
     {:type "IntervalTypeSpecifier",
      :pointType
      {:type "NamedTypeSpecifier"
       :name (clojure.core/or (:resultTypeName low) (:resultTypeName high))}}}))


;; 19.2. After
(defn after [ops]
  {:type "After" :operand ops})


;; 19.3. Before
(defn before [ops]
  {:type "Before" :operand ops})


;; 19.4. Collapse
(defn collapse [ops]
  {:type "Collapse" :operand ops})


;; 19.5. Contains
(defn contains [ops]
  {:type "Contains" :operand ops})


;; 19.13. Except
(defn except [ops]
  {:type "Except" :operand ops})


;; 19.13. Includes
(defn includes [ops]
  {:type "Includes" :operand ops})


;; 19.15. Intersect
(defn intersect [ops]
  {:type "Intersect" :operand ops})


;; 19.17. MeetsBefore
(defn meets-before [ops]
  {:type "MeetsBefore" :operand ops})


;; 19.18. MeetsAfter
(defn meets-after [ops]
  {:type "MeetsAfter" :operand ops})


;; 19.24. ProperContains
(defn proper-contains [ops]
  {:type "ProperContains" :operand ops})


;; 19.26. ProperIncludes
(defn proper-includes [ops]
  {:type "ProperIncludes" :operand ops})


;; 19.31. Union
(defn union [ops]
  {:type "Union" :operand ops})


;; 20.3. Current
(defn current [scope]
  {:type "Current" :scope scope})


;; 20.4. Distinct
(defn distinct [list]
  {:type "Distinct" :operand list})


;; 20.8. Exists
(defn exists [list]
  {:type "Exists" :operand list})


;; 20.11. Flatten
(defn flatten [list]
  {:type "Flatten" :operand list})


;; 20.25. SingletonFrom
(defn singleton-from [list]
  {:type "SingletonFrom" :operand list})


;; 20.28. Times
(defn times [lists]
  {:type "SingletonFrom" :operand lists})


;; 22.1. As
(defn as [[type operand]]
  {:type "As" :asType type :operand operand})


;; 22.4. CanConvertQuantity
(defn can-convert-quantity [list]
  {:type "CanConvertQuantity" :operand list})


;; 22.4. Children
(defn children [source]
  {:type "Children" :source source})


;; 22.6. ConvertQuantity
(defn convert-quantity [ops]
  {:type "ConvertQuantity" :operand ops})


;; 22.16. Descendents
(defn descendents [source]
  {:type "Descendents" :source source})


;; 22.21. ToDate
(defn to-date [operand]
  {:type "ToDate" :operand operand})


;; 22.22. ToDateTime
(defn to-date-time [operand]
  {:type "ToDateTime" :operand operand})


;; 22.23. ToDecimal
(defn to-decimal [operand]
  {:type "ToDecimal" :operand operand})


;; 22.24. ToInteger
(defn to-integer [operand]
  {:type "ToInteger" :operand operand})


;; 22.25. ToList
(defn to-list [operand]
  {:type "ToList" :operand operand})


;; 22.26. ToQuantity
(defn to-quantity [operand]
  {:type "ToQuantity" :operand operand})


;; 22.28. ToString
(defn to-string [operand]
  {:type "ToString" :operand operand})



;; 23. Clinical Operators

;; 23.4. CalculateAgeAt
(defn calculate-age-at [ops]
  {:type "CalculateAgeAt" :operand ops})
