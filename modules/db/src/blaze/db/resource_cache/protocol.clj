(ns blaze.db.resource-cache.protocol)

(defprotocol ResourceCache
  (-get [cache key])

  (-multi-get [cache key])

  (-multi-get-skip-cache-insertion [cache key]))
