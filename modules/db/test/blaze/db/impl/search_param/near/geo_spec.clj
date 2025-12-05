(ns blaze.db.impl.search-param.near.geo-spec
  (:require
   [blaze.db.impl.search-param.near.geo :as geo]
   [blaze.db.impl.search-param.near.geo.spec]
   [clojure.spec.alpha :as s]))

(s/fdef geo/haversine-distance
  :args (s/cat :loc-1 ::geo/coordinates :loc-2 ::geo/coordinates)
  :ret decimal?)
