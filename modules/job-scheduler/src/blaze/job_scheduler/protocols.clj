(ns blaze.job-scheduler.protocols)

(defprotocol JobScheduler
  (-submit [_ task]))
