(ns blaze.openid-auth.impl
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.auth.protocols :as p]
   [blaze.http-client.json :as json]
   [blaze.openid-auth.jwk :as jwk]
   [blaze.openid-auth.jwt :as jwt]
   [blaze.openid-auth.spec]
   [blaze.util :refer [str]]
   [cognitect.anomalies :as anom]
   [hato.client :as hc]
   [taoensso.timbre :as log])
  (:import
   [java.time Clock]))

(set! *warn-on-reflection* true)

(def ^:private ^:const well-known-uri "/.well-known/openid-configuration")

(defn- message [e]
  (let [cause (ex-cause e)]
    (cond-> (or (ex-message e) (class e))
      cause (str ": " (message cause)))))

(defn- fetch-response
  "Returns the response with a body stream or an anomaly.

  An error status doesn't throw, so that `json/parse-response` can close the
  body stream."
  [http-client uri]
  (try
    (hc/get uri {:http-client http-client :accept :json :as :stream
                 :throw-exceptions false})
    (catch Throwable e
      (ba/fault (message e)))))

(defn- fetch
  "Fetches the JSON document at `uri`, parsing it with the strict mapper,
  which limits it to 1 MiB."
  [http-client uri]
  (when-ok [response (fetch-response http-client uri)]
    (json/parse-response jwt/mapper response)))

(defn- prefix-msg [prefix anomaly]
  (update anomaly ::anom/message (partial str prefix ": ")))

(defn- missing-jwks-uri-msg [url]
  (format "Missing `jwks_uri` in OpenID config at `%s`." url))

(defn- jwks-uri [openid-config]
  (let [uri (when (map? openid-config) (:jwks_uri openid-config))]
    (when (string? uri) uri)))

(defn- jwks-json [http-client url]
  (if-ok [openid-config (fetch http-client url)]
    (if-let [jwks-uri (jwks-uri openid-config)]
      (if-ok [jwks-document (fetch http-client jwks-uri)]
        [jwks-uri jwks-document]
        (partial prefix-msg "Error while fetching the JWKS document"))
      (ba/fault (missing-jwks-uri-msg url)))
    (partial prefix-msg "Error while fetching the OpenID configuration")))

(defn- missing-key-msg [jwks-uri]
  (format "Missing key in JWKS document config at `%s`." jwks-uri))

(defn fetch-public-keys
  "Fetches the OpenID configuration of `provider-url` and the JWKS document it
  references.

  Returns a map of the usable public `:keys` and the messages of the
  `:skipped` keys, or an anomaly. If the JWKS document is a JWK Set without
  usable key, the anomaly carries the messages of the skipped keys under
  `:blaze.openid-auth/skipped`."
  [http-client provider-url]
  (when-ok [[jwks-uri jwks-document] (jwks-json http-client (str provider-url well-known-uri))
            {:keys [keys skipped] :as result}
            (-> (jwk/public-keys jwks-document)
                (ba/exceptionally (partial prefix-msg "Error while fetching the JWKS document")))]
    (if (empty? keys)
      (ba/fault (missing-key-msg jwks-uri) :blaze.openid-auth/skipped skipped)
      result)))

(defn key-state
  "Returns the key state following `state` after the fetch `result`.

  The key state is a map of the usable public `:keys` and the messages of the
  `:skipped` keys of the last fetch. Only a valid JWK Set replaces the keys
  fetched before. A fetch without one (network error, error status, invalid
  JSON, a document that isn't a JWK Set) keeps them. A JWK Set without usable
  key is an answer of the provider, so it removes the keys fetched before, as
  a JWK Set with other usable keys would."
  [state result]
  (if (ba/anomaly? result)
    (if-some [skipped (:blaze.openid-auth/skipped result)]
      {:keys [] :skipped skipped}
      state)
    result))

(defn- bearer-token [{{:strs [authorization]} :headers}]
  (some->> authorization (re-find #"^Bearer (.+)$") (second)))

(defn- epoch-seconds [^Clock clock]
  (.getEpochSecond (.instant clock)))

(defn- no-credentials [realm]
  (ba/incorrect "No bearer token." :blaze.auth/realm realm))

(defn- rejected-credentials [realm]
  (ba/forbidden "Invalid bearer token."
                :blaze.auth/realm realm
                :blaze.auth/error "invalid_token"))

(defn- keys-unavailable [realm]
  (ba/unavailable "No public key of the OpenID provider is available."
                  :blaze.auth/realm realm))

(defn- verify [public-keys opts clock realm token]
  (if-ok [claims (jwt/verify public-keys opts (epoch-seconds clock) token)]
    claims
    (fn [{::anom/keys [message]}]
      (log/debug "Token rejected:" message)
      (rejected-credentials realm))))

(defrecord Backend [future key-state clock opts realm]
  p/AuthenticationBackend
  (-authenticate [_ request]
    (if-let [token (bearer-token request)]
      (let [{public-keys :keys} @key-state]
        (if (empty? public-keys)
          (do (log/warn "Can't authenticate because no public key is available.")
              (keys-unavailable realm))
          (verify public-keys opts clock realm token)))
      (no-credentials realm))))
