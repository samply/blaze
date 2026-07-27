(ns blaze.scheduler.test-util
  (:require
   [blaze.scheduler.protocol :as p]
   [integrant.core :as ig])
  (:import
   [java.util.concurrent CompletableFuture ConcurrentLinkedQueue]))

(set! *warn-on-reflection* true)

(deftype ManualScheduler [^ConcurrentLinkedQueue submitted
                          ^ConcurrentLinkedQueue scheduled]
  p/Scheduler
  (-submit [_ f]
    (let [future (CompletableFuture.)]
      (.add submitted [future f])
      future))

  (-schedule-at-fixed-rate [_ f _initial-delay _period]
    (let [future (CompletableFuture.)]
      (.add scheduled [future f])
      future)))

(defmethod ig/init-key :blaze.test/manual-scheduler
  [_ _]
  (ManualScheduler. (ConcurrentLinkedQueue.) (ConcurrentLinkedQueue.)))

(defn- cancelled? [^CompletableFuture future]
  (.isCancelled future))

(defn run-all!
  "Runs all functions submitted to the manual `scheduler` on the calling thread,
  including functions submitted by the functions being run.

  Used to deterministically control when asynchronous work happens in tests."
  [scheduler]
  (let [^ConcurrentLinkedQueue queue (.-submitted ^ManualScheduler scheduler)]
    (loop []
      (when-let [[^CompletableFuture future f] (.poll queue)]
        (when-not (cancelled? future)
          (.complete future (f)))
        (recur)))))

(defn tick!
  "Runs one round of all functions scheduled at a fixed rate on the manual
  `scheduler` on the calling thread, followed by `run-all!`.

  Used to deterministically control when periodic work happens in tests."
  [scheduler]
  (run!
   (fn [[future f]]
     (when-not (cancelled? future)
       (f)))
   (.-scheduled ^ManualScheduler scheduler))
  (run-all! scheduler))
