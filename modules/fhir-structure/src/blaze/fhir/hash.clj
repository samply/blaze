(ns blaze.fhir.hash
  (:require
    [blaze.fhir.spec.type :as type])
  (:import
    [com.google.common.hash HashCode Hashing]))


(defn generate
  "Calculates a SHA256 hash for `resource`.

  The hash need to be cryptographic because otherwise it would be possible to
  introduce a resource into Blaze which has the same hash as the target
  resource, overwriting it."
  ^HashCode [resource]
  (let [hasher (.newHasher (Hashing/sha256))]
    (type/-hash-into resource hasher)
    (.hash hasher)))


(defn encode
  "Returns a byte array of length 32 (256 bit) which represents the hash."
  ^bytes [hash]
  (.asBytes ^HashCode hash))


(defn decode [bytes]
  (some-> bytes HashCode/fromBytes))
