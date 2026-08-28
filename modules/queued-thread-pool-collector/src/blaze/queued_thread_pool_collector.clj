(ns blaze.queued-thread-pool-collector
  (:require
   [blaze.metrics.core :as metrics]
   [blaze.module :as m]
   [blaze.queued-thread-pool-collector.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [taoensso.timbre :as log])
  (:import
   [org.eclipse.jetty.util.thread QueuedThreadPool]))

(set! *warn-on-reflection* true)

(defn- gauge-metric [name help pool-name value]
  (metrics/gauge-metric
   name help ["name"]
   [{:label-values [pool-name] :value value}]))

(defn- queued-thread-pool-collector [^QueuedThreadPool pool]
  (metrics/collector
    (let [name (.getName pool)]
      [(gauge-metric
        "queued_thread_pool_threads"
        "Returns the number of threads in the pool."
        name (.getThreads pool))
       (gauge-metric
        "queued_thread_pool_idle_threads"
        "Returns the number of idle threads in the pool."
        name (.getIdleThreads pool))
       (gauge-metric
        "queued_thread_pool_busy_threads"
        "Returns the number of busy threads in the pool."
        name (.getBusyThreads pool))
       (gauge-metric
        "queued_thread_pool_queue_size"
        "Returns the number of jobs in the queue waiting for a thread."
        name (.getQueueSize pool))
       (gauge-metric
        "queued_thread_pool_utilization_rate"
        "Returns the ratio of busy threads to the threads available to the application, so excluding the threads leased by acceptors and selectors."
        name (.getUtilizationRate pool))
       (gauge-metric
        "queued_thread_pool_low_on_threads"
        "Returns one if the pool is low on threads and zero otherwise."
        name (if (.isLowOnThreads pool) 1 0))])))

(defmethod m/pre-init-spec :blaze/queued-thread-pool-collector [_]
  (s/keys :req-un [::thread-pool]))

(defmethod ig/init-key :blaze/queued-thread-pool-collector
  [_ {:keys [thread-pool]}]
  (log/info "Init queued thread pool collector")
  (queued-thread-pool-collector thread-pool))

(derive :blaze/queued-thread-pool-collector :blaze.metrics/collector)
