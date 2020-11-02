(ns blaze.db.bytes
  (:import
    [java.util Arrays])
  (:refer-clojure :exclude [= < <= > >= empty]))


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
