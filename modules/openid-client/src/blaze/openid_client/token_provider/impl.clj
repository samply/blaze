(ns blaze.openid-client.token-provider.impl
  (:require
   [blaze.anomaly :as ba]
   [java-time.api :as time]))

(defn- expires-within-5-min? [expires-at]
  (time/before? expires-at (time/plus (time/instant) (time/minutes 5))))

(defn should-refresh? [state]
  (or (nil? state)
      (ba/anomaly? state)
      (nil? (:expires-at state))
      (expires-within-5-min? (:expires-at state))))

(defn token [state]
  (cond
    (nil? state) (ba/unavailable "No token available yet.")
    (ba/anomaly? state) state
    :else (:token state)))
