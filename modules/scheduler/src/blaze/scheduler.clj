(ns blaze.scheduler
  (:require
    [blaze.executors :as ex]
    [blaze.scheduler.protocol :as p]
    [blaze.scheduler.spec]
    [integrant.core :as ig]
    [java-time.api :as time]
    [taoensso.timbre :as log])
  (:import
    [java.util.concurrent Executors Future ScheduledExecutorService TimeUnit]))


(set! *warn-on-reflection* true)


(defn schedule-at-fixed-rate [scheduler f initial-delay period]
  (p/-schedule-at-fixed-rate scheduler f initial-delay period))


(defn cancel [future interrupt-if-running?]
  (.cancel ^Future future interrupt-if-running?))


(extend-protocol p/Scheduler
  ScheduledExecutorService
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
  (Executors/newSingleThreadScheduledExecutor))


(defmethod ig/halt-key! :blaze/scheduler
  [_ scheduler]
  (log/info "Stopping scheduler...")
  (ex/shutdown! scheduler)
  (if (ex/await-termination scheduler 10 TimeUnit/SECONDS)
    (log/info "Scheduler was stopped successfully")
    (log/warn "Got timeout while stopping the scheduler")))
