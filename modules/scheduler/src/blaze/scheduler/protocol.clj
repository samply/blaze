(ns blaze.scheduler.protocol)

(defprotocol Scheduler
  (-schedule-at-fixed-rate [scheduler f initial-delay period]))
