(ns blaze.db.impl.bytes
  (:import
    [java.util Arrays])
  (:refer-clojure :exclude [= < <=]))


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
