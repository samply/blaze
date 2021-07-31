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


(defn- add-hit-count! [^CounterMetricFamily family name stats]
  (.addMetric family [name] (.hitCount ^CacheStats stats)))


(defn- add-hit-counts! [family stats]
  (doseq [[name stats] stats]
    (add-hit-count! family name stats))
  family)


(defn- hits-total [stats]
  (-> (CounterMetricFamily.
        "blaze_db_cache_hits_total"
        "Returns the number of times Cache lookup methods have returned a cached value."
        ["name"])
      (add-hit-counts! stats)))


(defn- add-load-count! [^CounterMetricFamily family name stats]
  (.addMetric family [name] (.loadCount ^CacheStats stats)))


(defn- add-load-counts! [family stats]
  (doseq [[name stats] stats]
    (add-load-count! family name stats))
  family)


(defn- loads-total [stats]
  (-> (CounterMetricFamily.
        "blaze_db_cache_loads_total"
        "Returns the total number of times that Cache lookup methods attempted to load new values."
        ["name"])
      (add-load-counts! stats)))


(defn- add-load-failure-count! [^CounterMetricFamily family name stats]
  (.addMetric family [name] (.loadFailureCount ^CacheStats stats)))


(defn- add-load-failure-counts! [family stats]
  (doseq [[name stats] stats]
    (add-load-failure-count! family name stats))
  family)


(defn- load-failures-total [stats]
  (-> (CounterMetricFamily.
        "blaze_db_cache_load_failures_total"
        "Returns the number of times Cache lookup methods failed to load a new value, either because no value was found or an exception was thrown while loading."
        ["name"])
      (add-load-failure-counts! stats)))


(defn- add-load-time! [^CounterMetricFamily family name stats]
  (.addMetric family [name] (/ (double (.totalLoadTime ^CacheStats stats)) 1e9)))


(defn- add-load-times! [family stats]
  (doseq [[name stats] stats]
    (add-load-time! family name stats))
  family)


(defn- add-eviction-count! [^CounterMetricFamily family name stats]
  (.addMetric family [name] (.evictionCount ^CacheStats stats)))


(defn- add-eviction-counts! [family stats]
  (doseq [[name stats] stats]
    (add-eviction-count! family name stats))
  family)


(defn- load-seconds-total [stats]
  (-> (CounterMetricFamily.
        "blaze_db_cache_load_seconds_total"
        "Returns the total number of seconds the cache has spent loading new values."
        ["name"])
      (add-load-times! stats)))


(defn- load-evictions-total [stats]
  (-> (CounterMetricFamily.
        "blaze_db_cache_evictions_total"
        "Returns the number of times an entry has been evicted."
        ["name"])
      (add-eviction-counts! stats)))


(def ^:private mapper
  (map (fn [[name cache]] [name (p/-stats cache)])))


(defmethod ig/pre-init-spec :blaze.db/cache-collector [_]
  (s/keys :req-un [::caches]))


(defmethod ig/init-key :blaze.db/cache-collector
  [_ {:keys [caches]}]
  (proxy [Collector] []
    (collect []
      (let [stats (into [] mapper caches)]
        [(hits-total stats)
         (loads-total stats)
         (load-failures-total stats)
         (load-seconds-total stats)
         (load-evictions-total stats)]))))


(derive :blaze.db/cache-collector :blaze.metrics/collector)
