(ns blaze.db.impl.bytes-spec
  (:require
    [blaze.db.impl.bytes :as bytes]
    [clojure.spec.alpha :as s]))


(s/fdef bytes/=
  :args (s/cat :a (s/nilable bytes?) :b (s/nilable bytes?))
  :ret boolean?)

(s/fdef bytes/concat
  :args (s/cat :byte-arrays (s/coll-of bytes?))
  :ret bytes?)
