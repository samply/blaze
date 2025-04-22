(ns blaze.http-client.spec
  (:require
   [blaze.http-client :as-alias http-client]
   [clojure.spec.alpha :as s])
  (:import
   [java.net.http HttpClient]))

(s/def :blaze/http-client
  #(instance? HttpClient %))

(s/def ::http-client/connect-timeout
  pos-int?)

(s/def ::http-client/trust-store
  (s/nilable string?))

(s/def ::http-client/trust-store-pass
  (s/nilable string?))
