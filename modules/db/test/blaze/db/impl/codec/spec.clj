(ns blaze.db.impl.codec.spec
  (:require
    [blaze.byte-string :as bs :refer [byte-string?]]
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
    [clojure.test.check.generators :as gen2]))


(s/def :blaze.db/tid
  (s/with-gen int? gen/int))


(s/def :blaze.db/spid
  (s/with-gen int? gen/int))


(s/def :blaze.db/hash-prefix
  (s/and byte-string? #(= 4 (bs/size %))))


(s/def :blaze.db/c-hash
  (s/with-gen int? gen/int))


(s/def :blaze.db/v-hash
  (s/with-gen int? gen/int))


(s/def :blaze.db/unit-hash
  (s/with-gen int? gen/int))


;; A database resource id is a long value were the first 5 bytes is the `t` at
;; which the resource was created and the last 3 bytes are the index of the
;; resource in the transaction.
(s/def :blaze.db/did
  (s/with-gen
    (s/and int? #(< 0xFFF %))
    #(gen/fmap
       (fn [[t n]] (+ (bit-shift-left t 24) n))
       (gen/tuple (s/gen :blaze.db/t) (gen/choose 0 0xFFFFFE)))))


(def ^:private byte-string-gen
  #(gen/fmap bs/from-byte-array gen2/bytes))


(s/def :blaze.db/byte-string
  (s/with-gen byte-string? byte-string-gen))


(s/def :blaze.db/state
  nat-int?)
