(ns blaze.elm.date-time-spec
  (:require
    [blaze.elm.date-time :as date-time]
    [clojure.spec.alpha :as s]))


(s/fdef date-time/period
  :args (s/cat :years number? :months number? :millis number?))
