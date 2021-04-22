(ns blaze.scheduler.spec
  (:require
    [blaze.scheduler.protocol :as p]
    [clojure.spec.alpha :as s]))


(s/def :blaze/scheduler
  #(satisfies? p/Scheduler %))
