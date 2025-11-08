(ns blaze.db.resource-cache-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.async.comp-spec]
   [blaze.db.resource-cache :as rc]
   [blaze.db.resource-cache.spec]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store.spec]
   [clojure.spec.alpha :as s]))

(s/fdef rc/get
  :args (s/cat :store :blaze.db/resource-cache :key ::rs/key)
  :ret ac/completable-future?)

(s/fdef rc/multi-get
  :args (s/cat :store :blaze.db/resource-cache :keys (s/coll-of ::rs/key))
  :ret ac/completable-future?)
