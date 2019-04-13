(ns life-fhir-store.elm.quantity
  "Implementation of the quantity type.

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [clojure.spec.alpha :as s]
    [life-fhir-store.elm.protocols :as p])
  (:import
    [javax.measure Quantity UnconvertibleException]
    [javax.measure.format UnitFormat]
    [systems.uom.ucum.format UCUMFormat$Variant UCUMFormat]
    [tec.uom.se ComparableQuantity]
    [tec.uom.se.quantity DecimalQuantity NumberQuantity Quantities]))


(def ^:private ^UnitFormat ucum-format
  (UCUMFormat/getInstance UCUMFormat$Variant/CASE_SENSITIVE))


(s/fdef parse-quantity
  :args (s/cat :value number? :unit string?))

(defn parse-quantity [value unit]
  (->> (try
         (.parse ucum-format unit)
         (catch Throwable t
           (throw (ex-info (str "Problem while parsing unit `" unit "`.")
                           {:cause t}))))
       (Quantities/getQuantity value)))


(defn print-unit [unit]
  (.format ucum-format unit))


(defprotocol QuantityDivide
  (quantity-divide [divisor quantity]))


(defprotocol QuantityMultiply
  (quantity-multiply [multiplier quantity]))


;; 12.1. Equal
(extend-protocol p/Equal
  ComparableQuantity
  (equal [a b]
    (when b
      (try
        (.isEquivalentTo a b)
        (catch UnconvertibleException _ false)))))


;; 16.1. Abs
(extend-protocol p/Abs
  Quantity
  (abs [this]
    (Quantities/getQuantity (p/abs (.getValue this)) (.getUnit this))))


;; 16.2. Add
(extend-protocol p/Add
  Quantity
  (add [a b]
    (some->> b (.add a))))


;; 16.4. Divide
(extend-protocol p/Divide
  Quantity
  (divide [x y]
    (quantity-divide y x)))

(extend-protocol QuantityDivide
  nil
  (quantity-divide [_ _])

  Number
  (quantity-divide [divisor quantity]
    (.divide ^Quantity quantity divisor))

  Quantity
  (quantity-divide [divisor quantity]
    (.divide ^Quantity quantity divisor)))


;; 16.12. Multiply
(extend-protocol p/Multiply
  Quantity
  (multiply [x y]
    (quantity-multiply y x)))

(extend-protocol QuantityMultiply
  nil
  (quantity-multiply [_ _])

  Number
  (quantity-multiply [multiplier quantity]
    (.multiply ^Quantity quantity multiplier))

  Quantity
  (quantity-multiply [multiplier quantity]
    (.multiply ^Quantity quantity multiplier)))


;; 16.13. Negate
(extend-protocol p/Negate
  Quantity
  (negate [x]
    (.multiply x -1)))


;; 16.15. Predecessor
(extend-protocol p/Predecessor
  NumberQuantity
  (predecessor [x]
    (.subtract x (Quantities/getQuantity 1 (.getUnit x))))

  DecimalQuantity
  (predecessor [x]
    (.subtract x (Quantities/getQuantity 1E-8M (.getUnit x)))))


;; 16.17. Subtract
(extend-protocol p/Subtract
  Quantity
  (subtract [x y]
    (.subtract x y)))


;; 16.18. Successor
(extend-protocol p/Successor
  NumberQuantity
  (successor [x]
    (.add x (Quantities/getQuantity 1 (.getUnit x))))

  DecimalQuantity
  (successor [x]
    (.add x (Quantities/getQuantity 1E-8M (.getUnit x)))))
