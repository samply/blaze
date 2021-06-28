(ns blaze.scheduler-spec
  (:require
    [blaze.scheduler :as sched]
    [blaze.scheduler.spec]
    [clojure.spec.alpha :as s]
    [java-time :as jt])
  (:import
    [java.util.concurrent ScheduledFuture]))


(s/fdef sched/schedule-at-fixed-rate
  :args (s/cat :scheduler :blaze/scheduler :f fn?
               :initial-delay jt/duration? :period jt/duration?)
  :ret #(instance? ScheduledFuture %))
