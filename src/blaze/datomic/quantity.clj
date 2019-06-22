(ns blaze.datomic.quantity
  (:require
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom])
  (:import
    [javax.measure Unit]
    [javax.measure.spi ServiceProvider]
    [javax.measure.format UnitFormat]
    [tec.units.indriya.quantity Quantities]))


(def ^:private ^UnitFormat ucum-format
  (.getUnitFormat (.getUnitFormatService (ServiceProvider/current)) "UCUM"))


(defn- parse-unit* [s]
  (try
    (.parse ucum-format s)
    (catch Throwable t
      (throw (ex-info (str "Problem while parsing the unit `" s "`.")
                      (cond->
                        {::anom/category ::anom/incorrect
                         :unit s}
                        (ex-message t)
                        (assoc :cause-msg (ex-message t))))))))


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
  (Quantities/getQuantity value (parse-unit (or unit ""))))


(defn unit? [x]
  (instance? Unit x))


(s/fdef format-unit
  :args (s/cat :unit unit?))

(defn format-unit
  "Formats the unit after UCUM so that it is parsable again."
  [unit]
  (.format ucum-format unit))
