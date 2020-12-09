(ns blaze.db.bytes
  (:import
    [java.util Arrays])
  (:refer-clojure :exclude [= empty]))


(set! *warn-on-reflection* true)


(defn =
  "Compares two byte arrays for equivalence."
  {:arglists '([a b])}
  [^bytes a ^bytes b]
  (Arrays/equals a b))


(def ^{:tag 'bytes} empty
  "The empty byte array (immutable)."
  (byte-array 0))
