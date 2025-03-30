(ns blaze.db.resource-store-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.async.comp-spec]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store.spec]
   [blaze.fhir.spec]
   [clojure.spec.alpha :as s]))

(s/fdef rs/get
  :args (s/cat :store :blaze.db/resource-store
               :hash :blaze.resource/hash
               :variant :blaze.resource/variant)
  :ret ac/completable-future?)

(s/fdef rs/multi-get
  :args (s/cat :store :blaze.db/resource-store
               :hashes (s/coll-of :blaze.resource/hash)
               :variant :blaze.resource/variant)
  :ret ac/completable-future?)

(s/fdef rs/put!
  :args (s/cat :store :blaze.db/resource-store
               :entries (s/map-of :blaze.resource/hash :blaze/resource))
  :ret ac/completable-future?)
