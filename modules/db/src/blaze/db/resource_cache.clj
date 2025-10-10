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
   [blaze.fhir.spec.type Base]
   [com.github.benmanes.caffeine.cache
    AsyncCacheLoader AsyncLoadingCache Caffeine Weigher]
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

(def ^:private weigher
  (reify Weigher
    (weigh [_ _ resource]
      (Base/memSize resource))))

(defmethod m/pre-init-spec :blaze.db/resource-cache [_]
  (s/keys :req-un [:blaze.db/resource-store] :opt-un [::max-size-in-mb]))

(defmethod ig/init-key :blaze.db/resource-cache
  [_ {:keys [resource-store max-size-in-mb] :or {max-size-in-mb 100}}]
  (log/info "Create resource cache with a memory size of" max-size-in-mb "MiB")
  (->ResourceCache
   (-> (Caffeine/newBuilder)
       (.weigher weigher)
       (.maximumWeight (bit-shift-left max-size-in-mb 20))
       (.recordStats)
       (.buildAsync
        (reify AsyncCacheLoader
          (asyncLoad [_ key _]
            (rs/get resource-store key))

          (asyncLoadAll [_ keys _]
            (rs/multi-get resource-store keys)))))
   resource-store
   (ForkJoinPool/commonPool)))
