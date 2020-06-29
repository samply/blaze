(ns blaze.db.hash
  (:import
    [clojure.lang Keyword]
    [com.google.common.hash HashCode Hashing PrimitiveSink]
    [java.util List Map]
    [java.nio.charset StandardCharsets]))


(set! *warn-on-reflection* true)


(defprotocol Hashable
  (hash-into [_ sink]))


(extend-protocol Hashable
  nil
  (hash-into [_ ^PrimitiveSink sink]
    (.putByte sink (byte 0))
    (.putByte sink (byte 0)))
  String
  (hash-into [s ^PrimitiveSink sink]
    (.putByte sink (byte 1))
    (.putString sink s StandardCharsets/UTF_8))
  Keyword
  (hash-into [k ^PrimitiveSink sink]
    (.putByte sink (byte 2))
    (.putString sink (str k) StandardCharsets/UTF_8))
  Byte
  (hash-into [b ^PrimitiveSink sink]
    (.putByte sink (byte 3))
    (.putByte sink b))
  Short
  (hash-into [s ^PrimitiveSink sink]
    (.putByte sink (byte 4))
    (.putShort sink s))
  Integer
  (hash-into [i ^PrimitiveSink sink]
    (.putByte sink (byte 5))
    (.putInt sink i))
  Long
  (hash-into [l ^PrimitiveSink sink]
    (.putByte sink (byte 6))
    (.putLong sink l))
  BigDecimal
  (hash-into [l ^PrimitiveSink sink]
    (.putByte sink (byte 7))
    (.putLong sink l))
  Boolean
  (hash-into [b ^PrimitiveSink sink]
    (.putByte sink (byte 8))
    (.putBoolean sink b))
  List
  (hash-into [xs ^PrimitiveSink sink]
    (.putByte sink (byte 9))
    (doseq [x xs]
      (hash-into x sink)))
  Map
  (hash-into [m ^PrimitiveSink sink]
    (.putByte sink (byte 10))
    (doseq [[k v] (into (sorted-map) m)]
      (hash-into k sink)
      (hash-into v sink))))


(defn generate
  "Calculates a SHA256 hash for `resource`.

  The hash need to be cryptographic because otherwise it would be possible to
  introduce a resource into Blaze which has the same hash as the target
  resource, overwriting it."
  ^HashCode [resource]
  (let [hasher (.newHasher (Hashing/sha256))]
    (hash-into resource hasher)
    (.hash hasher)))


(defn encode
  "Returns a byte array of length 32 (256 bit) which represents the hash."
  ^bytes [hash]
  (.asBytes ^HashCode hash))


(defn decode [bytes]
  (some-> bytes HashCode/fromBytes))
