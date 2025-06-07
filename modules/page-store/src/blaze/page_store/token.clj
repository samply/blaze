(ns blaze.page-store.token
  (:import
   [com.google.common.hash HashCode Hashing]
   [com.google.common.io BaseEncoding]
   [java.nio.charset StandardCharsets]))

(set! *warn-on-reflection* true)

(defn hash-clause [clause]
  (let [hasher (.newHasher (Hashing/sha256))]
    (run! #(.putString hasher (str %) StandardCharsets/UTF_8) clause)
    (.hash hasher)))

(defn hash-hashes [hashes]
  (let [hasher (.newHasher (Hashing/sha256))]
    (run! #(.putBytes hasher (.asBytes ^HashCode %)) hashes)
    (.hash hasher)))

(defn encode [hash]
  (.encode (BaseEncoding/base16) (.asBytes ^HashCode hash)))

(defn generate [clauses]
  (encode (hash-hashes (mapv hash-clause clauses))))
