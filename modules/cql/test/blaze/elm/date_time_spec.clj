(ns blaze.elm.date-time-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.elm.date-time :as date-time]
   [blaze.fhir.spec.type.system-spec]
   [blaze.util-spec]
   [clojure.spec.alpha :as s])
  (:import
   [java.time OffsetDateTime ZoneOffset]
   [java.time.temporal ChronoUnit]))

(s/fdef date-time/period
  :args (s/cat :years number? :months number? :millis number?))

(s/fdef date-time/same-offset?
  :args (s/cat :o1 (s/nilable #(instance? ZoneOffset %))
               :o2 (s/nilable #(instance? ZoneOffset %))
               :now (s/nilable #(instance? OffsetDateTime %)))
  :ret boolean?)

(s/fdef date-time/normalize-offset?
  :args (s/cat :x any? :y any?
               :now (s/nilable #(instance? OffsetDateTime %))
               :precision (s/nilable #(instance? ChronoUnit %)))
  :ret boolean?)

(s/fdef date-time/normalize-offset
  :args (s/cat :x any? :now (s/nilable #(instance? OffsetDateTime %)))
  :ret any?)
