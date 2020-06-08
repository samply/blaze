(ns blaze.db.resource-cache
  "A cache for resource contents.

  Caffeine is used because it have better performance characteristics as a
  ConcurrentHashMap."
  (:require
    [blaze.db.impl.index :as index]
    [blaze.db.impl.metrics :as metrics]
    [blaze.db.impl.protocols :as p]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [com.github.benmanes.caffeine.cache CacheLoader Caffeine LoadingCache]))


(set! *warn-on-reflection* true)


(extend-protocol p/ResourceContentLookup
  LoadingCache
  (-get-content [cache hash]
    (.get cache hash)))


(defn new-resource-cache
  "Creates a new resource cache with implements the `ResourceContentLookup`
  protocol."
  [kv-store max-size]
  (-> (Caffeine/newBuilder)
      (.maximumSize max-size)
      (.recordStats)
      (.build
        (reify CacheLoader
          (load [_ hash]
            (index/load-resource-content kv-store hash))))))


(s/def ::max-size
  nat-int?)


(defmethod ig/pre-init-spec :blaze.db/resource-cache [_]
  (s/keys :req-un [:blaze.db/kv-store] :opt-un [::max-size]))


(defmethod ig/init-key :blaze.db/resource-cache
  [_ {:keys [kv-store max-size] :or {max-size 0}}]
  (log/info "Create resource cache with a size of" max-size "resources")
  (new-resource-cache kv-store max-size))


(defmethod ig/init-key ::collector
  [_ {:keys [cache]}]
  (metrics/cache-collector "resource-cache" cache))


(derive ::collector :blaze.metrics/collector)
