(ns blaze.db.impl.index.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def :blaze.db.index.stats/total
  nat-int?)

(s/def :blaze.db.index.stats/num-changes
  nat-int?)

(s/def :blaze.db.index/stats
  (s/keys :req-un [:blaze.db.index.stats/total :blaze.db.index.stats/num-changes]))
