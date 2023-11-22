(ns blaze.openid-auth.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def :blaze.openid-auth/provider-url
  string?)
