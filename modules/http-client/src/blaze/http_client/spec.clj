(ns blaze.http-client.spec
  (:require
   [blaze.http-client :as-alias http-client]
   [clojure.spec.alpha :as s])
  (:import
   [java.net.http HttpClient]))

(defn http-client? [x]
  (instance? HttpClient x))

(s/def :blaze/http-client
  http-client?)

(s/def ::http-client/connect-timeout
  pos-int?)

(s/def ::http-client/trust-store
  (s/nilable string?))

(s/def ::http-client/trust-store-pass
  (s/nilable string?))
