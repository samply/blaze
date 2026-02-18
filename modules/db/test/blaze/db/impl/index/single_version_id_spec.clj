(ns blaze.db.impl.index.single-version-id-spec
  (:require
   [blaze.byte-string :as bs]
   [blaze.db.impl.index :as-alias index]
   [blaze.db.impl.index.single-version-id :as svi]
   [blaze.db.impl.index.single-version-id.spec]
   [blaze.db.spec]
   [blaze.fhir.hash.spec]
   [clojure.spec.alpha :as s]))

(s/fdef svi/single-version-id
  :args (s/cat :id bs/byte-string? :hash :blaze.resource/hash)
  :ret ::index/single-version-id)

(s/fdef svi/from-resource-handle
  :args (s/cat :resource-handle :blaze.db/resource-handle)
  :ret ::index/single-version-id)

(s/fdef svi/id
  :args (s/cat :single-version-id ::index/single-version-id)
  :ret bs/byte-string?)

(s/fdef svi/hash-prefix
  :args (s/cat :single-version-id ::index/single-version-id)
  :ret int?)

(s/fdef svi/matches-hash?
  :args (s/cat :single-version-id ::index/single-version-id :hash :blaze.resource/hash)
  :ret boolean?)
