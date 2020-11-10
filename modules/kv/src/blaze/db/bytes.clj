(ns blaze.db.bytes
  (:import
    [com.google.common.primitives Bytes]
    [java.util Arrays])
  (:refer-clojure :exclude [= < <= > >= concat empty]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn =
  "Compares two byte arrays for equivalence."
  {:arglists '([a b])}
  [^bytes a ^bytes b]
  (Arrays/equals a b))


(defn starts-with?
  "Test whether `bs` starts with `prefix`."
  {:arglists '([bs prefix] [bs prefix length])}
  ([bs ^bytes prefix]
   (starts-with? bs prefix (alength prefix)))
  ([^bytes bs ^bytes prefix length]
   (and (clojure.core/<= ^int length (alength bs))
        (Arrays/equals bs 0 ^int length prefix 0 ^int length))))


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


(defn >
  "Compares two byte arrays lexicographically, numerically treating elements as
  unsigned.

  Returns non-nil if the first array is lexicographically greater than the
  second array, otherwise false."
  {:arglists '([a b])}
  [^bytes a ^bytes b]
  (clojure.core/> (Arrays/compareUnsigned a b) 0))


(defn >=
  "Compares two byte arrays lexicographically, numerically treating elements as
  unsigned.

  Returns non-nil if the first array is lexicographically greater than or equal
  the second array, otherwise false."
  {:arglists '([a b])}
  [^bytes a ^bytes b]
  (clojure.core/>= (Arrays/compareUnsigned a b) 0))


(def ^{:tag 'bytes} empty
  "The empty byte array (immutable)."
  (byte-array 0))


(defn concat [& byte-arrays]
  (if (seq byte-arrays)
    (Bytes/concat (into-array byte-arrays))
    empty))
