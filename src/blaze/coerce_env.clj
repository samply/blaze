(ns blaze.coerce-env
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.log]
   [blaze.spec]
   [clojure.string :as str]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defn coerce-base-url [s]
  (cond-> s (str/ends-with? s "/") (subs 0 (dec (count s)))))

(def ^:private valid-failure-modes
  #{:tag-only :tag-outcome :reject})

(defn coerce-validator-failure-mode
  "Coerces `s` to a validator failure mode keyword which must be one of
  `tag-only`, `tag-outcome` or `reject`."
  [s]
  (let [mode (-> s str/trim str/lower-case (str/replace "_" "-") keyword)]
    (if (valid-failure-modes mode)
      mode
      (ba/incorrect
       (format "Invalid validator failure mode `%s`. Must be one of tag-only, tag-outcome or reject." s)))))

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

(defn coerce-cache-size-ratio
  "Coerces `s` to a finite, non-negative ratio of the JVM heap size allocated
  to a cache. The word `false` disables the cache with a ratio of zero."
  [s]
  (let [s (str/trim s)]
    (if (= "false" s)
      0.0
      (if-some [ratio (parse-double s)]
        (cond
          (not (Double/isFinite ratio))
          (ba/incorrect (format "Invalid non-finite cache size ratio %s." ratio))

          (neg? ratio)
          (ba/incorrect (format "Invalid negative cache size ratio %s." ratio))

          :else ratio)
        (ba/incorrect (format "Can't parse a cache size ratio from `%s`." s))))))

(def ^:private ^:const max-cache-size-ratio
  "The maximum ratio of the JVM heap size the resource cache and the resource
  handle cache may take together."
  0.8)

(defn limit-cache-size-ratios
  "Limits the ratios of the resource cache and the resource handle cache in
  `bind-map`, so that both caches take at most 0.8 of the JVM heap together.

  Constrains each ratio to 0.8 first. If both exceed 0.8 together, lowers both
  by the same amount and logs a warning. A disabled resource handle cache has a
  ratio of zero and so leaves the ratio of the resource cache untouched."
  [bind-map]
  (let [rc (min max-cache-size-ratio (double (bind-map 'resource-cache-size-ratio)))
        rhc (min max-cache-size-ratio (double (bind-map 'resource-handle-cache-size-ratio)))
        excess (- (+ rc rhc) max-cache-size-ratio)]
    (if (pos? excess)
      (let [rc' (- rc (/ excess 2))
            rhc' (- rhc (/ excess 2))]
        (log/warn (format "The ratios DB_RESOURCE_CACHE_SIZE_RATIO (%.3f) and DB_RESOURCE_HANDLE_CACHE_SIZE_RATIO (%.3f) exceed %.1f together. Lower them to %.3f and %.3f."
                          rc rhc max-cache-size-ratio rc' rhc'))
        (assoc bind-map
               'resource-cache-size-ratio rc'
               'resource-handle-cache-size-ratio rhc'))
      (assoc bind-map
             'resource-cache-size-ratio rc
             'resource-handle-cache-size-ratio rhc))))

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
