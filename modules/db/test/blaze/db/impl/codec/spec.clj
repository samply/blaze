(ns blaze.db.impl.codec.spec
  (:require
   [blaze.byte-string :as bs :refer [byte-string?]]
   [blaze.db.impl.codec :as codec]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as sg]
   [clojure.string :as str]
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

(def ^:private id-gen
  #(sg/fmap (comp codec/id-byte-string str/join)
            (sg/vector gen/char-alphanumeric 1 64)))

(s/def :blaze.db/id-byte-string
  (s/with-gen (s/and byte-string? #(<= 1 (bs/size %) 64)) id-gen))

(def ^:private byte-string-gen
  #(sg/fmap bs/from-byte-array gen/bytes))

(s/def :blaze.db/byte-string
  (s/with-gen byte-string? byte-string-gen))

(s/def :blaze.db/state
  nat-int?)
