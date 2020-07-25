(ns blaze.db.resource-cache-spec
  (:require
    [blaze.async-comp-spec]
    [blaze.db.resource-cache :as resource-cache]
    [blaze.db.resource-cache.spec]
    [blaze.db.resource-store.spec]
    [blaze.db.spec]
    [clojure.spec.alpha :as s]))


(s/fdef resource-cache/new-resource-cache
  :args (s/cat :resource-store :blaze.db/resource-store
               :max-size :blaze.db.resource-cache/max-size)
  :ret :blaze.db/resource-cache)
