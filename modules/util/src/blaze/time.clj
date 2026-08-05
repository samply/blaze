(ns blaze.time
  "Functions for creating and calculating with temporal entities.

  The `clojure.java-time` counterparts of these functions are either routed
  through a conversion graph that is resolved at runtime, on every call,
  allocating between 1.3 and 2 KiB per call, or are varargs functions that
  allocate an `ArraySeq` for their rest args. The functions here delegate to
  `java.time` directly."
  (:import
   [java.time Clock Duration Instant OffsetDateTime]
   [java.time.temporal Temporal TemporalAmount TemporalUnit]))

(set! *warn-on-reflection* true)

(defn instant
  "Returns the current instant, either from the system clock or from `clock`."
  (^Instant [] (Instant/now))
  (^Instant [^Clock clock] (Instant/now clock)))

(defn offset-date-time
  "Returns the current date-time, either from the system clock using the default
  offset or from `clock` using its offset."
  (^OffsetDateTime [] (OffsetDateTime/now))
  (^OffsetDateTime [^Clock clock] (OffsetDateTime/now clock)))

(defn to-instant
  "Returns the instant of `date-time`."
  ^Instant [^OffsetDateTime date-time]
  (.toInstant date-time))

(defn duration
  "Returns the duration between `start` and `end`.

  The duration is negative if `end` is before `start`."
  ^Duration [^Temporal start ^Temporal end]
  (Duration/between start end))

(defn as-seconds
  "Returns the number of whole seconds in `duration`."
  [^Duration duration]
  (.toSeconds duration))

(defn as-millis
  "Returns the number of whole milliseconds in `duration`."
  [^Duration duration]
  (.toMillis duration))

(defn plus
  "Returns `date-time` with `amount` added."
  ^OffsetDateTime [^OffsetDateTime date-time ^TemporalAmount amount]
  (.plus date-time amount))

(defn plus-unit
  "Returns `temporal` with `amount` units of `unit` added.

  The `amount` can be negative. Returns `temporal` unchanged if `amount` is
  zero, in order to avoid the allocation of a new temporal.

  Chain calls to add more than one unit, coarsest unit first. The month addition
  clamps an overflowing day-of-month and so doesn't commute with the other
  units."
  ^Temporal [^Temporal temporal amount ^TemporalUnit unit]
  (if (zero? amount)
    temporal
    (.plus temporal (long amount) unit)))
