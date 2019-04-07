(ns life-fhir-store.elm.literals
  (:require
    [clojure.spec.alpha :as s]
    [life-fhir-store.elm.spec])
  (:refer-clojure :exclude [boolean list]))


(s/fdef boolean
  :args (s/cat :s string?)
  :ret :elm/expression)

(defn boolean [s]
  {:type "Literal"
   :valueType "{urn:hl7-org:elm-types:r1}Boolean"
   :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"
   :value s})


(s/fdef decimal
  :args (s/cat :s string?)
  :ret :elm/expression)

(defn decimal [s]
  {:type "Literal"
   :valueType "{urn:hl7-org:elm-types:r1}Decimal"
   :resultTypeName "{urn:hl7-org:elm-types:r1}Decimal"
   :value s})


(s/fdef integer
  :args (s/cat :s string?)
  :ret :elm/expression)

(defn integer [s]
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
     :year (integer (str year))}
    month (assoc :month (integer (str month)))
    day (assoc :day (integer (str day)))))


(s/fdef date-time
  :args (s/cat :args (s/coll-of number?))
  :ret :elm/expression)

(defn date-time [[year month day hour minute second millisecond timezone-offset]]
  (cond->
    {:type "DateTime"
     :year (integer (str year))}
    month (assoc :month (integer (str month)))
    day (assoc :day (integer (str day)))
    hour (assoc :hour (integer (str hour)))
    minute (assoc :minute (integer (str minute)))
    second (assoc :second (integer (str second)))
    millisecond (assoc :millisecond (integer (str millisecond)))
    timezone-offset (assoc :timezone-offset (decimal (str timezone-offset)))))
