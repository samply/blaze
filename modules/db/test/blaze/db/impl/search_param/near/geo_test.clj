(ns blaze.db.impl.search-param.near.geo-test
  (:require
   [blaze.db.impl.search-param.near.geo :as geo]
   [blaze.db.impl.search-param.near.geo-spec]
   [clojure.test :refer [deftest is testing]]))

;; Test data
(def ^:private london
  {:latitude 51.5074 :longitude -0.1278})

(def ^:private paris
  {:latitude 48.8566 :longitude 2.3522})

(def ^:private leipzig
  {:latitude 51.33102 :longitude 12.38610})

(def ^:private max-error
  0.005)

(defn distance-within-error [actual expected]
  (let [error-margin (* expected max-error)]
    (<= (- expected error-margin) actual (+ expected error-margin))))

(deftest haversine-distance-test
  (testing "distance between identical locations"
    (is (= 0.0 (geo/haversine-distance london london))))

  (testing "distance between London and Paris"
    (let [dist (geo/haversine-distance london paris)]       ;; ~ 343.56km
      (is (distance-within-error dist 343560))))

  (testing "distance between Jakarta and Perth"
    (let [jakarta {:latitude -6.2 :longitude 106.8167}
          perth {:latitude -31.953512 :longitude 115.857048}
          dist (geo/haversine-distance jakarta perth)]      ;; ~ 3013.66km
      (is (distance-within-error dist 3013660))))

  (testing "distance between two points on equator 1 degree apart"
    (let [dist (geo/haversine-distance
                {:latitude -0.5 :longitude 0.0}
                {:latitude 0.5 :longitude 0.0})]            ;; ~ 111.200km
      (is (distance-within-error dist 111200))))

  (testing "distance when crossing international date line (also 1 degree)"
    (let [dist (geo/haversine-distance
                {:latitude 0.0 :longitude 179.5}
                {:latitude 0.0 :longitude -179.5})]         ;; ~ 111.200km
      (is (distance-within-error dist 111200))))

  (testing "distance between Leipzig and Capetown"
    (let [capetown {:latitude -33.94075 :longitude 18.46438}
          dist (geo/haversine-distance leipzig capetown)]   ;; ~ 9500.43km
      (is (distance-within-error dist 9500430))))

  (testing "distance between Leipzig and Auckland"
    (let [auckland {:latitude -36.85200 :longitude 174.76316}
          dist (geo/haversine-distance leipzig auckland)]   ;; ~ 17887.91km
      (is (distance-within-error dist 17887910)))))
