(ns blaze.db.impl.index.single-version-id-spec
  (:require
   [blaze.db.impl.index :as-alias index]
   [blaze.db.impl.index.single-version-id :as svi]
   [blaze.db.impl.index.single-version-id.spec]
   [blaze.db.spec]
   [blaze.fhir.hash.spec]
   [clojure.spec.alpha :as s]))

(s/fdef svi/single-version-id
  :args (s/cat :id :blaze.resource/id :hash :blaze.resource/hash)
  :ret ::index/single-version-id)

(s/fdef svi/from-resource-handle
  :args (s/cat :resource-handle :blaze.db/resource-handle)
  :ret ::index/single-version-id)

(s/fdef svi/id
  :args (s/cat :single-version-id ::index/single-version-id)
  :ret :blaze.resource/id)

(s/fdef svi/hash-prefix
  :args (s/cat :single-version-id ::index/single-version-id)
  :ret int?)

(s/fdef svi/matches-hash?
  :args (s/cat :single-version-id ::index/single-version-id
               :resource-handle :blaze.db/resource-handle)
  :ret boolean?)
