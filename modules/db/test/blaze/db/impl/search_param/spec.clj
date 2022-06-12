(ns blaze.db.impl.search-param.spec
  (:require
    [blaze.db.impl.codec.spec]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db/compartment
  (s/tuple :blaze.db/c-hash :blaze.db/did))
