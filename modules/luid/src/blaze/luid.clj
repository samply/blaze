(ns blaze.luid
  (:refer-clojure :exclude [next])
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

(defprotocol Generator
  (-head [_])
  (-next [_]))

(defn generator? [x]
  (satisfies? Generator x))

(defn head [generator]
  (-head generator))

(defn next [generator]
  (-next generator))

(deftype LuidGenerator [^long timestamp ^long entropy]
  Generator
  (-head [_]
    (impl/luid timestamp entropy))
  (-next [_]
    (if (= 0xFFFFFFFFF entropy)
      (LuidGenerator. (inc timestamp) 0)
      (LuidGenerator. timestamp (inc entropy)))))

(defn generator [clock rng]
  (->LuidGenerator (.millis ^Clock clock) (entropy rng)))
