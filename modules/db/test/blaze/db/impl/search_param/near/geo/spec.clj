(ns blaze.db.impl.search-param.near.geo.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::latitude
  (s/and decimal? #(<= -90.0 % 90.0)))

(s/def ::longitude
  (s/and decimal? #(<= -180.0 % 180.0)))

(s/def ::coordinates
  (s/keys :req-un [::latitude ::longitude]))
