(ns blaze.db.impl.bytes-spec
  (:require
    [blaze.db.impl.bytes :as bytes]
    [clojure.spec.alpha :as s]))


(s/fdef bytes/=
  :args (s/cat :a bytes? :b bytes?)
  :ret boolean?)
