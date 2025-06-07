(ns blaze.page-store.local.hash
  (:import
   [com.google.common.hash HashCode Hashing]
   [com.google.common.io BaseEncoding]
   [java.nio.charset StandardCharsets]))

(set! *warn-on-reflection* true)

(defn hash-clause
  "Calculates a SHA256 hash of `clause`."
  [clause]
  (let [hasher (.newHasher (Hashing/sha256))]
    (run! #(.putString hasher (str %) StandardCharsets/UTF_8) clause)
    (.hash hasher)))

(defn hash-hashes
  "Calculates a SHA256 hash of `hashes`."
  [hashes]
  (let [hasher (.newHasher (Hashing/sha256))]
    (run! #(.putBytes hasher (.asBytes ^HashCode %)) hashes)
    (.hash hasher)))

(defn encode
  "Encodes `hash` via Base16, returning a token, a string of length 64."
  [hash]
  (.encode (BaseEncoding/base16) (.asBytes ^HashCode hash)))
