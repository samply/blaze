(ns blaze.elm.quantity
  "Implementation of the quantity type.

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.anomaly :as ba :refer [throw-anom]]
    [blaze.elm.protocols :as p]
    [cuerdas.core :as str])
  (:import
    [javax.measure Quantity UnconvertibleException Unit]
    [javax.measure.format UnitFormat]
    [javax.measure.spi ServiceProvider]
    [tech.units.indriya ComparableQuantity]
    [tech.units.indriya.function RationalNumber]
    [tech.units.indriya.quantity Quantities]))


(set! *warn-on-reflection* true)


(def ^:private ^UnitFormat ucum-format
  (.getUnitFormat (.getFormatService (ServiceProvider/current)) "CASE_SENSITIVE"))


(defn- parse-unit* [s]
  (try
    (.parse ucum-format s)
    (catch Throwable t
      (throw-anom
        (ba/incorrect
          (format "Problem while parsing the unit `%s`." s)
          :unit s
          :cause-msg (ex-message t))))))


(defn- replace-exp [s n]
  (str/replace s (str "10*" n) (apply str "1" (repeat n \0))))


(defn- hack-replace-unsupported [s]
  (reduce replace-exp s (range 1 13)))


(let [mem (volatile! {})]
  (defn- parse-unit [s]
    (if-let [unit (get @mem s)]
      unit
      (let [unit (parse-unit* (hack-replace-unsupported s))]
        (vswap! mem assoc s unit)
        unit))))


(defn format-unit
  "Formats the unit after UCUM so that it is parsable again."
  [unit]
  (.format ucum-format unit))


(defn quantity? [x]
  (instance? Quantity x))


(defn quantity
  "Creates a quantity with numerical value and string unit."
  [value unit]
  (Quantities/getQuantity ^Number value ^Unit (parse-unit unit)))


(defprotocol QuantityDivide
  (quantity-divide [divisor quantity]))


(defprotocol QuantityMultiply
  (quantity-multiply [multiplier quantity]))


;; 2.3. Property
(defprotocol ToBigDecimal
  (-to-big-decimal [_]))

(extend-protocol ToBigDecimal
  RationalNumber
  (-to-big-decimal [x]
    (.bigDecimalValue x))
  BigDecimal
  (-to-big-decimal [x]
    x)
  Long
  (-to-big-decimal [x]
    (BigDecimal/valueOf x))
  Integer
  (-to-big-decimal [x]
    (BigDecimal/valueOf ^long x)))

(extend-protocol p/StructuredType
  Quantity
  (get [quantity key]
    (case key
      :value (-to-big-decimal (.getValue quantity))
      :unit (format-unit (.getUnit quantity))
      nil)))


;; 12.1. Equal
(extend-protocol p/Equal
  ComparableQuantity
  (equal [a b]
    (when b
      (try
        (.isEquivalentTo a b)
        (catch UnconvertibleException _ false)))))


;; 12.2. Equivalent
(extend-protocol p/Equivalent
  ComparableQuantity
  (equivalent [a b]
    (if b
      (try
        (.isEquivalentTo a b)
        (catch UnconvertibleException _ false))
      false)))


;; 16.1. Abs
(extend-protocol p/Abs
  Quantity
  (abs [x]
    (Quantities/getQuantity ^Number (p/abs (.getValue x)) (.getUnit x))))


;; 16.2. Add
(extend-protocol p/Add
  Quantity
  (add [x y]
    (some->> y (.add x))))


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
    (Quantities/getQuantity ^Number (p/negate (.getValue x)) (.getUnit x))))


;; 16.15. Predecessor
(extend-protocol p/Predecessor
  Quantity
  (predecessor [x]
    (Quantities/getQuantity ^Number (p/predecessor (.getValue x)) (.getUnit x))))


;; 16.17. Subtract
(extend-protocol p/Subtract
  Quantity
  (subtract [x y]
    (.subtract x y)))


;; 16.18. Successor
(extend-protocol p/Successor
  Quantity
  (successor [x]
    (Quantities/getQuantity ^Number (p/successor (.getValue x)) (.getUnit x))))


;; 22.3. CanConvertQuantity
(extend-protocol p/CanConvertQuantity
  Quantity
  (can-convert-quantity [x unit]
    (when unit
      (-> (.getUnit x) (.isCompatible (parse-unit unit))))))


;; 22.6. ConvertQuantity
(extend-protocol p/ConvertQuantity
  Quantity
  (convert-quantity [x unit]
    (try
      (.to x (parse-unit unit))
      (catch Exception _))))


;; 22.26. ToQuantity
(extend-protocol p/ToQuantity
  Number
  (to-quantity [x]
    (quantity x "1"))

  String
  (to-quantity [s]
    ;; (+|-)?#0(.0#)?('<unit>')?
    (let [[_ value unit] (re-matches #"(\d+(?:\.\d+)?)\s*('[^']+')?" s)]
      (when value
        (quantity (p/to-decimal value) (or (str/trim unit "'") "1"))))))


;; 22.28. ToString
(extend-protocol p/ToString
  Quantity
  (to-string [x]
    (str (p/to-string (.getValue x)) " '" (.getUnit x) "'")))
