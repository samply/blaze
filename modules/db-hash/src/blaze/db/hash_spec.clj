(ns blaze.db.hash-spec
  (:require
    [blaze.db.hash :as hash]
    [blaze.db.hash.spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(s/fdef hash/generate
  :args (s/cat :resource :blaze/resource)
  :ret :blaze.db.resource/hash)


(s/fdef hash/encode
  :args (s/cat :hash :blaze.db.resource/hash)
  :ret bytes?)


(s/fdef hash/decode
  :args (s/cat :bytes (s/and bytes? #(= 32 (alength %))))
  :ret :blaze.db.resource/hash)
