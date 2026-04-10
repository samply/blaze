(ns blaze.openid-auth.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def :blaze.openid-auth/provider-url
  string?)

(s/def :blaze.openid-auth/issuer
  (s/nilable string?))

(s/def :blaze.openid-auth/audience
  (s/nilable string?))
