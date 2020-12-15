(ns blaze.fhir.hash
  (:require
    [blaze.byte-string :as bs]
    [blaze.fhir.spec.type :as type])
  (:import
    [com.google.common.hash Hashing]))


(defn generate
  "Calculates a SHA256 hash for `resource`.

  The hash need to be cryptographic because otherwise it would be possible to
  introduce a resource into Blaze which has the same hash as the target
  resource, overwriting it."
  [resource]
  (let [hasher (.newHasher (Hashing/sha256))]
    (type/hash-into resource hasher)
    (bs/from-byte-array (.asBytes (.hash hasher)))))
