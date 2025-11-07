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

(def ^:private sig-keys-xf
  (comp (filter (comp #{"sig"} :use)) (map keys/jwk->public-key)))

(defn- public-keys
  "Returns all JWKs with usage signature from the parsed jwks-json map and
  converts it into a PublicKey."
  [jwks-document]
  (into [] sig-keys-xf (:keys jwks-document)))

(defn- missing-key-msg [jwks-uri]
  (format "Missing key in JWKS document config at `%s`." jwks-uri))

(defn fetch-public-keys [http-client provider-url]
  (when-ok [[jwks-uri jwks-document] (jwks-json http-client (str provider-url well-known-uri))]
    (let [keys (public-keys jwks-document)]
      (if (empty? keys)
        (ba/fault (missing-key-msg jwks-uri))
        keys))))

(defn- parse-header [{{:strs [authorization]} :headers}]
  (some->> authorization (re-find #"^Bearer (.+)$") (second)))

(defn- unsign [token public-key]
  (try
    (jwt/unsign token public-key {:alg :rs256})
    (catch Exception e
      (let [{:keys [type]} (ex-data e)]
        (if (= :validation type)
          (log/trace "Token validation unsuccessful. Will try next public key.")
          (log/error "Error while unsigning:" (ex-message e)))))))

(defrecord Backend [future public-keys-state]
  p/IAuthentication
  (-parse [_ request]
    (parse-header request))

  (-authenticate [_ _ token]
    (when token
      (let [public-keys @public-keys-state]
        (if (empty? public-keys)
          (log/warn "Can't authenticate because no public key is available.")
          (some (partial unsign token) public-keys))))))
