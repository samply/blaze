(ns blaze.http-client.spec
  (:require
    [clojure.spec.alpha :as s])
  (:import
    [java.net.http HttpClient]))


(s/def :blaze/http-client
  #(instance? HttpClient %))


(s/def :blaze.http-client/connect-timeout
  pos-int?)
