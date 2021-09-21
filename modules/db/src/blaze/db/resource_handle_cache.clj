(ns blaze.db.resource-handle-cache
  "A cache for resource handles.

  Caffeine is used because it have better performance characteristics as a
  ConcurrentHashMap."
  (:require
    [blaze.db.resource-handle-cache.spec]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [com.github.benmanes.caffeine.cache Caffeine]))


(set! *warn-on-reflection* true)


(defmethod ig/pre-init-spec :blaze.db/resource-handle-cache [_]
  (s/keys :opt-un [::max-size]))


(defmethod ig/init-key :blaze.db/resource-handle-cache
  [_ {:keys [max-size] :or {max-size 0}}]
  (log/info "Create resource handle cache with a size of" max-size
            "resource handles")
  (-> (Caffeine/newBuilder)
      (.maximumSize max-size)
      (.recordStats)
      (.build)))
