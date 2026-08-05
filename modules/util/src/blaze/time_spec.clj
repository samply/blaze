(ns blaze.time-spec
  (:require
   [blaze.time :as bt]
   [clojure.spec.alpha :as s])
  (:import
   [java.time Clock Duration Instant OffsetDateTime]
   [java.time.temporal Temporal TemporalAmount TemporalUnit]))

(s/fdef bt/instant
  :args (s/cat :clock (s/? #(instance? Clock %)))
  :ret #(instance? Instant %))

(s/fdef bt/offset-date-time
  :args (s/cat :clock (s/? #(instance? Clock %)))
  :ret #(instance? OffsetDateTime %))

(s/fdef bt/to-instant
  :args (s/cat :date-time #(instance? OffsetDateTime %))
  :ret #(instance? Instant %))

(s/fdef bt/duration
  :args (s/cat :start #(instance? Temporal %) :end #(instance? Temporal %))
  :ret #(instance? Duration %))

(s/fdef bt/as-seconds
  :args (s/cat :duration #(instance? Duration %))
  :ret int?)

(s/fdef bt/as-millis
  :args (s/cat :duration #(instance? Duration %))
  :ret int?)

(s/fdef bt/plus
  :args (s/cat :date-time #(instance? OffsetDateTime %)
               :amount #(instance? TemporalAmount %))
  :ret #(instance? OffsetDateTime %))

(s/fdef bt/plus-unit
  :args (s/cat :temporal #(instance? Temporal %) :amount int?
               :unit #(instance? TemporalUnit %))
  :ret #(instance? Temporal %))
