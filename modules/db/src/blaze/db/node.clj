(ns blaze.db.node
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.db.api :as d]
    [blaze.db.impl.batch-db :as batch-db]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.db :as db]
    [blaze.db.impl.index :as index]
    [blaze.db.impl.protocols :as p]
    [blaze.db.indexer :as indexer]
    [blaze.db.search-param-registry :as sr]
    [blaze.db.tx-log :as tx-log]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [manifold.deferred :as md]
    [taoensso.timbre :as log])
  (:import
    [com.github.benmanes.caffeine.cache Cache CacheLoader Caffeine]
    [com.github.benmanes.caffeine.cache.stats CacheStats]
    [io.prometheus.client Collector CounterMetricFamily]))


(set! *warn-on-reflection* true)


(defn- resolve-search-param [search-param-registry type code]
  (if-let [search-param (sr/get search-param-registry code type)]
    search-param
    {::anom/category ::anom/not-found
     ::anom/message (format "search-param with code `%s` and type `%s` not found" code type)}))


(defn- resolve-search-params [search-param-registry type clauses]
  (reduce
    (fn [ret [code & values]]
      (let [res (resolve-search-param search-param-registry type code)]
        (if (::anom/category res)
          (reduced res)
          (conj ret [res values]))))
    []
    clauses))


(deftype Node [tx-log tx-indexer store resource-cache search-param-registry]
  p/Node
  (-db [this]
    (db/db store resource-cache this (indexer/last-t tx-indexer)))

  (-sync [this t]
    (-> (indexer/tx-result tx-indexer t)
        (md/chain' (fn [_] (d/db this)))))

  (-submit-tx [this tx-ops]
    (-> (tx-log/submit tx-log tx-ops)
        (md/chain' #(indexer/tx-result tx-indexer %))
        (md/chain' #(db/db store resource-cache this %))))

  p/QueryCompiler
  (-compile-type-query [_ type clauses]
    (when-ok [clauses (resolve-search-params search-param-registry type clauses)]
      (batch-db/->TypeQuery (codec/tid type) clauses)))

  (-compile-compartment-query [_ code type clauses]
    (when-ok [clauses (resolve-search-params search-param-registry type clauses)]
      (batch-db/->CompartmentQuery (codec/c-hash code) (codec/tid type) clauses))))


(defn resource-cache [kv-store max-size]
  (-> (Caffeine/newBuilder)
      (.maximumSize max-size)
      (.recordStats)
      (.build
        (reify CacheLoader
          (load [_ hash]
            (index/load-resource-content kv-store hash))))))


(defn init-node [tx-log tx-indexer store resource-cache search-param-registry]
  (->Node tx-log tx-indexer store resource-cache search-param-registry))


(defn- cache-collector [name ^Cache cache]
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


(defmethod ig/init-key :blaze.db/node
  [_ {:keys [tx-log tx-indexer kv-store resource-cache search-param-registry]}]
  (log/info "Open database node")
  (init-node tx-log tx-indexer kv-store resource-cache search-param-registry))


(defmethod ig/init-key :blaze.db/resource-cache
  [_ {:keys [kv-store max-size]}]
  (log/info "Create resource cache with a size of" max-size "resources")
  (resource-cache kv-store max-size))


(defmethod ig/init-key ::resource-cache-collector
  [_ {:keys [cache]}]
  (cache-collector "resource-cache" cache))


(derive ::resource-cache-collector :blaze.metrics/collector)
