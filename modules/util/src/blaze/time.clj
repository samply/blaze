(ns blaze.time
  "Functions for creating temporal entities.

  The `clojure.java-time` counterparts of these functions define only their
  zero-argument arity explicitly. Every other arity is routed through a
  conversion graph that is resolved at runtime, on every call, allocating about
  2 KiB per call. The functions here delegate to `java.time` directly."
  (:import
   [java.time Clock Instant OffsetDateTime]))

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
