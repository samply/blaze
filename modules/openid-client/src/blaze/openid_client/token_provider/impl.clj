(ns blaze.openid-client.token-provider.impl
  (:require
   [blaze.anomaly :as ba]
   [blaze.http-client.json :as json]
   [blaze.time :as bt]
   [java-time.api :as time]))

(def mapper
  "The strict JSON mapper used for the OpenID configuration and the token
  response. Its string limit of 64 KiB covers large access tokens."
  (json/object-mapper
   {:max-nesting-depth 8
    :max-number-length 32
    :max-name-length 256
    :max-string-length 65536
    :max-document-length 1048576}))

(defn- expires-within-5-min? [expires-at]
  (time/before? expires-at (time/plus (bt/instant) (time/minutes 5))))

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
