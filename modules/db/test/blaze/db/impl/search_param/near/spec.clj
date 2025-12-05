(ns blaze.db.impl.search-param.near.spec
  (:require
   [blaze.db.impl.search-param.near :as-alias near]
   [blaze.db.impl.search-param.near.geo :as-alias geo]
   [blaze.db.impl.search-param.near.geo.spec]
   [clojure.spec.alpha :as s]))

(s/def ::near/distance
  decimal?)

(s/def ::near/compiled-value
  (s/keys :req-un [::geo/latitude ::geo/longitude ::near/distance]))
