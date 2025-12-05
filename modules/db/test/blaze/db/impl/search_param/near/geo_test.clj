(ns blaze.db.impl.search-param.near.geo-test
  (:require
   [blaze.db.impl.search-param.near.geo :as geo]
   [blaze.db.impl.search-param.near.geo-spec]
   [blaze.db.impl.search-param.near.geo.spec]
   [clojure.test :refer [deftest is testing]]))

;; Test data
(def ^:private london
  {:latitude 51.5074M :longitude -0.1278M})

(def ^:private paris
  {:latitude 48.8566M :longitude 2.3522M})

(def ^:private leipzig
  {:latitude 51.33102M :longitude 12.38610M})

(def ^:private max-error
  0.005)

(defn distance-within-error [actual expected]
  (let [error-margin (* expected max-error)]
    (<= (- expected error-margin) actual (+ expected error-margin))))

(deftest haversine-distance-test
  (testing "distance between identical locations"
    (is (= 0.0M (geo/haversine-distance london london))))

  (testing "distance between London and Paris"
    (let [dist (geo/haversine-distance london paris)]       ;; ~ 343.56km
      (is (distance-within-error dist 343560M))))

  (testing "distance between Jakarta and Perth"
    (let [jakarta {:latitude -6.2M :longitude 106.8167M}
          perth {:latitude -31.953512M :longitude 115.857048M}
          dist (geo/haversine-distance jakarta perth)]      ;; ~ 3013.66km
      (is (distance-within-error dist 3013660M))))

  (testing "distance between two points on equator 1 degree apart"
    (let [dist (geo/haversine-distance
                {:latitude -0.5M :longitude 0.0M}
                {:latitude 0.5M :longitude 0.0M})]            ;; ~ 111.200km
      (is (distance-within-error dist 111200M))))

  (testing "distance when crossing international date line (also 1 degree)"
    (let [dist (geo/haversine-distance
                {:latitude 0.0M :longitude 179.5M}
                {:latitude 0.0M :longitude -179.5M})]         ;; ~ 111.200km
      (is (distance-within-error dist 111200M))))

  (testing "distance between Leipzig and Capetown"
    (let [capetown {:latitude -33.94075M :longitude 18.46438M}
          dist (geo/haversine-distance leipzig capetown)]   ;; ~ 9500.43km
      (is (distance-within-error dist 9500430M))))

  (testing "distance between Leipzig and Auckland"
    (let [auckland {:latitude -36.85200M :longitude 174.76316M}
          dist (geo/haversine-distance leipzig auckland)]   ;; ~ 17887.91km
      (is (distance-within-error dist 17887910M)))))
