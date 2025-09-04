(ns blaze.dev.db
  (:require
   [blaze.core :refer [system]])
  (:import
   [com.github.benmanes.caffeine.cache Cache]))

(defn resource-cache ^Cache []
  (.synchronous (.-cache (:blaze.db/resource-cache system))))

(defn tx-cache ^Cache []
  (:blaze.db/tx-cache system))

(defn node []
  (:blaze.db/node system))

(comment
  (.estimatedSize (resource-cache))
  (.invalidateAll (resource-cache))

  (.estimatedSize (tx-cache))
  (.invalidateAll (tx-cache)))
