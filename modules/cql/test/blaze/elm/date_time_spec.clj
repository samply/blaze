(ns blaze.elm.date-time-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.elm.date-time :as date-time]
   [blaze.fhir.spec.type.system-spec]
   [blaze.util-spec]
   [clojure.spec.alpha :as s]))

(s/fdef date-time/period
  :args (s/cat :years number? :months number? :millis number?))
