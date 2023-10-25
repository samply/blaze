(ns blaze.elm.expression.cache
  "Expression cache API."
  (:refer-clojure :exclude [get list])
  (:require
    [blaze.async.comp :as ac]
    [blaze.cache-collector.protocols :as ccp]
    [blaze.db.impl.iterators :as i]
    [blaze.db.impl.macros :refer [with-open-coll]]
    [blaze.db.kv :as kv]
    [blaze.db.spec]
    [blaze.elm.expression :as expr]
    [blaze.elm.expression.cache.bloom-filter :as bloom-filter]
    [blaze.elm.expression.cache.codec :as codec]
    [blaze.elm.expression.cache.protocols :as p]
    [blaze.elm.expression.cache.spec]
    [blaze.elm.expression.spec]
    [blaze.executors :as ex]
    [blaze.module :refer [reg-collector]]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [java-time.api :as time]
    [prometheus.alpha :refer [defcounter]]
    [taoensso.timbre :as log])
  (:import
    [com.github.benmanes.caffeine.cache
     AsyncCache AsyncCacheLoader AsyncLoadingCache Caffeine Weigher]
    [java.util.concurrent TimeUnit]))


(set! *warn-on-reflection* true)


(defcounter bloom-filter-useful-total
  "Number of times Bloom filter has avoided expression evaluation."
  {:namespace "blaze"
   :subsystem "cql_expr_cache"})


(defcounter bloom-filter-not-useful-total
  "Number of times Bloom filter has not avoided expression evaluation."
  {:namespace "blaze"
   :subsystem "cql_expr_cache"})


(defcounter bloom-filter-false-positive-total
  "Number of false positives reported by Bloom all filters."
  {:namespace "blaze"
   :subsystem "cql_expr_cache"})


(defn get
  "Returns the Bloom filter of `expression` from `cache` or nil if it isn't
  available yet."
  [cache expression]
  (p/-get cache expression))


(defn list
  "Returns a reducible collection of all Bloom filters in `cache`."
  [cache]
  (p/-list cache))


(defn total
  "Returns the number of all Bloom filters in `cache`."
  [cache]
  (p/-total cache))


(def ^:private weigher
  (reify Weigher
    (weigh [_ _ bloom-filter]
      (::bloom-filter/mem-size bloom-filter))))


(defn- load-bloom-filter [kv-store expression]
  (some-> (kv/get kv-store :cql-bloom-filter (codec/encode-key expression))
          codec/decode-value))


(defn- store-bloom-filter! [kv-store bloom-filter expression]
  (kv/put! kv-store [(codec/index-entry bloom-filter expression)]))


(defn- mem-cache ^AsyncLoadingCache
  [{:keys [kv-store] :as node} executor max-size-in-mb refresh]
  (-> (Caffeine/newBuilder)
      (.weigher weigher)
      (.maximumWeight (bit-shift-left max-size-in-mb 20))
      (.refreshAfterWrite refresh)
      (.executor executor)
      (.recordStats)
      (.buildAsync
        (reify AsyncCacheLoader
          (asyncLoad [_ expression executor]
            (if-let [bloom-filter (load-bloom-filter kv-store expression)]
              (ac/completed-future bloom-filter)
              (ac/supply-async
                #(let [bloom-filter (bloom-filter/create node expression)]
                   (store-bloom-filter! kv-store bloom-filter expression)
                   bloom-filter)
                executor)))
          (asyncReload [_ expression old-bloom-filter executor]
            (ac/supply-async
              #(let [bloom-filter (bloom-filter/recreate node old-bloom-filter
                                                         expression)]
                 (store-bloom-filter! kv-store bloom-filter expression)
                 bloom-filter)
              executor))))))


(defrecord Cache [^AsyncLoadingCache mem-cache kv-store]
  p/Cache
  (-get [_ expression]
    (let [future (.get mem-cache expression)]
      (when (.isDone future)
        (.get future))))
  (-list [_]
    (with-open-coll [snapshot (kv/new-snapshot kv-store)
                     iter (kv/new-iterator snapshot :cql-bloom-filter)]
      (i/kvs! iter codec/decoder)))
  (-total [_])
  ccp/StatsCache
  (-stats [_]
    (.stats (.synchronous mem-cache)))
  (-estimated-size [_]
    (.estimatedSize (.synchronous mem-cache))))


(defmethod ig/pre-init-spec ::expr/cache [_]
  (s/keys :req-un [:blaze.db/node ::executor] :opt-un [::max-size-in-mb ::refresh]))


(defmethod ig/init-key ::expr/cache
  [_
   {:keys [executor max-size-in-mb refresh] {:keys [kv-store] :as node} :node
    :or {max-size-in-mb 100 refresh (time/hours 24)}}]
  (log/info "Create CQL expression cache with a memory size of" max-size-in-mb "MiB and a refresh duration of" refresh)
  (->Cache (mem-cache node executor max-size-in-mb refresh) kv-store))


(defmethod ig/halt-key! ::expr/cache
  [_ {:keys [mem-cache]}]
  (log/info "Stopping CQL expression cache...")
  (.cleanUp (.synchronous ^AsyncCache mem-cache)))


(defmethod ig/pre-init-spec ::executor [_]
  (s/keys :opt-un [::num-threads]))


(defn- executor-init-msg [num-threads]
  (format "Init CQL expression cache executor with %d threads" num-threads))


(defmethod ig/init-key ::executor
  [_ {:keys [num-threads] :or {num-threads 4}}]
  (log/info (executor-init-msg num-threads))
  (ex/io-pool num-threads "cql-expr-cache-%d"))


(defmethod ig/halt-key! ::executor
  [_ executor]
  (log/info "Stopping CQL expression cache executor...")
  (ex/shutdown! executor)
  (if (ex/await-termination executor 10 TimeUnit/SECONDS)
    (log/info "CQL expression cache executor was stopped successfully")
    (log/warn "Got timeout while stopping the CQL expression cache executor")))


(derive ::executor :blaze.metrics/thread-pool-executor)


(reg-collector ::bloom-filter-creation-duration-seconds
  bloom-filter/bloom-filter-creation-duration-seconds)


(reg-collector ::bloom-filter-useful-total
  bloom-filter-useful-total)


(reg-collector ::bloom-filter-not-useful-total
  bloom-filter-not-useful-total)


(reg-collector ::bloom-filter-false-positive-total
  bloom-filter-false-positive-total)


(reg-collector ::bloom-filter-bytes
  bloom-filter/bloom-filter-bytes)
