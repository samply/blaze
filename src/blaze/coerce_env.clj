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
  (str/includes? (str/lower-case env-var) "pass"))

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
