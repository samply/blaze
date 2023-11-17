(ns blaze.fhir.hash.spec
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.fhir.hash :as hash]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as sg]))

(s/def :blaze.resource/hash
  (s/with-gen
    hash/hash?
    #(sg/fmap
      (comp hash/from-byte-buffer! bb/wrap byte-array)
      (sg/vector (sg/fmap byte (sg/choose Byte/MIN_VALUE Byte/MAX_VALUE)) 32))))
