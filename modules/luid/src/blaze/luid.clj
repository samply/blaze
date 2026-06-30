(ns blaze.luid
  "Locally Unique Identifiers (LUIDs).

  A LUID is a compact, lexicographically sortable identifier made from a
  millisecond timestamp and a random entropy component. It is encoded as a base
  32 string of length 16.

  Two ways to obtain LUIDs are provided:

   * `luid` creates a single LUID, drawing one long from the RNG, and
   * `generator` creates a `Generator` that yields a strictly increasing
     sequence of LUIDs while drawing only a single long from the RNG in total,
     at creation time."
  (:refer-clojure :exclude [next])
  (:require
   [blaze.luid.impl :as impl])
  (:import
   [java.time Clock]
   [java.util Random]))

(set! *warn-on-reflection* true)

(defn- entropy
  "Draws a single long from `rng` and returns its lower 36 bit."
  [rng]
  (bit-and (.nextLong ^Random rng) 0xFFFFFFFFF))

(defn luid
  "Creates a LUID from the current time of `clock` and one random long from `rng`.

  A LUID consists of a 44 bit wide timestamp component followed by a 36 bit wide
  entropy component. The total width is 80 bit or 10 byte. The timestamp is the
  current millisecond of `clock` and the entropy is the lower 36 bit of a single
  long drawn from `rng`.

  Exactly one long is fetched from `rng`.

  The LUID is encoded as string with base 32 so the string length is 16."
  [clock rng]
  (impl/luid (.millis ^Clock clock) (entropy rng)))

(defprotocol Generator
  "A generator of a strictly increasing sequence of LUIDs.

  See `head` and `next`."
  (-head [_])
  (-next [_]))

(defn generator?
  "Returns true if `x` is a LUID `Generator`."
  [x]
  (satisfies? Generator x))

(defn head
  "Returns the current LUID of `generator`."
  [generator]
  (-head generator))

(defn next
  "Returns a new generator whose head is the LUID following that of `generator`.

  Doesn't draw from the RNG. The entropy component is incremented and, on
  overflow, rolls over into the timestamp component."
  [generator]
  (-next generator))

(deftype LuidGenerator [^long timestamp ^long entropy]
  Generator
  (-head [_]
    (impl/luid timestamp entropy))
  (-next [_]
    (if (= 0xFFFFFFFFF entropy)
      (LuidGenerator. (inc timestamp) 0)
      (LuidGenerator. timestamp (inc entropy)))))

(defn generator
  "Creates a LUID `Generator` from the current time of `clock` and one random
  long from `rng`.

  Exactly one long is fetched from `rng`; its lower 36 bit seed the entropy of
  the first LUID. The generator doesn't retain a reference to `rng`. Subsequent
  LUIDs obtained via `next` increment the entropy (rolling over into the
  timestamp) and never draw from `rng` again."
  [clock rng]
  (->LuidGenerator (.millis ^Clock clock) (entropy rng)))
