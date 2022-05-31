(ns blaze.luid
  (:require
    [blaze.luid.impl :as impl])
  (:import
    [java.time Clock]
    [java.util Random]))


(set! *warn-on-reflection* true)


(defn- entropy [rng]
  (bit-and (.nextLong ^Random rng) 0xFFFFFFFFF))


(defn luid
  "Creates a LUID.

  A LUID consists of a 44 bit wide timestamp component followed by a 36 bit wide
  entropy component. The total width is 80 bit or 10 byte.

  The LUID is encoded as string with base 32 so the string length is 16."
  [clock rng]
  (impl/luid (.millis ^Clock clock) (entropy rng)))


(defn- successive-luids* [^long timestamp ^long entropy]
  (cons (impl/luid timestamp entropy)
        (lazy-seq
          (if (= 0xFFFFFFFFF entropy)
            (successive-luids* (inc timestamp) 0)
            (successive-luids* timestamp (inc entropy))))))


(defn successive-luids [clock rng]
  (successive-luids* (.millis ^Clock clock) (entropy rng)))
