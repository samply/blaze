(ns blaze.db.resource-cache
  "A cache for resource contents.

  Caffeine is used because it have better performance characteristics as a
  ConcurrentHashMap."
  (:require
   [blaze.cache-collector.protocols :as ccp]
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
   [java.util.concurrent ForkJoinPool]))

(set! *warn-on-reflection* true)

(deftype ResourceCache [^AsyncLoadingCache cache resource-store put-executor]
  rs/ResourceStore
  (-get [_ key]
    (.get cache key))

  (-multi-get [_ keys]
    (.getAll cache keys))

  (-put [_ entries]
    (rs/put! resource-store entries))

  ccp/StatsCache
  (-stats [_]
    (.stats (.synchronous cache)))
  (-estimated-size [_]
    (.estimatedSize (.synchronous cache))))

(defn invalidate-all! [resource-cache]
  (-> (.synchronous ^AsyncLoadingCache (.cache ^ResourceCache resource-cache))
      (.invalidateAll)))

(defmethod m/pre-init-spec :blaze.db/resource-cache [_]
  (s/keys :req-un [:blaze.db/resource-store] :opt-un [::max-size]))

(defmethod ig/init-key :blaze.db/resource-cache
  [_ {:keys [resource-store max-size] :or {max-size 0}}]
  (log/info "Create resource cache with a size of" max-size "resources")
  (->ResourceCache
   (-> (Caffeine/newBuilder)
       (.maximumSize max-size)
       (.recordStats)
       (.buildAsync
        (reify AsyncCacheLoader
          (asyncLoad [_ key _]
            (rs/get resource-store key))

          (asyncLoadAll [_ keys _]
            (rs/multi-get resource-store keys)))))
   resource-store
   (ForkJoinPool/commonPool)))
