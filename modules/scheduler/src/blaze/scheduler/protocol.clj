(ns blaze.scheduler.protocol)


(defprotocol Scheduler
  (-submit [scheduler f])
  (-schedule-at-fixed-rate [scheduler f initial-delay period]))
