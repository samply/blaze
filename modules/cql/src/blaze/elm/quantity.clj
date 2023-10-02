(ns blaze.elm.quantity
  "Implementation of the quantity type.

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.protocols :as p]
   [clojure.string :as str])
  (:import
   [com.google.common.base CharMatcher]
   [javax.measure Quantity UnconvertibleException Unit]
   [javax.measure.format UnitFormat]
   [javax.measure.spi ServiceProvider]
   [tech.units.indriya ComparableQuantity]
   [tech.units.indriya.function Calculus DefaultNumberSystem]
   [tech.units.indriya.quantity Quantities]))

(set! *warn-on-reflection* true)

(Calculus/setCurrentNumberSystem
 (proxy [DefaultNumberSystem] []
   (narrow [number]
     number)))

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
  (reduce replace-exp s (reverse (range 1 13))))

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

(extend-protocol core/Expression
  Quantity
  (-static [_]
    true)
  (-attach-cache [quantity _]
    quantity)
  (-resolve-refs [quantity _]
    quantity)
  (-resolve-params [quantity _]
    quantity)
  (-eval [quantity _ _ _]
    quantity)
  (-form [quantity]
    `(~'quantity ~(.getValue quantity) ~(format-unit (.getUnit quantity)))))

(defprotocol QuantityDivide
  (quantity-divide [divisor quantity]))

(defprotocol QuantityMultiply
  (quantity-multiply [multiplier quantity]))

;; 2.3. Property
(extend-protocol p/StructuredType
  Quantity
  (get [quantity key]
    (case key
      :value (p/to-decimal (.getValue quantity))
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

;; 16.14. Multiply
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

;; 16.15. Negate
(extend-protocol p/Negate
  Quantity
  (negate [x]
    (Quantities/getQuantity ^Number (p/negate (.getValue x)) (.getUnit x))))

;; 16.18. Predecessor
(extend-protocol p/Predecessor
  Quantity
  (predecessor [x]
    (when-let [value (p/predecessor (p/to-decimal (.getValue x)))]
      (Quantities/getQuantity ^Number value (.getUnit x)))))

;; 16.20. Subtract
(extend-protocol p/Subtract
  Quantity
  (subtract [x y]
    (.subtract x y)))

;; 16.21. Successor
(extend-protocol p/Successor
  Quantity
  (successor [x]
    (when-let [value (p/successor (p/to-decimal (.getValue x)))]
      (Quantities/getQuantity ^Number value (.getUnit x)))))

;; 22. Type Operators

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

(def ^:private ^CharMatcher quote-matcher (CharMatcher/is \'))

;; 22.28. ToQuantity
(extend-protocol p/ToQuantity
  Number
  (to-quantity [x]
    (quantity x "1"))

  String
  (to-quantity [s]
   ;; (+|-)?#0(.0#)?('<unit>')?
    (let [[_ value unit] (re-matches #"([+-]?\d+(?:\.\d+)?)\s*('[^']+')?" s)]
      (when value
        (when-let [value (p/to-decimal value)]
          (quantity value (if unit (.trimFrom quote-matcher unit) "1")))))))

;; 22.30. ToString
(extend-protocol p/ToString
  Quantity
  (to-string [x]
    (str (p/to-string (.getValue x)) " '" (.getUnit x) "'")))
