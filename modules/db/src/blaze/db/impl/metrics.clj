(ns blaze.db.impl.metrics
  (:import
    [com.github.benmanes.caffeine.cache AsyncCache]
    [com.github.benmanes.caffeine.cache.stats CacheStats]
    [io.prometheus.client Collector CounterMetricFamily]))


(set! *warn-on-reflection* true)


(defn- hits-total [name stats]
  (-> (CounterMetricFamily.
        "blaze_db_cache_hits_total"
        "Returns the number of times Cache lookup methods have returned a cached value."
        ["name"])
      (.addMetric [name] (.hitCount ^CacheStats stats))))


(defn- loads-total [name stats]
  (-> (CounterMetricFamily.
        "blaze_db_cache_loads_total"
        "Returns the total number of times that Cache lookup methods attempted to load new values."
        ["name"])
      (.addMetric [name] (.loadCount ^CacheStats stats))))


(defn- load-failures-total [name stats]
  (-> (CounterMetricFamily.
        "blaze_db_cache_load_failures_total"
        "Returns the number of times Cache lookup methods failed to load a new value, either because no value was found or an exception was thrown while loading."
        ["name"])
      (.addMetric [name] (.loadFailureCount ^CacheStats stats))))


(defn- load-seconds-total [name stats]
  (-> (CounterMetricFamily.
        "blaze_db_cache_load_seconds_total"
        "Returns the total number of seconds the cache has spent loading new values."
        ["name"])
      (.addMetric [name] (/ (double (.totalLoadTime ^CacheStats stats)) 1e9))))


(defn- load-evictions-total [name stats]
  (-> (CounterMetricFamily.
        "blaze_db_cache_evictions_total"
        "Returns the number of times an entry has been evicted."
        ["name"])
      (.addMetric [name] (.evictionCount ^CacheStats stats))))


(defn cache-collector
  "Creates a cache collector with `name` from `cache`."
  [name cache]
  (proxy [Collector] []
    (collect []
      (let [stats (.stats (.synchronous ^AsyncCache cache))]
        [(hits-total name stats)
         (loads-total name stats)
         (load-failures-total name stats)
         (load-seconds-total name stats)
         (load-evictions-total name stats)]))))
