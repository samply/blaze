(ns blaze.elm.expression.cache
  "Expression cache API."
  (:refer-clojure :exclude [get])
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.cache-collector.protocols :as ccp]
   [blaze.coll.core :refer [with-open-coll]]
   [blaze.db.impl.iterators :as i]
   [blaze.db.kv :as kv]
   [blaze.db.spec]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.expression :as expr]
   [blaze.elm.expression.cache.bloom-filter :as bloom-filter]
   [blaze.elm.expression.cache.codec :as codec]
   [blaze.elm.expression.cache.codec.by-t :as codec-by-t]
   [blaze.elm.expression.cache.codec.form :as form]
   [blaze.elm.expression.cache.protocols :as p]
   [blaze.elm.expression.cache.spec]
   [blaze.elm.expression.spec]
   [blaze.executors :as ex]
   [blaze.module :as m :refer [reg-collector]]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [java-time.api :as time]
   [prometheus.alpha :refer [defcounter]]
   [taoensso.timbre :as log])
  (:import
   [com.github.benmanes.caffeine.cache
    AsyncCache AsyncCacheLoader AsyncLoadingCache Caffeine Weigher]
   [com.google.common.hash HashCode]
   [java.lang AutoCloseable]
   [java.util.concurrent TimeUnit]))

(set! *warn-on-reflection* true)

(defcounter bloom-filter-useful-total
  "Number of times Bloom filter has avoided expression evaluation."
  {:namespace "blaze"
   :subsystem "cql_expr_cache"}
  "name")

(defcounter bloom-filter-not-useful-total
  "Number of times Bloom filter has not avoided expression evaluation."
  {:namespace "blaze"
   :subsystem "cql_expr_cache"}
  "name")

(defcounter bloom-filter-false-positive-total
  "Number of false positives reported by Bloom all filters."
  {:namespace "blaze"
   :subsystem "cql_expr_cache"}
  "name")

(defn get
  "Returns the Bloom filter of `expression` from `cache` or nil if it isn't
  available yet."
  [cache expression]
  (p/-get cache expression))

(defn get-disk
  "Returns the Bloom filter with `hash` from `cache` or an anomaly if the Bloom
  filter was not found."
  [cache hash]
  (p/-get-disk cache hash))

(defn delete-disk!
  "Deletes the Bloom filter with `hash` from `cache` or returns an anomaly if
  the Bloom filter was not found."
  [cache hash]
  (p/-delete-disk cache hash))

(defn list-by-t
  "Returns a reducible collection of all Bloom filters in `cache` ordered by t
  descending."
  [cache]
  (p/-list-by-t cache))

(defn total
  "Returns the total number of Bloom filters in `cache`."
  [cache]
  (p/-total cache))

(def ^:private weigher
  (reify Weigher
    (weigh [_ _ bloom-filter]
      (::bloom-filter/mem-size bloom-filter))))

(defn- load-bloom-filter [kv-store hash]
  (some->> (kv/get kv-store :cql-bloom-filter (.asBytes ^HashCode hash))
           (codec/decode-value hash)))

(defn- load-bloom-filter-from-expr [kv-store expression]
  (let [key (form/hash (pr-str (core/-form expression)))]
    (load-bloom-filter kv-store key)))

(defn- bloom-filter-creation-counter ^AutoCloseable [state]
  (swap! state update :num-running-bloom-filter-creations inc)
  (reify AutoCloseable
    (close [_]
      (swap! state update :num-running-bloom-filter-creations dec))))

(defn- mem-cache
  [state {:keys [kv-store] :as node} executor max-size-in-mb refresh]
  (-> (Caffeine/newBuilder)
      (.weigher weigher)
      (.maximumWeight (bit-shift-left max-size-in-mb 20))
      (.refreshAfterWrite refresh)
      (.executor executor)
      (.recordStats)
      (.buildAsync
       (reify AsyncCacheLoader
         (asyncLoad [_ expression executor]
           (if-let [bloom-filter (load-bloom-filter-from-expr kv-store expression)]
             (ac/completed-future bloom-filter)
             (ac/supply-async
              #(with-open [_ (bloom-filter-creation-counter state)]
                 (let [bloom-filter (bloom-filter/create node expression)]
                   (kv/write!
                    kv-store
                    [(codec/put-entry bloom-filter)
                     (codec-by-t/put-entry bloom-filter)])
                   bloom-filter))
              executor)))
         (asyncReload [_ expression old-bloom-filter executor]
           (ac/supply-async
            #(with-open [_ (bloom-filter-creation-counter state)]
               (let [bloom-filter (bloom-filter/recreate node old-bloom-filter
                                                         expression)]
                 (kv/write!
                  kv-store
                  [(codec/put-entry bloom-filter)
                   (codec-by-t/delete-entry old-bloom-filter)
                   (codec-by-t/put-entry bloom-filter)])
                 bloom-filter))
            executor))))))

(def ^:private ^:const ^long expression-size-limit
  "The limit of form size of cacheable expressions.

  Bigger expressions will not be cache to keep memory consumption under control.

  The current value is 640 kB."
  (bit-shift-left 10 16))

(def ^:private ^:const ^long concurrent-bloom-filter-creation-limit
  "Maximum number of concurrent Bloom filter creations allowed.

  This limit should prevent the over-saturation of Bloom filter creations."
  500)

(defn concurrent-bloom-filter-creation-limit-reached?
  [{:keys [num-running-bloom-filter-creations]}]
  (< concurrent-bloom-filter-creation-limit num-running-bloom-filter-creations))

(defn- overly-large? [expression]
  (< expression-size-limit (count (pr-str (core/-form expression)))))

(defn- not-found-anom [hash]
  (ba/not-found (format "The Bloom filter with hash `%s` was not found." hash)))

(defrecord Cache [state ^AsyncLoadingCache mem-cache node kv-store]
  p/Cache
  (-get [_ expression]
    (if (overly-large? expression)
      (log/warn "Skip caching overly large CQL expression.")
      (if-let [future (.getIfPresent mem-cache expression)]
        (when (.isDone future)
          (.get future))
        (if (concurrent-bloom-filter-creation-limit-reached? @state)
          (log/debug "Skip caching CQL expression because the concurrent Bloom filter creation limit of" concurrent-bloom-filter-creation-limit "is reached.")
          (let [future (.get mem-cache expression)]
            (when (.isDone future)
              (.get future)))))))

  (-get-disk [_ hash]
    (or (load-bloom-filter kv-store hash)
        (not-found-anom hash)))

  (-delete-disk [_ hash]
    (if-let [bloom-filter (load-bloom-filter kv-store hash)]
      (kv/write!
       kv-store
       [(codec/delete-entry bloom-filter)
        (codec-by-t/delete-entry bloom-filter)])
      (not-found-anom hash)))

  (-list-by-t [_]
    (with-open-coll [snapshot (kv/new-snapshot kv-store)]
      (i/entries snapshot :cql-bloom-filter-by-t (map codec-by-t/decoder))))

  (-total [_]
    (kv/estimate-num-keys kv-store :cql-bloom-filter))

  ccp/StatsCache
  (-stats [_]
    (.stats (.synchronous mem-cache)))

  (-estimated-size [_]
    (.estimatedSize (.synchronous mem-cache))))

(defmethod m/pre-init-spec ::expr/cache [_]
  (s/keys :req-un [:blaze.db/node ::executor] :opt-un [::max-size-in-mb ::refresh]))

(defmethod ig/init-key ::expr/cache
  [_
   {:keys [executor max-size-in-mb refresh] {:keys [kv-store] :as node} :node
    :or {max-size-in-mb 100 refresh (time/hours 24)}}]
  (log/info "Create CQL expression cache with a memory size of" max-size-in-mb "MiB and a refresh duration of" (str refresh))
  (let [state (atom {:num-running-bloom-filter-creations 0})]
    (->Cache state (mem-cache state node executor max-size-in-mb refresh) node kv-store)))

(defmethod ig/halt-key! ::expr/cache
  [_ {:keys [mem-cache]}]
  (log/info "Stopping CQL expression cache...")
  (.cleanUp (.synchronous ^AsyncCache mem-cache)))

(defmethod m/pre-init-spec ::executor [_]
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
