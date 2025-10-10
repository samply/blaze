(ns blaze.db.resource-cache.spec
  (:require
   [blaze.db.resource-cache.protocol :as p]
   [clojure.spec.alpha :as s]))

(s/def :blaze.db/resource-cache
  #(satisfies? p/ResourceCache %))

(s/def :blaze.db.resource-cache/max-size-ratio
  (s/or :int int? :double double?))
