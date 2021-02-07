(ns blaze.openid-auth-spec
  (:require
    [blaze.openid-auth :as openid-auth]
    [clojure.spec.alpha :as s])
  (:import
    [java.security PublicKey]))


(s/fdef openid-auth/public-key
  :args (s/cat :jwks-json string?)
  :ret (s/nilable #(instance? PublicKey %)))


(s/fdef openid-auth/jwks-json
  :args (s/cat :url string?)
  :ret map?)
