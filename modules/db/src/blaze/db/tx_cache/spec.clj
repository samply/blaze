(ns blaze.db.tx-cache.spec
  (:require
    [clojure.spec.alpha :as s]))


(s/def :blaze.db.tx-cache/max-size
  nat-int?)
