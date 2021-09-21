(ns blaze.dev.db
  (:require
    [blaze.core :refer [system]])
  (:import
    [com.github.benmanes.caffeine.cache Cache]))


(def ^Cache resource-cache
  (.synchronous (.-cache (:blaze.db/resource-cache system))))


(def ^Cache resource-handle-cache
  (:blaze.db/resource-handle-cache system))


(def ^Cache tx-cache
  (:blaze.db/tx-cache system))


(def node
  (:blaze.db/node system))


(comment
  (.estimatedSize resource-cache)
  (.invalidateAll resource-cache)

  (.estimatedSize resource-handle-cache)
  (.invalidateAll resource-handle-cache)

  (.estimatedSize tx-cache)
  (.invalidateAll tx-cache)
  )
