(ns blaze.http-client.spec
  (:require
    [clojure.spec.alpha :as s]))


(s/def :blaze.http-client/connect-timeout
  pos-int?)
