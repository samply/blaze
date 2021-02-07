(ns blaze.openid-auth
  "Verifies a signed JWT using OpenID Connect to provide
   the public key used to sign the token."
  (:require
    [buddy.auth.backends :as backends]
    [buddy.core.keys :as keys]
    [integrant.core :as ig]
    [jsonista.core :as j]
    [taoensso.timbre :as log]))


(set! *warn-on-reflection* true)


(def ^:private object-mapper
  (j/object-mapper
    {:decode-key-fn true}))


(defn public-key
  "Take a the first jwk from jwks-json string and
   convert it into a PublicKey."
  [jwks-json]
  (some-> jwks-json
          (j/read-value object-mapper)
          :keys
          first
          keys/jwk->public-key))


(defn jwks-json [url]
  (let [well-known "/.well-known/openid-configuration"
        jwks-json (some-> url
                          (str well-known)
                          slurp
                          (j/read-value object-mapper)
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


(derive :blaze.openid-auth/backend :blaze.auth/backend)
