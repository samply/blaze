(ns blaze.time-spec
  (:require
   [blaze.time :as bt]
   [clojure.spec.alpha :as s])
  (:import
   [java.time Clock Instant OffsetDateTime]))

(s/fdef bt/instant
  :args (s/cat :clock (s/? #(instance? Clock %)))
  :ret #(instance? Instant %))

(s/fdef bt/offset-date-time
  :args (s/cat :clock (s/? #(instance? Clock %)))
  :ret #(instance? OffsetDateTime %))
