(ns blaze.datomic.quantity
  (:require
    [clojure.spec.alpha :as s]))


(defprotocol Quantity
  (value [_])
  (unit [_])
  (system [_])
  (code [_]))


(defn quantity? [x]
  (satisfies? Quantity x))


(defrecord UcumQuantityWithoutUnit [value code]
  Quantity
  (value [_] value)
  (unit [_])
  (system [_] "http://unitsofmeasure.org")
  (code [_] code))


(s/fdef ucum-quantity-without-unit
  :args (s/cat :value number? :code string?))

(defn ucum-quantity-without-unit
  "Creates a quantity with system `http://unitsofmeasure.org` and no
  human-readable unit."
  [value code]
  (->UcumQuantityWithoutUnit value code))


(defrecord UcumQuantityWithSameUnit [value code]
  Quantity
  (value [_] value)
  (unit [_] code)
  (system [_] "http://unitsofmeasure.org")
  (code [_] code))


(s/fdef ucum-quantity-with-same-unit
  :args (s/cat :value number? :code string?))

(defn ucum-quantity-with-same-unit
  "Creates a quantity with system `http://unitsofmeasure.org` and a
  human-readable unit identical to code."
  [value code]
  (->UcumQuantityWithSameUnit value code))


(defrecord UcumQuantityWithDifferentUnit [value unit code]
  Quantity
  (value [_] value)
  (unit [_] unit)
  (system [_] "http://unitsofmeasure.org")
  (code [_] code))


(s/fdef ucum-quantity-with-different-unit
  :args (s/cat :value number? :unit string? :code string?))

(defn ucum-quantity-with-different-unit
  "Creates a quantity with system `http://unitsofmeasure.org` and a
  human-readable unit different from code."
  [value unit code]
  (->UcumQuantityWithDifferentUnit value unit code))


(defrecord CustomQuantity [value unit system code]
  Quantity
  (value [_] value)
  (unit [_] unit)
  (system [_] system)
  (code [_] code))


(s/fdef custom-quantity
  :args
  (s/cat
    :value number?
    :unit (s/nilable string?)
    :system (s/nilable string?)
    :code (s/nilable string?)))

(defn custom-quantity
  "Creates a quantity with custom unit, system and code."
  [value unit system code]
  (->CustomQuantity value unit system code))
