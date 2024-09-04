(ns blaze.scheduler.spec
  (:require
   [blaze.scheduler.protocol :as p]
   [clojure.spec.alpha :as s])
  (:import
   [java.util.concurrent Future]))

(defn scheduler? [x]
  (satisfies? p/Scheduler x))

(s/def :blaze/scheduler
  scheduler?)

(s/def :blaze.scheduler/future
  #(instance? Future %))
