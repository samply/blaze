(ns blaze.fhir.hash.spec
  (:require
    [blaze.byte-string :as bs :refer [byte-string?]]
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]))


(s/def :blaze.resource/hash
  (s/with-gen
    (s/and byte-string? #(= 32 (bs/size %)))
    #(gen/fmap
       (comp bs/from-byte-array byte-array)
       (gen/vector (gen/fmap byte (gen/choose Byte/MIN_VALUE Byte/MAX_VALUE)) 32))))
