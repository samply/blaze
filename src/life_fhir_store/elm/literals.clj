(ns life-fhir-store.elm.literals
  (:require
    [clojure.spec.alpha :as s]
    [life-fhir-store.elm.spec]
    [clojure.string :as str])
  (:refer-clojure :exclude [boolean dec int list time]))


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
        timezone-offset (assoc :timezone-offset timezone-offset)))))


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
