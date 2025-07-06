(ns blaze.db.impl.index.multi-version-id-spec
  (:require
   [blaze.byte-string :as bs]
   [blaze.db.impl.index :as-alias index]
   [blaze.db.impl.index.multi-version-id :as ihps]
   [blaze.db.impl.index.multi-version-id.spec]
   [blaze.db.impl.index.single-version-id.spec]
   [blaze.fhir.hash.spec]
   [clojure.spec.alpha :as s]))

(s/fdef ihps/id
  :args (s/cat :multi-version-id ::index/multi-version-id)
  :ret bs/byte-string?)

(s/fdef ihps/matches-hash?
  :args (s/cat :multi-version-id ::index/multi-version-id
               :hash :blaze.resource/hash)
  :ret boolean?)

(s/fdef ihps/conj
  :args (s/cat :multi-version-id ::index/multi-version-id
               :single-version-id ::index/single-version-id)
  :ret ::index/multi-version-id)
