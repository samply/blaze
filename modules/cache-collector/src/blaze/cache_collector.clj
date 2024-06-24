(ns blaze.cache-collector
  (:require
   [blaze.cache-collector.protocols :as p]
   [blaze.cache-collector.spec]
   [blaze.metrics.core :as metrics]
   [blaze.module :as m]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig])
  (:import
   [com.github.benmanes.caffeine.cache AsyncCache Cache]
   [com.github.benmanes.caffeine.cache.stats CacheStats]))

(set! *warn-on-reflection* true)

(extend-protocol p/StatsCache
  Cache
  (-stats [cache]
    (.stats cache))
  (-estimated-size [cache]
    (.estimatedSize cache))
  AsyncCache
  (-stats [cache]
    (.stats (.synchronous cache)))
  (-estimated-size [cache]
    (.estimatedSize (.synchronous cache))))

(defn- sample-xf [f]
  (map (fn [[name stats estimated-size]] {:label-values [name] :value (f stats estimated-size)})))

(defn- counter-metric [name help f stats]
  (let [samples (into [] (sample-xf f) stats)]
    (metrics/counter-metric name help ["name"] samples)))

(defn- gauge-metric [name help f stats]
  (let [samples (into [] (sample-xf f) stats)]
    (metrics/gauge-metric name help ["name"] samples)))

(def ^:private mapper
  (keep
   (fn [[name cache]]
     (when cache
       [name (p/-stats cache) (p/-estimated-size cache)]))))

(defmethod m/pre-init-spec :blaze/cache-collector [_]
  (s/keys :req-un [::caches]))

(defmethod ig/init-key :blaze/cache-collector
  [_ {:keys [caches]}]
  (metrics/collector
    (let [stats (into [] mapper caches)]
      [(counter-metric
        "blaze_cache_hits_total"
        "Returns the number of times Cache lookup methods have returned a cached value."
        (fn [stats _] (.hitCount ^CacheStats stats))
        stats)
       (counter-metric
        "blaze_cache_misses_total"
        "Returns the number of times Cache lookup methods have returned an uncached (newly loaded) value, or null."
        (fn [stats _] (.missCount ^CacheStats stats))
        stats)
       (counter-metric
        "blaze_cache_load_successes_total"
        "Returns the number of times Cache lookup methods have successfully loaded a new value."
        (fn [stats _] (.loadSuccessCount ^CacheStats stats))
        stats)
       (counter-metric
        "blaze_cache_load_failures_total"
        "Returns the number of times Cache lookup methods failed to load a new value, either because no value was found or an exception was thrown while loading."
        (fn [stats _] (.loadFailureCount ^CacheStats stats))
        stats)
       (counter-metric
        "blaze_cache_load_seconds_total"
        "Returns the total number of seconds the cache has spent loading new values."
        (fn [stats _] (/ (double (.totalLoadTime ^CacheStats stats)) 1e9))
        stats)
       (counter-metric
        "blaze_cache_evictions_total"
        "Returns the number of times an entry has been evicted."
        (fn [stats _] (.evictionCount ^CacheStats stats))
        stats)
       (gauge-metric
        "blaze_cache_estimated_size"
        "Returns the approximate number of entries in this cache."
        (fn [_ estimated-size] estimated-size)
        stats)])))

(derive :blaze/cache-collector :blaze.metrics/collector)
