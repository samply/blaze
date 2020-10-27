(ns blaze.db.resource-cache.spec
  (:require
    [clojure.spec.alpha :as s]))


(s/def :blaze.db.resource-cache/max-size
  nat-int?)
