(ns blaze.db.impl.search-param.near.geo)

(set! *warn-on-reflection* true)

(def ^:private ^:const earth-radius
  "Earths mean radius in meters, see https://en.wikipedia.org/wiki/Earth_radius."
  6371008.7714)

(defn- hav
  "Haversed sine of `theta`."
  [theta]
  (let [half-angle (/ theta 2)]
    (* (Math/sin half-angle) (Math/sin half-angle))))

(defn haversine-distance
  "Calculate the distance between two geographic coordinates (`location-1` and
  `location-2`) using the Haversine formula, which simplifies by assuming a
  spherical earth (error ≤ 0.5%). Returns distance in meters. See
  https://en.wikipedia.org/wiki/Haversine_formula."
  {:arglists '([location-1 location-2])}
  [{lat-1 :latitude lon-1 :longitude} {lat-2 :latitude lon-2 :longitude}]
  (let [delta-lat (Math/toRadians (- lat-2 lat-1))
        delta-long (Math/toRadians (- lon-2 lon-1))
        alpha (+ (hav delta-lat)
                 (* (Math/cos (Math/toRadians lat-1))
                    (Math/cos (Math/toRadians lat-2))
                    (hav delta-long)))]
    (bigdec (* earth-radius (Math/asin (Math/sqrt alpha)) 2))))
