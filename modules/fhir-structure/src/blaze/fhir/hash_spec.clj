(ns blaze.fhir.hash-spec
  (:require
    [blaze.fhir.hash :as hash]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(s/fdef hash/generate
  :args (s/cat :resource :blaze/resource)
  :ret :blaze.resource/hash)


(s/fdef hash/encode
  :args (s/cat :hash :blaze.resource/hash)
  :ret bytes?)


(s/fdef hash/decode
  :args (s/cat :bytes (s/and bytes? #(= 32 (alength %))))
  :ret :blaze.resource/hash)
