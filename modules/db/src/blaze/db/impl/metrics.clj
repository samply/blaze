(ns blaze.db.impl.metrics
  (:import
    [com.github.benmanes.caffeine.cache Cache]
    [com.github.benmanes.caffeine.cache.stats CacheStats]
    [io.prometheus.client Collector CounterMetricFamily]))


(set! *warn-on-reflection* true)


(defn cache-collector
  "Creates a cache collector with `name` from `cache`."
  [name ^Cache cache]
  (proxy [Collector] []
    (collect []
      (let [^CacheStats stats (.stats cache)]
        [(let [mf (CounterMetricFamily.
                    "blaze_db_cache_hit_count"
                    "Returns the number of times Cache lookup methods have returned a cached value."
                    ["name"])]
           (.addMetric mf [name] (.hitCount stats))
           mf)
         (let [mf (CounterMetricFamily.
                    "blaze_db_cache_load_count"
                    "Returns the total number of times that Cache lookup methods attempted to load new values."
                    ["name"])]
           (.addMetric mf [name] (.loadCount stats))
           mf)
         (let [mf (CounterMetricFamily.
                    "blaze_db_cache_load_failure_count"
                    "Returns the number of times Cache lookup methods failed to load a new value, either because no value was found or an exception was thrown while loading."
                    ["name"])]
           (.addMetric mf [name] (.loadFailureCount stats))
           mf)
         (let [mf (CounterMetricFamily.
                    "blaze_db_cache_load_seconds_total"
                    "Returns the total number of nanoseconds the cache has spent loading new values."
                    ["name"])]
           (.addMetric mf [name] (/ (double (.totalLoadTime stats)) 1e9))
           mf)
         (let [mf (CounterMetricFamily.
                    "blaze_db_cache_eviction_count"
                    "Returns the number of times an entry has been evicted."
                    ["name"])]
           (.addMetric mf [name] (.evictionCount stats))
           mf)]))))
