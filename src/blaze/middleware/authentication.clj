(ns blaze.middleware.authentication
  "Verifies a signed JWT using OpenID Connect to provide
   the public key used to sign the token."
  (:require
    [buddy.auth.backends :as backends]
    [buddy.auth.middleware :as middleware]
    [buddy.core.keys :as keys]
    [cheshire.core :as json]
    [clojure.spec.alpha :as s])
  (:import
    (java.security PublicKey)))


(s/fdef public-key
  :args (s/cat :jwks-json string?)
  :ret (s/nilable #(instance? PublicKey %)))


(defn public-key [jwks-json]
  "Take a the first jwk from jwks-json string and
   convert it into a PublicKey."
  (some-> jwks-json
          (json/parse-string keyword)
          :keys
          first
          keys/jwk->public-key))


(s/fdef jwks-json
  :args (s/cat :url string?)
  :ret map?)


(defn jwks-json [url]
  (let [well-known "/.well-known/openid-configuration"
        jwks-json  (some-> url
                           (str well-known)
                           slurp
                           json/parse-string
                           (get "jwks_uri")
                           slurp)]
    (if (some? jwks-json)
      jwks-json
       (throw (ex-info "No jwk found"
                       {:cause (str "No jwk found at " url well-known)})))))


(s/fdef wrap-authentication
  :args (s/cat :public-key #(instance? PublicKey %)))


(defn wrap-authentication [public-key]
  (fn [handler]
    (middleware/wrap-authentication
     handler
     (backends/jws {:token-name "Bearer"
                    :secret     public-key
                    :options    {:alg :rs256}}))))
