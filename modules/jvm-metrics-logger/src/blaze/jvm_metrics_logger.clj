(ns blaze.jvm-metrics-logger
  (:require
   [blaze.jvm-metrics-logger.impl :as impl]
   [blaze.jvm-metrics-logger.spec]
   [blaze.module :as m]
   [blaze.scheduler :as sched]
   [blaze.scheduler.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [java-time.api :as time]
   [taoensso.timbre :as log])
  (:import
   [java.time Duration]))

(set! *warn-on-reflection* true)

(defmethod m/pre-init-spec :blaze/jvm-metrics-logger [_]
  (s/keys :req-un [:blaze/scheduler]
          :opt-un [:blaze.jvm-metrics-logger/interval
                   :blaze.jvm-metrics-logger/warn-factor
                   :blaze.jvm-metrics-logger/warn-threshold]))

(defn- init-msg [interval warn-factor warn-threshold]
  (format "Start JVM metrics logger with an interval of %s, a warn factor of %d and a warn threshold of %d%%" interval warn-factor warn-threshold))

(defmethod ig/init-key :blaze/jvm-metrics-logger
  [_ {:keys [scheduler interval warn-factor warn-threshold]
      :or {interval (time/minutes 5)
           warn-factor 5
           warn-threshold 80}}]
  (log/info (init-msg interval warn-factor warn-threshold))
  (let [tick-interval (.dividedBy ^Duration interval (long warn-factor))
        tick-counter (atom 0)]
    {:future
     (sched/schedule-at-fixed-rate
      scheduler
      #(impl/run-tick! warn-threshold warn-factor tick-counter)
      tick-interval
      tick-interval)}))

(defmethod ig/halt-key! :blaze/jvm-metrics-logger
  [_ {:keys [future]}]
  (log/info "Stop JVM metrics logger")
  (sched/cancel future false))
