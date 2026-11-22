(ns blaze.openid-auth.test-util
  "Generates RSA keys, JWKs and signed tokens for tests."
  (:require
   [jsonista.core :as j])
  (:import
   [java.math BigInteger]
   [java.nio.charset StandardCharsets]
   [java.security KeyPair KeyPairGenerator PrivateKey Signature]
   [java.security.interfaces RSAPublicKey]
   [java.util Arrays Base64]
   [javax.crypto Mac]
   [javax.crypto.spec SecretKeySpec]))

(set! *warn-on-reflection* true)

(defn key-pair
  (^KeyPair [] (key-pair 2048))
  (^KeyPair [bits]
   (.generateKeyPair (doto (KeyPairGenerator/getInstance "RSA") (.initialize (int bits))))))

(defn base64url ^String [^bytes bs]
  (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) bs))

(defn base64url-str ^String [^String s]
  (base64url (.getBytes s StandardCharsets/UTF_8)))

(defn unsigned-bytes
  "Returns the big-endian bytes of the positive `n` without a sign byte."
  ^bytes [^BigInteger n]
  (let [bs (.toByteArray n)]
    (if (and (< 1 (alength bs)) (zero? (aget bs 0)))
      (Arrays/copyOfRange bs 1 (alength bs))
      bs)))

(defn int->base64url ^String [n]
  (base64url (unsigned-bytes (biginteger n))))

(defn jwk
  "Returns the public JWK of `key-pair` as map with string keys."
  ([key-pair] (jwk key-pair {}))
  ([^KeyPair key-pair more]
   (let [^RSAPublicKey public-key (.getPublic key-pair)]
     (merge {"kty" "RSA"
             "use" "sig"
             "alg" "RS256"
             "n" (int->base64url (.getModulus public-key))
             "e" (int->base64url (.getPublicExponent public-key))}
            more))))

(defn jwks [& jwks]
  (j/write-value-as-string {"keys" (vec jwks)}))

(defn sign-bytes ^bytes [^PrivateKey private-key ^String signing-input]
  (let [signature (doto (Signature/getInstance "SHA256withRSA")
                    (.initSign private-key)
                    (.update (.getBytes signing-input StandardCharsets/US_ASCII)))]
    (.sign signature)))

(defn hmac-bytes ^bytes [^bytes secret ^String signing-input]
  (let [mac (doto (Mac/getInstance "HmacSHA256")
              (.init (SecretKeySpec. secret "HmacSHA256")))]
    (.doFinal mac (.getBytes signing-input StandardCharsets/US_ASCII))))

(defn raw-token
  "Returns a token of the raw JSON strings `header` and `payload`, signed by
  the private key of `key-pair`."
  [^KeyPair key-pair header payload]
  (let [signing-input (str (base64url-str header) "." (base64url-str payload))]
    (str signing-input "." (base64url (sign-bytes (.getPrivate key-pair) signing-input)))))

(defn token
  "Returns a token of the `header` and `claims` maps, signed by the private
  key of `key-pair`."
  ([key-pair claims]
   (token key-pair {"alg" "RS256" "typ" "JWT"} claims))
  ([key-pair header claims]
   (raw-token key-pair (j/write-value-as-string header)
              (j/write-value-as-string claims))))
