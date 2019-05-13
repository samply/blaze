(ns blaze.elm.literals
  (:require
    [clojure.spec.alpha :as s]
    [blaze.elm.spec]
    [clojure.string :as str])
  (:refer-clojure :exclude [boolean dec distinct flatten int list time]))


(s/fdef boolean
  :args (s/cat :s string?)
  :ret :elm/expression)

(defn boolean [s]
  {:type "Literal"
   :valueType "{urn:hl7-org:elm-types:r1}Boolean"
   :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"
   :value s})


(s/fdef dec
  :args (s/cat :s string?)
  :ret :elm/expression)

(defn dec [s]
  {:type "Literal"
   :valueType "{urn:hl7-org:elm-types:r1}Decimal"
   :resultTypeName "{urn:hl7-org:elm-types:r1}Decimal"
   :value s})


(s/fdef int
  :args (s/cat :s string?)
  :ret :elm/expression)

(defn int [s]
  {:type "Literal"
   :valueType "{urn:hl7-org:elm-types:r1}Integer"
   :resultTypeName "{urn:hl7-org:elm-types:r1}Integer"
   :value s})


(s/fdef string
  :args (s/cat :s string?)
  :ret :elm/expression)

(defn string [s]
  {:type "Literal"
   :valueType "{urn:hl7-org:elm-types:r1}String"
   :resultTypeName "{urn:hl7-org:elm-types:r1}String"
   :value s})


(s/fdef quantity
  :args (s/cat :args (s/spec (s/cat :value number? :unit (s/? string?))))
  :ret :elm/expression)

(defn quantity [[value unit]]
  (cond->
    {:type "Quantity"
     :value value}
    unit
    (assoc :unit unit)))


(s/fdef tuple
  :args (s/cat :arg (s/map-of string? :elm/expression))
  :ret :elm/expression)

(defn tuple [m]
  {:type "Tuple"
   :element (reduce #(conj %1 {:name (key %2) :value (val %2)}) [] m)})


(s/fdef equal
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn equal [ops]
  {:type "Equal"
   :operand ops})


(s/fdef equivalent
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn equivalent [ops]
  {:type "Equivalent"
   :operand ops})


(s/fdef greater
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn greater [ops]
  {:type "Greater"
   :operand ops})


(s/fdef greater-or-equal
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn greater-or-equal [ops]
  {:type "GreaterOrEqual"
   :operand ops})


(s/fdef less
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn less [ops]
  {:type "Less"
   :operand ops})


(s/fdef less-or-equal
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn less-or-equal [ops]
  {:type "LessOrEqual"
   :operand ops})


(s/fdef not-equal
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn not-equal [ops]
  {:type "NotEqual"
   :operand ops})


(s/fdef list
  :args (s/cat :elements (s/coll-of :elm/expression))
  :ret :elm/expression)

(defn list [elements]
  {:type "List"
   :element elements})


(s/fdef if-expr
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression :elm/expression))
  :ret :elm/expression)

(defn if-expr [[condition then else]]
  {:type "If" :condition condition :then then :else else})


(s/fdef add
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn add [ops]
  {:type "Add"
   :operand ops})


(s/fdef divide
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn divide [ops]
  {:type "Divide"
   :operand ops})


(s/fdef multiply
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn multiply [ops]
  {:type "Multiply"
   :operand ops})


(s/fdef negate
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)

(defn negate [op]
  {:type "Negate"
   :operand op})


(s/fdef power
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn power [ops]
  {:type "Power"
   :operand ops})


(s/fdef predecessor
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)

(defn predecessor [op]
  {:type "Predecessor"
   :operand op})


(s/fdef round
  :args (s/cat :arg (s/coll-of :elm/expression))
  :ret :elm/expression)

(defn round [[operand precision]]
  (cond->
    {:type "Round"
     :operand operand}
    precision
    (assoc :precision precision)))


(s/fdef subtract
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn subtract [ops]
  {:type "Subtract"
   :operand ops})


(s/fdef successor
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)

(defn successor [op]
  {:type "Successor"
   :operand op})


(s/fdef truncate
  :args (s/cat :op :elm/expression)
  :ret :elm/expression)

(defn truncate [op]
  {:type "Truncate"
   :operand op})


(s/fdef truncated-divide
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn truncated-divide [ops]
  {:type "TruncatedDivide"
   :operand ops})


(s/fdef date
  :args (s/cat :arg (s/alt :str string? :exprs (s/coll-of :elm/expression)))
  :ret :elm/expression)

(defn date [arg]
  (if (string? arg)
    (date (map int (str/split arg #"-")))
    (let [[year month day] arg]
      (cond->
        {:type "Date"
         :year year}
        month (assoc :month month)
        day (assoc :day day)))))


(s/fdef date-time
  :args (s/cat :arg (s/alt :str string? :exprs (s/coll-of :elm/expression)))
  :ret :elm/expression)

(defn date-time [arg]
  (if (string? arg)
    (date-time (map int (str/split arg #"[-T:.]")))
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


(s/fdef time
  :args (s/cat :arg (s/alt :str string? :exprs (s/coll-of :elm/expression)))
  :ret :elm/expression)

(defn time [arg]
  (if (string? arg)
    (time (map int (str/split arg #"[:.]")))
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

(s/fdef interval
  :args (s/cat :arg (s/spec ::interval-arg))
  :ret :elm/expression)

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
       :name (or (:resultTypeName low) (:resultTypeName high))}}}))


;; 19.2. After
(s/fdef after
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn after [ops]
  {:type "After" :operand ops})


;; 19.3. Before
(s/fdef before
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn before [ops]
  {:type "Before" :operand ops})


;; 19.4. Collapse
(s/fdef collapse
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn collapse [ops]
  {:type "Collapse" :operand ops})


;; 19.5. Contains
(s/fdef contains
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn contains [ops]
  {:type "Contains" :operand ops})


;; 19.13. Except
(s/fdef except
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn except [ops]
  {:type "Except" :operand ops})


;; 19.13. Includes
(s/fdef includes
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn includes [ops]
  {:type "Includes" :operand ops})


;; 19.15. Intersect
(s/fdef intersect
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn intersect [ops]
  {:type "Intersect" :operand ops})


;; 19.17. MeetsBefore
(s/fdef meets-before
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn meets-before [ops]
  {:type "MeetsBefore" :operand ops})


;; 19.18. MeetsAfter
(s/fdef meets-after
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn meets-after [ops]
  {:type "MeetsAfter" :operand ops})


;; 19.24. ProperContains
(s/fdef proper-contains
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn proper-contains [ops]
  {:type "ProperContains" :operand ops})


;; 19.26. ProperIncludes
(s/fdef proper-includes
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn proper-includes [ops]
  {:type "ProperIncludes" :operand ops})


;; 19.31. Union
(s/fdef union
  :args (s/cat :ops (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn union [ops]
  {:type "Union" :operand ops})


;; 20.3. Current
(s/fdef current
  :args (s/cat :scope string?)
  :ret :elm/expression)

(defn current [scope]
  {:type "Current" :scope scope})


;; 20.4. Distinct
(s/fdef distinct
  :args (s/cat :list :elm/expression)
  :ret :elm/expression)

(defn distinct [list]
  {:type "Distinct" :operand list})


;; 20.8. Exists
(s/fdef exists
  :args (s/cat :list :elm/expression)
  :ret :elm/expression)

(defn exists [list]
  {:type "Exists" :operand list})


;; 20.11. Flatten
(s/fdef flatten
  :args (s/cat :list :elm/expression)
  :ret :elm/expression)

(defn flatten [list]
  {:type "Flatten" :operand list})


;; 20.25. SingletonFrom
(s/fdef singleton-from
  :args (s/cat :list :elm/expression)
  :ret :elm/expression)

(defn singleton-from [list]
  {:type "SingletonFrom" :operand list})


;; 20.28. Times
(s/fdef times
  :args (s/cat :lists (s/tuple :elm/expression :elm/expression))
  :ret :elm/expression)

(defn times [lists]
  {:type "SingletonFrom" :operand lists})


;; 22.1. As
(s/fdef as
  :args (s/cat :arg (s/tuple string? :elm/expression))
  :ret :elm/expression)

(defn as [[type operand]]
  {:type "As" :asType type :operand operand})
