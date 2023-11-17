(ns blaze.db.impl.bytes
  (:refer-clojure :exclude [empty]))

(def ^{:tag 'bytes} empty
  "The empty byte array (immutable)."
  (byte-array 0))
