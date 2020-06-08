(ns blaze.db.impl.bytes
  (:import
    [com.google.common.primitives Bytes]
    [java.util Arrays])
  (:refer-clojure :exclude [= < <= concat empty]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn =
  "Compares two byte arrays for equivalence."
  {:arglists '([a b])}
  [^bytes a ^bytes b]
  (Arrays/equals a b))


(defn starts-with?
  "Test whether `bs` start with `prefix`."
  {:arglists '([bs prefix])}
  [^bytes bs ^bytes prefix]
  (let [prefix-length (alength prefix)]
    (and (clojure.core/<= prefix-length (alength bs))
         (Arrays/equals bs 0 prefix-length prefix 0 prefix-length))))


(defn <
  "Compares two byte arrays lexicographically, numerically treating elements as
  unsigned.

  Returns non-nil if the first array is lexicographically less than the second
  array, otherwise false."
  {:arglists '([a b])}
  [^bytes a ^bytes b]
  (clojure.core/< (Arrays/compareUnsigned a b) 0))


(defn <=
  "Compares two byte arrays lexicographically, numerically treating elements as
  unsigned.

  Returns non-nil if the first array is lexicographically less than or equal to
  the second array, otherwise false."
  {:arglists '([a b])}
  [^bytes a ^bytes b]
  (clojure.core/<= (Arrays/compareUnsigned a b) 0))


(def empty
  "Returns an empty byte array of length 0."
  (byte-array 0))


(defn concat
  "Concatenates a seq of byte arrays. Returns an empty byte array on empty seq."
  [byte-arrays]
  (if (seq byte-arrays)
    (Bytes/concat (into-array byte-arrays))
    empty))
