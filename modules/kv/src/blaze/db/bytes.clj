(ns blaze.db.bytes
  (:refer-clojure :exclude [= empty])
  (:import
    [java.util Arrays]))


(set! *warn-on-reflection* true)


(defn =
  "Compares two byte arrays for equivalence."
  {:arglists '([a b])}
  [^bytes a ^bytes b]
  (Arrays/equals a b))


(def ^{:tag 'bytes} empty
  "The empty byte array (immutable)."
  (byte-array 0))
