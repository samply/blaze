(ns life-fhir-store.elm.literals
  (:require
    [clojure.spec.alpha :as s]
    [life-fhir-store.elm.spec])
  (:refer-clojure :exclude [boolean list]))


(s/fdef boolean
  :args (s/cat :b boolean?)
  :ret :elm/expression)

(defn boolean [b]
  {:type "Literal"
   :valueType "{urn:hl7-org:elm-types:r1}Boolean"
   :value (str b)})


(defn decimal [d]
  {:type "Literal"
   :valueType "{urn:hl7-org:elm-types:r1}Decimal"
   :value (str d)})


(defn integer [i]
  {:type "Literal"
   :valueType "{urn:hl7-org:elm-types:r1}Integer"
   :value (str i)})


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


(s/fdef date
  :args (s/cat :args (s/coll-of int?))
  :ret :elm/expression)

(defn date [[year month day]]
  (cond->
    {:type "Date"
     :year (integer year)}
    month (assoc :month (integer month))
    day (assoc :day (integer day))))


(s/fdef date-time
  :args (s/cat :args (s/coll-of int?))
  :ret :elm/expression)

(defn date-time [[year month day hour minute second millisecond timezone-offset]]
  (cond->
    {:type "DateTime"
     :year (integer year)}
    month (assoc :month (integer month))
    day (assoc :day (integer day))
    hour (assoc :hour (integer hour))
    minute (assoc :minute (integer minute))
    second (assoc :second (integer second))
    millisecond (assoc :millisecond (integer millisecond))
    timezone-offset (assoc :timezone-offset (integer timezone-offset))))
