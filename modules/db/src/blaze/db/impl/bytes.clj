(ns blaze.db.impl.bytes
  (:import
    [com.google.common.primitives Bytes]
    [java.util Arrays])
  (:refer-clojure :exclude [= < <= concat empty]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn = [^bytes a ^bytes b]
  (Arrays/equals a b))


(defn prefix= [^bytes a ^bytes b ^long size]
  (Arrays/equals a 0 size b 0 size))


(defn < [^bytes a ^bytes b]
  (clojure.core/< (Arrays/compareUnsigned a b) 0))


(defn <= [^bytes a ^bytes b]
  (clojure.core/<= (Arrays/compareUnsigned a b) 0))


(defn starts-with? [^bytes b ^bytes sub]
  (Arrays/equals b 0 (alength sub) sub 0 (alength sub)))


(def empty (byte-array 0))


(defn concat [byte-arrays]
  (if (seq byte-arrays)
    (Bytes/concat (into-array byte-arrays))
    empty))
