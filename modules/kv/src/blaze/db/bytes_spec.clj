(ns blaze.db.bytes-spec
  (:require
    [blaze.db.bytes :as bytes]
    [clojure.spec.alpha :as s]))


(s/fdef bytes/=
  :args (s/cat :a (s/nilable bytes?) :b (s/nilable bytes?))
  :ret boolean?)
