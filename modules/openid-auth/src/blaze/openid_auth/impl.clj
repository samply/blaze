(ns blaze.openid-auth.impl
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.openid-auth.spec]
   [blaze.util :refer [str]]
   [buddy.auth.protocols :as p]
   [buddy.core.keys :as keys]
   [buddy.sign.jwt :as jwt]
   [cognitect.anomalies :as anom]
   [hato.client :as hc]
   [taoensso.timbre :as log]))

(defn- missing-key-msg [jwks-uri]
  (format "Missing key in JWKS document config at `%s`." jwks-uri))

(defn- public-key
  "Take the first jwk with usage signature from the parsed jwks-json map and
  converts it into a PublicKey.

  Returns nil of no key was found."
  [[jwks-uri jwks-document]]
  (if-let [key (some #(when (= "sig" (:use %)) %) (:keys jwks-document))]
    (keys/jwk->public-key key)
    (ba/fault (missing-key-msg jwks-uri))))

(def ^:private ^:const well-known-uri "/.well-known/openid-configuration")

(defn- message [e]
  (let [cause (ex-cause e)]
    (cond-> (or (ex-message e) (class e))
      cause (str ": " (message cause)))))

(defn- fetch [http-client uri]
  (try
    (:body (hc/get uri {:http-client http-client :accept :json :as :json}))
    (catch Throwable e
      (ba/fault (message e)))))

(defn- prefix-msg [prefix anomaly]
  (update anomaly ::anom/message (partial str prefix ": ")))

(defn- missing-jwks-uri-msg [url]
  (format "Missing `jwks_uri` in OpenID config at `%s`." url))

(defn- jwks-json [http-client url]
  (if-ok [openid-config (fetch http-client url)]
    (if-let [jwks-uri (:jwks_uri openid-config)]
      (if-ok [jwks-document (fetch http-client jwks-uri)]
        [jwks-uri jwks-document]
        (partial prefix-msg "Error while fetching the JWKS document"))
      (ba/fault (missing-jwks-uri-msg url)))
    (partial prefix-msg "Error while fetching the OpenID configuration")))

(defn fetch-public-key [http-client provider-url]
  (when-ok [result (jwks-json http-client (str provider-url well-known-uri))]
    (public-key result)))

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
