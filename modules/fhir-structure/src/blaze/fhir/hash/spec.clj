(ns blaze.fhir.hash.spec
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.fhir.hash :as hash]
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]))


(s/def :blaze.resource/hash
  (s/with-gen
    hash/hash?
    #(gen/fmap
       (comp hash/from-byte-buffer! bb/wrap byte-array)
       (gen/vector (gen/fmap byte (gen/choose Byte/MIN_VALUE Byte/MAX_VALUE)) 32))))
