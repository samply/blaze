(ns blaze.db.resource-cache
  "A cache for resource contents.

  Caffeine is used because it have better performance characteristics as a
  ConcurrentHashMap."
  (:refer-clojure :exclude [get])
  (:require
   [blaze.cache-collector.protocols :as ccp]
   [blaze.db.resource-cache.protocol :as p]
   [blaze.db.resource-cache.spec]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store.spec]
   [blaze.module :as m]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [taoensso.timbre :as log])
  (:import
   [com.github.benmanes.caffeine.cache
    AsyncCacheLoader AsyncLoadingCache Caffeine]
   [com.github.benmanes.caffeine.cache.stats CacheStats]))

(set! *warn-on-reflection* true)

(defn get
  "Returns a CompletableFuture that will complete with the resource content of
  the resource with `key` or will complete with nil if it was not found.

  The key is a tuple of `type`, `hash` and `variant`."
  [cache key]
  (p/-get cache key))

(defn multi-get
  "Returns a CompletableFuture that will complete with a map from `key` to the
  resource content of all found `keys`.

  The key is a tuple of `type`, `hash` and `variant`."
  [cache keys]
  (p/-multi-get cache keys))

(deftype DefaultResourceCache [^AsyncLoadingCache cache resource-store]
  p/ResourceCache
  (-get [_ key]
    (.get cache key))

  (-multi-get [_ keys]
    (.getAll cache keys))

  ccp/StatsCache
  (-stats [_]
    (.stats (.synchronous cache)))
  (-estimated-size [_]
    (.estimatedSize (.synchronous cache))))

(defn invalidate-all! [resource-cache]
  (-> (.synchronous ^AsyncLoadingCache (.cache ^DefaultResourceCache resource-cache))
      (.invalidateAll)))

(defmethod m/pre-init-spec :blaze.db/resource-cache [_]
  (s/keys :req-un [:blaze.db/resource-store] :opt-un [::max-size]))

(defmethod ig/init-key :blaze.db/resource-cache
  [_ {:keys [resource-store max-size] :or {max-size 0}}]
  (log/info "Create resource cache with a size of" max-size "resources")
  (if (zero? max-size)
    (reify
      p/ResourceCache
      (-get [_ key]
        (rs/get resource-store key))

      (-multi-get [_ keys]
        (rs/multi-get resource-store keys))

      ccp/StatsCache
      (-stats [_]
        (CacheStats/empty))
      (-estimated-size [_]
        0))

    (->DefaultResourceCache
     (-> (Caffeine/newBuilder)
         (.maximumSize max-size)
         (.recordStats)
         (.buildAsync
          (reify AsyncCacheLoader
            (asyncLoad [_ key _]
              (rs/get resource-store key))

            (asyncLoadAll [_ keys _]
              (rs/multi-get resource-store keys)))))
     resource-store)))
