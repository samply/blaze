(ns blaze.openid-auth.jwt
  "Verification of RS256 signed JWT access tokens.

  Every rule is explicit and either applies in both modes or only in the RFC
  mode (RFC 9068, RFC 8725, RFC 7515). Failures are anomalies with fixed
  messages that contain nothing taken from the token."
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.http-client.json :as json]
   [clojure.string :as str]
   [cognitect.anomalies :as anom])
  (:import
   [java.io ByteArrayInputStream]
   [java.nio.charset StandardCharsets]
   [java.security GeneralSecurityException Signature]
   [java.security.interfaces RSAPublicKey]
   [java.util Base64]))

(set! *warn-on-reflection* true)

(def mapper
  "The strict JSON mapper used for the token header and payload, the JWKS and
  the OpenID configuration."
  (json/object-mapper
   {:max-nesting-depth 8
    :max-number-length 32
    :max-name-length 256
    :max-string-length 16384
    :max-document-length 1048576}))

(def ^:const max-token-length 8192)

(def ^:private ^:const rfc-leeway
  "The leeway in seconds used for `exp` and `nbf` in the RFC mode."
  60)

(defn- leeway [legacy-mode?]
  (if legacy-mode? 0 rfc-leeway))

(defn- check-size [token]
  (if (<= (count token) max-token-length)
    token
    (ba/incorrect "Token too large.")))

(defn- split-token
  "Returns the three segments of `token` if it consists of exactly three
  non-empty segments of the base64url alphabet without padding."
  [token]
  (if (re-matches #"[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+" token)
    (str/split token #"\.")
    (ba/incorrect "Invalid token.")))

(defn- decode-segment [^String segment]
  (ba/try-one IllegalArgumentException ::anom/incorrect
    (.decode (Base64/getUrlDecoder) segment)))

(defn- parse-object
  "Decodes and parses `segment`, returning the JSON object or an anomaly with
  `msg`."
  [segment msg]
  (let [result (when-ok [bytes (decode-segment segment)]
                 (json/parse-json mapper (ByteArrayInputStream. bytes)))]
    (if (and (map? result) (not (ba/anomaly? result)))
      result
      (ba/incorrect msg))))

(defn- check-header
  {:arglists '([header legacy-mode?])}
  [{:keys [alg] :as header} legacy-mode?]
  (cond
    legacy-mode? header
    (not= "RS256" alg) (ba/forbidden "Unsupported token algorithm.")
    (contains? header :crit) (ba/forbidden "Unsupported critical token header.")
    :else header))

(defn- ordered-keys
  "Returns `keys` with the ones having `kid` first.

  The `kid` is only an optimization, since all keys are tried."
  [keys kid]
  (if (string? kid)
    (let [{matching true others false} (group-by #(= kid (:kid %)) keys)]
      (concat matching others))
    keys))

(defn- modulus-length
  "Returns the length of the modulus of `key` in bytes."
  [^RSAPublicKey key]
  (quot (+ (.bitLength (.getModulus key)) 7) 8))

(defn- signature-valid? [^bytes signing-input ^bytes signature {:keys [key]}]
  (and (= (alength signature) (modulus-length key))
       (true? (ba/try-one GeneralSecurityException ::anom/fault
                (let [verifier (Signature/getInstance "SHA256withRSA")]
                  (.initVerify verifier ^RSAPublicKey key)
                  (.update verifier signing-input)
                  (.verify verifier signature))))))

(defn- signing-input
  "Returns the ASCII bytes of the first two segments of `token` as received."
  ^bytes [^String token]
  (.getBytes (subs token 0 (str/last-index-of token ".")) StandardCharsets/US_ASCII))

(defn- check-signature
  {:arglists '([keys header token signature-segment])}
  [keys {:keys [kid]} token signature-segment]
  (if-ok [signature (decode-segment signature-segment)]
    (let [signing-input (signing-input token)]
      (if (some (partial signature-valid? signing-input signature) (ordered-keys keys kid))
        token
        (ba/forbidden "Invalid token signature.")))
    (fn [_] (ba/incorrect "Invalid token signature."))))

(defn- numeric-date?
  "Returns true if `x` is a NumericDate (RFC 7519 section 2): any integer, or
  a double only if it is finite.

  Jackson parses a number like `1e999` as infinite double, which would make
  a token never expire or always be valid."
  [x]
  (or (integer? x)
      (and (double? x) (Double/isFinite x))))

(defn- claim-present?
  "Returns true if `claims` contain the claim `key`.

  In legacy mode a null claim counts as absent, as it did before."
  [claims key legacy-mode?]
  (if legacy-mode?
    (some? (get claims key))
    (contains? claims key)))

(defn- check-exp [claims legacy-mode? now]
  (if (claim-present? claims :exp legacy-mode?)
    (let [exp (:exp claims)]
      (cond
        (not (numeric-date? exp)) (ba/incorrect "Invalid exp claim.")
        (>= now (+' exp (leeway legacy-mode?))) (ba/forbidden "Token expired.")
        :else claims))
    (if legacy-mode?
      claims
      (ba/forbidden "Missing exp claim."))))

(defn- check-nbf [claims legacy-mode? now]
  (if (claim-present? claims :nbf legacy-mode?)
    (let [nbf (:nbf claims)]
      (cond
        (not (numeric-date? nbf)) (ba/incorrect "Invalid nbf claim.")
        (< now (-' nbf (leeway legacy-mode?))) (ba/forbidden "Token not yet valid.")
        :else claims))
    claims))

(defn- check-iat [claims legacy-mode?]
  (if (and (not legacy-mode?) (contains? claims :iat) (not (numeric-date? (:iat claims))))
    (ba/incorrect "Invalid iat claim.")
    claims))

(defn- check-iss
  {:arglists '([claims opts])}
  [{:keys [iss] :as claims} {:keys [legacy-mode? issuer]}]
  (if (or (and legacy-mode? (nil? issuer))
          (and (string? iss) (= issuer iss)))
    claims
    (ba/forbidden "Invalid issuer.")))

(defn- audience-matches? [audience aud]
  (cond
    (string? aud) (= audience aud)
    (sequential? aud) (and (every? string? aud) (some #(= audience %) aud))
    :else false))

(defn- check-aud
  {:arglists '([claims opts])}
  [{:keys [aud] :as claims} {:keys [legacy-mode? audience]}]
  (if (or (and legacy-mode? (nil? audience))
          (audience-matches? audience aud))
    claims
    (ba/forbidden "Invalid audience.")))

(defn- check-claims
  {:arglists '([claims opts now])}
  [claims {:keys [legacy-mode?] :as opts} now]
  (when-ok [_ (check-exp claims legacy-mode? now)
            _ (check-nbf claims legacy-mode? now)
            _ (check-iat claims legacy-mode?)
            _ (check-iss claims opts)
            _ (check-aud claims opts)]
    claims))

(defn verify
  "Verifies `token` against `keys` at `now` (epoch seconds).

  The `opts` are `:legacy-mode?`, `:issuer` and `:audience`. Returns the claims
  with keyword keys or an anomaly. The anomaly has the category `incorrect`
  for malformed tokens and `forbidden` for well-formed but rejected ones."
  {:arglists '([keys opts now token])}
  [keys {:keys [legacy-mode?] :as opts} now token]
  (when-ok [_ (check-size token)
            [header-segment payload-segment signature-segment] (split-token token)
            header (parse-object header-segment "Invalid token header.")
            _ (check-header header legacy-mode?)
            _ (check-signature keys header token signature-segment)
            claims (parse-object payload-segment "Invalid token payload.")]
    (check-claims claims opts now)))
