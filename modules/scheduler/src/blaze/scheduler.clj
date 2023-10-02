(ns blaze.scheduler
  (:require
    [blaze.executors :as ex]
    [blaze.scheduler.protocol :as p]
    [blaze.scheduler.spec]
    [integrant.core :as ig]
    [java-time.api :as time]
    [taoensso.timbre :as log])
  (:import
    [java.util.concurrent Future ScheduledExecutorService TimeUnit]))


(set! *warn-on-reflection* true)


(defn submit
  "Submits the function `f` to be called later.

  Returns a future that can be used in `cancel`."
  [scheduler f]
  (p/-submit scheduler f))


(defn schedule-at-fixed-rate
  "Schedules the function `f` to be called at a rate of `period` with an
  `initial-delay`.

  Returns a future that can be used in `cancel`."
  [scheduler f initial-delay period]
  (p/-schedule-at-fixed-rate scheduler f initial-delay period))


(defn cancel [future interrupt-if-running?]
  (.cancel ^Future future interrupt-if-running?))


(extend-protocol p/Scheduler
  ScheduledExecutorService
  (-submit [scheduler f]
    (.submit scheduler ^Runnable f))

  (-schedule-at-fixed-rate [scheduler f initial-delay period]
    (.scheduleAtFixedRate
      scheduler
      f
      (time/as initial-delay :nanos)
      (time/as period :nanos)
      TimeUnit/NANOSECONDS)))


(defmethod ig/init-key :blaze/scheduler
  [_ _]
  (log/info "Start scheduler")
  (ex/scheduled-pool 4 "scheduler-%d"))


(defmethod ig/halt-key! :blaze/scheduler
  [_ scheduler]
  (log/info "Stopping scheduler...")
  (ex/shutdown! scheduler)
  (if (ex/await-termination scheduler 10 TimeUnit/SECONDS)
    (log/info "Scheduler was stopped successfully")
    (log/warn "Got timeout while stopping the scheduler")))


(derive :blaze/scheduler :blaze.metrics/thread-pool-executor)
