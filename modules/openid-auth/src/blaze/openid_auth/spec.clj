(ns blaze.openid-auth.spec
  (:require
   [blaze.auth.spec]
   [clojure.spec.alpha :as s])
  (:import
   [java.security.interfaces RSAPublicKey]))

(s/def :blaze.openid-auth/provider-url
  string?)

(s/def :blaze.openid-auth/issuer
  (s/nilable :blaze.auth/realm))

(s/def :blaze.openid-auth/audience
  (s/nilable string?))

(s/def :blaze.openid-auth.public-key/kid
  (s/nilable string?))

(s/def :blaze.openid-auth.public-key/key
  #(instance? RSAPublicKey %))

(s/def :blaze.openid-auth/public-key
  (s/keys :req-un [:blaze.openid-auth.public-key/kid
                   :blaze.openid-auth.public-key/key]))

(s/def :blaze.openid-auth/public-keys
  (s/coll-of :blaze.openid-auth/public-key :kind vector? :max-count 16))

(s/def :blaze.openid-auth/legacy-mode?
  boolean?)

(s/def :blaze.openid-auth/verify-opts
  (s/keys :req-un [:blaze.openid-auth/legacy-mode?]
          :opt-un [:blaze.openid-auth/issuer :blaze.openid-auth/audience]))

(s/def :blaze.openid-auth.jwks/keys
  :blaze.openid-auth/public-keys)

(s/def :blaze.openid-auth.jwks/skipped
  (s/coll-of string? :kind vector?))
