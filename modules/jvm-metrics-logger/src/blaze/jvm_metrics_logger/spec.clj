(ns blaze.jvm-metrics-logger.spec
  (:require
   [blaze.jvm-metrics-logger :as-alias jml]
   [clojure.spec.alpha :as s]
   [java-time.api :as time]))

(s/def ::jml/interval
  time/duration?)

(s/def ::jml/warn-factor
  (s/int-in 1 1000))

(s/def ::jml/warn-threshold
  (s/int-in 1 100))
