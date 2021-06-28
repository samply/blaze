(ns blaze.db.resource-handle-cache.spec
  (:require
    [clojure.spec.alpha :as s]))


(s/def :blaze.db.resource-handle-cache/max-size
  nat-int?)
