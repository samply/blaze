(ns life-fhir-store.datomic.quantity
  (:require
    [clojure.spec.alpha :as s])
  (:import
    [javax.measure Unit]
    [javax.measure.format UnitFormat]
    [systems.uom.ucum.format UCUMFormat$Variant UCUMFormat]
    [tec.uom.se.quantity Quantities]))


(def ^:private ^UnitFormat ucum-format
  (UCUMFormat/getInstance UCUMFormat$Variant/CASE_SENSITIVE))


(defn- parse-unit* [s]
  (try
    (.parse ucum-format s)
    (catch Throwable t
      (throw (ex-info (str "Problem while parsing the unit `" s "`.")
                      {:unit s :cause-msg (.getMessage t)})))))


(let [mem (volatile! {})]
  (defn- parse-unit [s]
    (if-let [unit (get @mem s)]
      unit
      (let [unit (parse-unit* s)]
        (vswap! mem assoc s unit)
        unit))))


(s/fdef quantity
  :args (s/cat :value decimal? :unit (s/nilable string?)))

(defn quantity
  "Creates a quantity with numerical value and string unit."
  [value unit]
  (->> (parse-unit (or unit ""))
       (Quantities/getQuantity value)))


(defn unit? [x]
  (instance? Unit x))


(s/fdef format-unit
  :args (s/cat :unit unit?))

(defn format-unit
  "Formats the unit after UCUM so that it is parsable again."
  [unit]
  (.format ucum-format unit))
