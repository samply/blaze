(ns blaze.openid-client.token-provider.spec
  (:require
   [blaze.openid-client.token-provider :as-alias tp]
   [blaze.openid-client.token-provider.protocol :as p]
   [clojure.spec.alpha :as s]))

(s/def :blaze.openid-client/token-provider
  #(satisfies? p/TokenProvider %))

(s/def ::tp/provider-url
  string?)

(s/def ::tp/client-id
  string?)

(s/def ::tp/client-secret
  string?)
