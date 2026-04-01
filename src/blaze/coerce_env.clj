(ns blaze.coerce-env
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.log]
   [blaze.spec]
   [clojure.string :as str]))

(defn coerce-base-url [s]
  (cond-> s (str/ends-with? s "/") (subs 0 (dec (count s)))))

(defn- coerce-nat-int [s]
  (if-some [i (parse-long s)]
    (if (nat-int? i)
      i
      (ba/incorrect (format "Invalid negative integer %d." i)))
    (ba/incorrect (format "Can't parse integer from `%s`." s))))

(defn jvm-metrics-logger-warn-factor
  "Coerces `s` to a JVM metrics logger warn factor which must be a positive
  integer."
  [s]
  (when-ok [i (coerce-nat-int s)]
    (if (pos? i)
      i
      (ba/incorrect (format "Invalid JVM metrics logger warn factor %d. Must be a positive integer." i)))))

(defn jvm-metrics-logger-warn-threshold
  "Coerces `s` to a JVM metrics logger threshold in the range from 1 (inclusive)
  to 100 (exclusive)."
  [s]
  (when-ok [i (coerce-nat-int s)]
    (let [start 1 end 100]
      (if (and (<= start i) (< i end))
        i
        (ba/incorrect (format "Invalid JVM metrics logger threshold %d. Must be in the range from %d (inclusive) to %d (exclusive)." i start end))))))

(def ^:private valid-db-scaling-factors
  (into #{} (map #(bit-shift-left 1 %)) (range 5)))

(defn coerce-db-scale-factor [s]
  (when-ok [i (coerce-nat-int s)]
    (if (valid-db-scaling-factors i)
      i
      (ba/incorrect (format "Invalid DB scale factor %d. Must be one of %s." i (str/join ", " (sort valid-db-scaling-factors)))))))

(def ^:private valid-db-block-sizes
  (into #{} (map #(bit-shift-left 1 %)) (range 12 18)))

(defn coerce-db-block-size [s]
  (when-ok [i (coerce-nat-int s)]
    (if (valid-db-block-sizes i)
      i
      (ba/incorrect (format "Invalid DB block size %d. Must be one of %s." i (str/join ", " (sort valid-db-block-sizes)))))))

(defn get-blank [m k default]
  (let [v (get m k)]
    (if (or (nil? v) (str/blank? v))
      default
      v)))

(defn- secret? [env-var]
  (let [lower (str/lower-case env-var)]
    (or (str/includes? lower "pass")
        (str/includes? lower "secret"))))

(defn setting [{:keys [env-var default]} value]
  (cond->
   (if (secret? env-var)
     {:masked true}
     {:value value :default-value default})
    (some? default) (assoc :default-value default)))

(defn bindings-and-settings [env & bindings]
  (reduce
   (fn [r [sym [name coercer default]]]
     (if-ok [v (coercer (get-blank env name default))]
       (-> (assoc-in r [:bind-map sym] v)
           (assoc-in [:settings name] (setting {:env-var name :default default} v)))
       reduced))
   {}
   (partition 2 bindings)))
