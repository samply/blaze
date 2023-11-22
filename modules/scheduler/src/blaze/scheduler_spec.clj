(ns blaze.scheduler-spec
  (:require
   [blaze.scheduler :as sched]
   [blaze.scheduler.spec]
   [clojure.spec.alpha :as s]
   [java-time.api :as time]))

(s/fdef sched/schedule-at-fixed-rate
  :args (s/cat :scheduler :blaze/scheduler :f fn?
               :initial-delay time/duration? :period time/duration?)
  :ret :blaze.scheduler/future)

(s/fdef sched/cancel
  :args (s/cat :future :blaze.scheduler/future :interrupt-if-running? boolean?)
  :ret boolean?)
