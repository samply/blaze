(ns blaze.scheduler.spec
  (:require
   [blaze.scheduler.protocol :as p]
   [clojure.spec.alpha :as s])
  (:import
   [java.util.concurrent Future]))

(s/def :blaze/scheduler
  #(satisfies? p/Scheduler %))

(s/def :blaze.scheduler/future
  #(instance? Future %))
