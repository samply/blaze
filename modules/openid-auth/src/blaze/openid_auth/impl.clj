(ns blaze.openid-auth.impl
  (:require
    [blaze.anomaly :as ba :refer [throw-anom]]
    [blaze.openid-auth.spec]
    [buddy.auth.protocols :as p]
    [buddy.core.keys :as keys]
    [buddy.sign.jwt :as jwt]
    [hato.client :as hc]
    [taoensso.timbre :as log]))


(defn- missing-key-msg [jwks-uri]
  (format "Missing key in JWKS document config at `%s`." jwks-uri))


(defn- public-key
  "Take the first jwk from the parsed jwks-json map and converts it into a
  PublicKey.

  Returns nil of no key was found."
  [[jwks-uri jwks-document]]
  (if-let [key (some-> jwks-document :keys first)]
    (keys/jwk->public-key key)
    (throw-anom (ba/fault (missing-key-msg jwks-uri)))))

(def well-known-uri "/.well-known/openid-configuration")


(defn- fetch [uri http-client]
  (hc/get uri {:http-client http-client :accept :json :as :json}))


(defn- missing-jwks-uri-msg [url]
  (format "Missing `jwks_uri` in OpenID config at `%s`." url))


(defn- jwks-json [url http-client]
  (let [openid-config (-> (fetch url http-client) :body)]
    (if-let [jwks-uri (:jwks_uri openid-config)]
      [jwks-uri (-> (fetch jwks-uri http-client) :body)]
      (throw-anom (ba/fault (missing-jwks-uri-msg url))))))


(defn fetch-public-key [http-client provider-url]
  (-> provider-url (str well-known-uri) (jwks-json http-client) public-key))


(defn- parse-header [{{:strs [authorization]} :headers}]
  (some->> authorization (re-find #"^Bearer (.+)$") (second)))


(defn- unsign [token public-key]
  (try
    (jwt/unsign token public-key {:alg :rs256})
    (catch Exception e
      (log/error "Error while unsigning:" (ex-message e)))))


(defrecord Backend [future public-key-state]
  p/IAuthentication
  (-parse [_ request]
    (parse-header request))

  (-authenticate [_ _ token]
    (when token
      (if-let [public-key @public-key-state]
        (unsign token public-key)
        (log/warn "Can't authenticate because the public key is missing.")))))
