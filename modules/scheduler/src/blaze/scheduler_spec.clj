(ns blaze.scheduler-spec
  (:require
    [blaze.scheduler :as sched]
    [blaze.scheduler.spec]
    [clojure.spec.alpha :as s]
    [java-time :as time])
  (:import
    [java.util.concurrent ScheduledFuture]))


(s/fdef sched/schedule-at-fixed-rate
  :args (s/cat :scheduler :blaze/scheduler :f fn?
               :initial-delay time/duration? :period time/duration?)
  :ret #(instance? ScheduledFuture %))
