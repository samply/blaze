(ns blaze.db.resource-cache
  "A cache for resource contents.

  Caffeine is used because it have better performance characteristics as a
  ConcurrentHashMap."
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.cache-collector :as cc]
    [blaze.db.resource-cache.spec]
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store.spec]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [com.github.benmanes.caffeine.cache
     AsyncCache AsyncCacheLoader AsyncLoadingCache Caffeine]
    [java.util.concurrent ForkJoinPool]))


(set! *warn-on-reflection* true)


(defn- put-stored [^AsyncCache cache entries]
  (fn [_ e]
    (.putAll
      (.synchronous cache)
      (cond-> entries
        e
        (select-keys (:successfully-stored-hashes (ex-data e)))))))


(deftype ResourceCache [^AsyncLoadingCache cache resource-store put-executor]
  rs/ResourceLookup
  (-get [_ hash]
    (.get cache hash))

  (-multi-get [_ hashes]
    (.getAll cache hashes))

  rs/ResourceStore
  (-put [_ entries]
    (-> (rs/put resource-store entries)
        (ac/when-complete-async (put-stored cache entries) put-executor)))

  cc/StatsCache
  (-stats [_]
    (.stats (.synchronous cache))))


(defn invalidate-all! [resource-cache]
  (-> (.synchronous ^AsyncLoadingCache (.cache ^ResourceCache resource-cache))
      (.invalidateAll)))


(defn new-resource-cache
  "Creates a new resource cache with implements the `ResourceContentLookup`
  protocol."
  [resource-store max-size]
  (->ResourceCache
    (-> (Caffeine/newBuilder)
        (.maximumSize max-size)
        (.recordStats)
        (.buildAsync
          (reify AsyncCacheLoader
            (asyncLoad [_ hash _]
              (rs/get resource-store hash))

            (asyncLoadAll [_ hashes _]
              (rs/multi-get resource-store (vec hashes))))))
    resource-store
    (ForkJoinPool/commonPool)))


(defmethod ig/pre-init-spec :blaze.db/resource-cache [_]
  (s/keys :req-un [:blaze.db/resource-store] :opt-un [::max-size]))


(defmethod ig/init-key :blaze.db/resource-cache
  [_ {:keys [resource-store max-size] :or {max-size 0}}]
  (log/info "Create resource cache with a size of" max-size "resources")
  (new-resource-cache resource-store max-size))
