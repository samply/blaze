(ns blaze.db.node.stats.spec
  (:require
   [blaze.db.impl.codec.spec]
   [blaze.db.impl.index.spec]
   [blaze.db.node.stats :as-alias stats]
   [clojure.spec.alpha :as s]))

;; the increments of a transaction are partial and can be negative, so they
;; can't be `:blaze.db.index/stats`
(s/def ::stats/total
  int?)

(s/def ::stats/num-changes
  int?)

(s/def ::stats/increment
  (s/keys :opt-un [::stats/total ::stats/num-changes]))

(s/def ::stats/increments
  (s/map-of :blaze.db/tid ::stats/increment))

(s/def ::stats/types
  (s/map-of :blaze.db/tid :blaze.db.index/stats))

(s/def ::stats/system
  :blaze.db.index/stats)

(s/def ::stats/stats
  (s/keys :req-un [::stats/types ::stats/system]))
