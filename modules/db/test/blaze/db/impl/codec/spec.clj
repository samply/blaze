(ns blaze.db.impl.codec.spec
  (:require
   [blaze.byte-string :as bs :refer [byte-string?]]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as sg]
   [clojure.test.check.generators :as gen]))

(s/def :blaze.db/tid
  (s/with-gen int? sg/int))

(s/def :blaze.db/spid
  (s/with-gen int? sg/int))

(s/def :blaze.db/hash-prefix
  (s/and byte-string? #(= 4 (bs/size %))))

(s/def :blaze.db/c-hash
  (s/with-gen int? sg/int))

(s/def :blaze.db/v-hash
  (s/with-gen int? sg/int))

(s/def :blaze.db/unit-hash
  (s/with-gen int? sg/int))

(def ^:private byte-string-gen
  #(sg/fmap bs/from-byte-array gen/bytes))

(s/def :blaze.db/byte-string
  (s/with-gen byte-string? byte-string-gen))

(s/def :blaze.db/state
  nat-int?)
