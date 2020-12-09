(ns blaze.db.impl.codec.spec
  (:require
    [blaze.byte-string :as bs :refer [byte-string?]]
    [blaze.db.impl.codec :as codec]
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
    [clojure.string :as str]
    [clojure.test.check.generators :as gen2]))


(s/def :blaze.db/tid
  (s/with-gen int? gen/int))


(s/def :blaze.db/spid
  (s/with-gen int? gen/int))


(s/def :blaze.db/c-hash
  (s/with-gen int? gen/int))


(s/def :blaze.db/v-hash
  (s/with-gen int? gen/int))


(s/def :blaze.db/unit-hash
  (s/with-gen int? gen/int))


(def ^:private id-gen
  #(gen/fmap (comp codec/id-byte-string str/join)
             (gen/vector gen2/char-alphanumeric 1 64)))


(s/def :blaze.db/id-byte-string
  (s/with-gen (s/and byte-string? #(<= 1 (bs/size %) 64)) id-gen))


(s/def :blaze.db/state
  nat-int?)
