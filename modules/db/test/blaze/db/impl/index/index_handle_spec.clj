(ns blaze.db.impl.index.index-handle-spec
  (:require
   [blaze.byte-string :as bs]
   [blaze.db.impl.index :as-alias index]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.index-handle.spec]
   [blaze.db.impl.index.single-version-id.spec]
   [blaze.fhir.hash.spec]
   [clojure.spec.alpha :as s]))

(s/fdef ih/from-single-version-id
  :args (s/cat :single-version-id ::index/single-version-id)
  :ret ::index/handle)

(s/fdef ih/from-resource-handle
  :args (s/cat :resource-handle :blaze.db/resource-handle)
  :ret ::index/handle)

(s/fdef ih/id
  :args (s/cat :index-handle ::index/handle)
  :ret bs/byte-string?)

(s/fdef ih/hash-prefixes
  :args (s/cat :index-handle ::index/handle)
  :ret (s/coll-of int?))

(s/fdef ih/matches-hash?
  :args (s/cat :index-handle ::index/handle :hash :blaze.resource/hash)
  :ret boolean?)

(s/fdef ih/conj
  :args (s/cat :index-handle ::index/handle
               :single-version-id ::index/single-version-id)
  :ret ::index/handle)

(s/fdef ih/intersection
  :args (s/cat :index-handle-1 ::index/handle :index-handle-2 ::index/handle)
  :ret ::index/handle)

(s/fdef ih/union
  :args (s/cat :index-handle-1 ::index/handle :index-handle-2 ::index/handle)
  :ret ::index/handle)
