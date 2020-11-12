(ns blaze.db.impl.codec.spec
  (:require
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
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


(defn- byte-array-gen [max-size]
  #(gen/fmap byte-array (gen/vector gen2/byte 1 max-size)))


(s/def :blaze.db/id-bytes
  (s/with-gen bytes? (byte-array-gen 64)))


(s/def :blaze.db/state
  nat-int?)
