(ns blaze.openid-auth.jwk
  "Loads the RSA public keys usable for RS256 from a JWKS document.

  A JWK not satisfying all key loading rules is skipped with a message naming
  its `kid`, never its key material."
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [cognitect.anomalies :as anom])
  (:import
   [java.math BigInteger]
   [java.security KeyFactory]
   [java.security.spec InvalidKeySpecException RSAPublicKeySpec]
   [java.util Base64]))

(set! *warn-on-reflection* true)

(def ^:const max-keys
  "The maximum number of keys used."
  16)

(def ^:private ^:const max-n-length
  "The maximum length of the base64url encoded modulus (8192 bits)."
  1366)

(def ^:private ^:const max-e-length
  "The maximum length of the base64url encoded public exponent (256 bits)."
  43)

(def ^:private ^:const min-modulus-bits 2048)
(def ^:private ^:const max-modulus-bits 8192)

(def ^:private ^BigInteger min-exponent
  "The public exponent has to be greater than 2^16 (FIPS 186-5)."
  (.shiftLeft BigInteger/ONE 16))

(def ^:private ^BigInteger max-exponent
  "The public exponent has to be less than 2^256 (FIPS 186-5)."
  (.shiftLeft BigInteger/ONE 256))

(def ^:private private-key-members
  [:d :p :q :dp :dq :qi])

(defn- skip [msg]
  (ba/incorrect msg))

(defn- decode-positive-integer
  "Decodes the strict base64url (no padding) string `s` of at most `max-length`
  characters to a positive integer."
  [s max-length name]
  (if (and (string? s) (<= (count s) max-length) (re-matches #"[A-Za-z0-9_-]+" s))
    (when-ok [bytes (ba/try-one IllegalArgumentException ::anom/incorrect
                      (.decode (Base64/getUrlDecoder) ^String s))]
      (let [n (BigInteger. 1 ^bytes bytes)]
        (if (pos? (.signum n))
          n
          (skip (format "`%s` isn't positive" name)))))
    (skip (format "invalid `%s`" name))))

(defn- check-header-members
  {:arglists '([jwk])}
  [{:keys [kty use alg key_ops] :as jwk}]
  (cond
    (not= "RSA" kty) (skip "`kty` isn't `RSA`")
    (not (contains? #{nil "sig"} use)) (skip "`use` isn't `sig`")
    (not (contains? #{nil "RS256"} alg)) (skip "`alg` isn't `RS256`")
    (and (some? key_ops) (not (and (sequential? key_ops) (some #{"verify"} key_ops))))
    (skip "`key_ops` doesn't contain `verify`")
    (some #(contains? jwk %) private-key-members) (skip "contains private key members")
    :else jwk))

(defn- check-modulus [^BigInteger n]
  (if (<= min-modulus-bits (.bitLength n) max-modulus-bits)
    n
    (skip (format "the modulus has %d bits, not between %d and %d"
                  (.bitLength n) min-modulus-bits max-modulus-bits))))

(defn- check-exponent [^BigInteger e]
  (if (and (.testBit e 0) (pos? (.compareTo e min-exponent)) (neg? (.compareTo e max-exponent)))
    e
    (skip "the public exponent isn't odd and between 2^16 and 2^256")))

(defn- rsa-public-key [^BigInteger n ^BigInteger e]
  (ba/try-one InvalidKeySpecException ::anom/incorrect
    (.generatePublic (KeyFactory/getInstance "RSA") (RSAPublicKeySpec. n e))))

(defn- public-key
  "Returns `{:kid kid :key public-key}` from `jwk` or an anomaly stating why
  `jwk` is skipped."
  {:arglists '([jwk])}
  [{:keys [kid] :as jwk}]
  (when-ok [{:keys [n e]} (check-header-members jwk)
            n (decode-positive-integer n max-n-length "n")
            n (check-modulus n)
            e (decode-positive-integer e max-e-length "e")
            e (check-exponent e)
            key (rsa-public-key n e)]
    {:kid (when (string? kid) kid) :key key}))

(defn- skip-msg
  {:arglists '([jwk reason])}
  [{:keys [kid]} reason]
  (format "Skip JWK with kid %s: %s." (pr-str kid) reason))

(defn- usable-keys-rf
  "Reducing function collecting at most `max-keys` usable keys and a message
  for every skipped JWK."
  [result jwk]
  (if-ok [key (public-key jwk)]
    (if (< (count (:keys result)) max-keys)
      (update result :keys conj key)
      (update result :skipped conj (skip-msg jwk (format "more than %d usable keys" max-keys))))
    (fn [{::anom/keys [message]}]
      (update result :skipped conj (skip-msg jwk message)))))

(defn- jwk-set?
  "Returns true if `document` is a JWK Set: a JSON object with a `keys` array
  of JSON objects (RFC 7517 section 5)."
  [document]
  (and (map? document)
       (sequential? (:keys document))
       (every? map? (:keys document))))

(defn public-keys
  "Returns a map of `:keys`, a vector of `{:kid kid :key public-key}` for at
  most 16 usable keys of the parsed `jwks-document` in the order of the
  document, and `:skipped`, a vector of messages naming the skipped JWKs and
  why they were skipped.

  Returns an anomaly if `jwks-document` isn't a JWK Set, so that a document
  like an error object isn't taken as a JWK Set without keys. The `:kid` is
  nil if the JWK has no string `kid`."
  [jwks-document]
  (if (jwk-set? jwks-document)
    (reduce usable-keys-rf {:keys [] :skipped []} (:keys jwks-document))
    (ba/incorrect "The document isn't a JWK Set, which requires a `keys` array of JSON objects.")))
