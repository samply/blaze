(ns blaze.luid
  (:require
    ;; Needed for Cloverage. See: https://github.com/cloverage/cloverage/issues/312
    [clojure.core])
  (:import
    [java.time Clock]
    [java.util Random]
    [java.util.concurrent ThreadLocalRandom]
    [com.google.common.io BaseEncoding])
  (:refer-clojure :exclude [next]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn internal-luid [^long timestamp ^long entropy]
  (let [high (BigInteger/valueOf (bit-and timestamp 0xFFFFFFFFFFF))
        low (BigInteger/valueOf (bit-and entropy 0xFFFFFFFFF))
        bs (-> (.add (.shiftLeft high 36) low)
               (.add (.shiftLeft BigInteger/ONE 80))
               (.toByteArray))]
    (.encode (BaseEncoding/base32) bs 1 10)))


(defn luid
  "Creates a LUID.

  A LUID consists of a 44 bit wide timestamp component followed by a 36 bit wide
  entropy component. The total width is 80 bit or 10 byte.

  The string encoding is base 32.
  "
  ([]
   (luid (Clock/systemUTC) (ThreadLocalRandom/current)))
  ([clock rng]
   (internal-luid (.millis ^Clock clock) (.nextLong ^Random rng))))


(defn init
  ([]
   (init (Clock/systemUTC) (ThreadLocalRandom/current)))
  ([clock rng]
   [(.millis ^Clock clock) (.nextLong ^Random rng)]))


(defn next [[timestamp entropy]]
  (let [entropy (inc ^long entropy)]
    [[timestamp entropy] (internal-luid timestamp entropy)]))
