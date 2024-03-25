(ns blaze.job-scheduler.spec
  (:require
   [blaze.job-scheduler.protocols :as p]
   [clojure.spec.alpha :as s]))

(s/def :blaze/job-scheduler
  #(satisfies? p/JobScheduler %))
