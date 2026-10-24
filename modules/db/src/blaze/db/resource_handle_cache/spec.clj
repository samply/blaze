(ns blaze.db.resource-handle-cache.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def :blaze.db.resource-handle-cache/max-size-ratio
  (s/and number? pos?))

(s/def :blaze.db.resource-handle-cache/max-recent-writes
  nat-int?)

(s/def :blaze.db.resource-handle-cache/doorkeeper-window
  pos-int?)
