(ns blaze.db.impl.search-param.near.geo-spec
  (:require
   [blaze.db.impl.search-param.near.geo.spec :as gs]
   [clojure.spec.alpha :as s]))

(s/fdef haversine-distance
  :args (s/cat :loc-1 ::gs/coordinates :loc-2 ::gs/coordinates)
  :ret number?)
