(ns blaze.db.cache-collector
  (:require
    [blaze.db.cache-collector.protocols :as p]
    [blaze.db.cache-collector.spec]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig])
  (:import
    [com.github.benmanes.caffeine.cache Cache]
    [com.github.benmanes.caffeine.cache.stats CacheStats]
    [io.prometheus.client Collector CounterMetricFamily]))


(set! *warn-on-reflection* true)


(extend-protocol p/StatsCache
  Cache
  (-stats [cache]
    (.stats cache)))


(defn- counter-metric-family [name help f stats]
  (let [mf (CounterMetricFamily. ^String name ^String help ["name"])]
    (run! (fn [[name stats]] (.addMetric mf [name] (f stats))) stats)
    mf))


(def ^:private mapper
  (map (fn [[name cache]] [name (p/-stats cache)])))


(defmethod ig/pre-init-spec :blaze.db/cache-collector [_]
  (s/keys :req-un [::caches]))


(defmethod ig/init-key :blaze.db/cache-collector
  [_ {:keys [caches]}]
  (proxy [Collector] []
    (collect []
      (let [stats (into [] mapper caches)]
        [(counter-metric-family
           "blaze_db_cache_hits_total"
           "Returns the number of times Cache lookup methods have returned a cached value."
           #(.hitCount ^CacheStats %)
           stats)
         (counter-metric-family
           "blaze_db_cache_loads_total"
           "Returns the total number of times that Cache lookup methods attempted to load new values."
           #(.loadCount ^CacheStats %)
           stats)
         (counter-metric-family
           "blaze_db_cache_load_failures_total"
           "Returns the number of times Cache lookup methods failed to load a new value, either because no value was found or an exception was thrown while loading."
           #(.loadFailureCount ^CacheStats %)
           stats)
         (counter-metric-family
           "blaze_db_cache_load_seconds_total"
           "Returns the total number of seconds the cache has spent loading new values."
           #(/ (double (.totalLoadTime ^CacheStats %)) 1e9)
           stats)
         (counter-metric-family
           "blaze_db_cache_evictions_total"
           "Returns the number of times an entry has been evicted."
           #(.evictionCount ^CacheStats %)
           stats)]))))


(derive :blaze.db/cache-collector :blaze.metrics/collector)
