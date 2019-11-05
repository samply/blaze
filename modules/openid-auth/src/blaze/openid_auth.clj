(ns blaze.openid-auth
  "Verifies a signed JWT using OpenID Connect to provide
   the public key used to sign the token."
  (:require
    [buddy.auth.backends :as backends]
    [buddy.core.keys :as keys]
    [cheshire.core :as json]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [java.security PublicKey]))


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
        jwks-json (some-> url
                          (str well-known)
                          slurp
                          json/parse-string
                          (get "jwks_uri")
                          slurp)]
    (if (some? jwks-json)
      jwks-json
      (throw (ex-info "No jwk found"
                      {:cause (str "No jwk found at " url well-known)})))))


(defmethod ig/init-key :blaze.openid-auth/backend
  [_ {:openid-provider/keys [url]}]
  (log/info "Enabled authentication using OpenID provider:" url)
  (backends/jws
    {:token-name "Bearer"
     :secret (-> url jwks-json public-key)
     :options {:alg :rs256}}))
