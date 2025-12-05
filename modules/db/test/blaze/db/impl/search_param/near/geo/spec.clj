(ns blaze.db.impl.search-param.near.geo.spec
  (:require
   [blaze.db.impl.search-param.near.geo :as-alias geo]
   [clojure.spec.alpha :as s]))

(s/def ::geo/latitude
  (s/and decimal? #(<= -90.0M % 90.0M)))

(s/def ::geo/longitude
  (s/and decimal? #(<= -180.0M % 180.0M)))

(s/def ::geo/coordinates
  (s/keys :req-un [::geo/latitude ::geo/longitude]))
