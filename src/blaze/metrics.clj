(ns blaze.metrics
  (:import
    [io.prometheus.client Collector GaugeMetricFamily CounterMetricFamily]
    [java.util.concurrent ForkJoinPool]))


(set! *warn-on-reflection* true)


(defn fork-join-pool-collector [pools]
  (proxy [Collector] []
    (collect []
      [(let [mf (GaugeMetricFamily.
                  "fork_join_pool_queued_task_count"
                  "Returns an estimate of the total number of tasks currently held in queues by worker threads (but not including tasks submitted to the pool that have not begun executing)."
                  ["name"])]
         (doseq [[name pool] pools]
           (.addMetric mf [name] (.getQueuedTaskCount ^ForkJoinPool pool)))
         mf)
       (let [mf (GaugeMetricFamily.
                  "fork_join_pool_parallelism"
                  "Returns the targeted parallelism level of this pool."
                  ["name"])]
         (doseq [[name pool] pools]
           (.addMetric mf [name] (.getParallelism ^ForkJoinPool pool)))
         mf)
       (let [mf (GaugeMetricFamily.
                  "fork_join_pool_active_thread_count"
                  "Returns an estimate of the number of threads that are currently stealing or executing tasks. This method may overestimate the number of active threads."
                  ["name"])]
         (doseq [[name pool] pools]
           (.addMetric mf [name] (.getActiveThreadCount ^ForkJoinPool pool)))
         mf)
       (let [mf (GaugeMetricFamily.
                  "fork_join_pool_queued_submission_count"
                  "Returns an estimate of the number of tasks submitted to this pool that have not yet begun executing."
                  ["name"])]
         (doseq [[name pool] pools]
           (.addMetric mf [name] (.getQueuedSubmissionCount ^ForkJoinPool pool)))
         mf)
       (let [mf (GaugeMetricFamily.
                  "fork_join_pool_running_thread_count"
                  "Returns an estimate of the number of worker threads that are not blocked waiting to join tasks or for other managed synchronization. This method may overestimate the number of running threads."
                  ["name"])]
         (doseq [[name pool] pools]
           (.addMetric mf [name] (.getRunningThreadCount ^ForkJoinPool pool)))
         mf)
       (let [mf (CounterMetricFamily.
                  "fork_join_pool_steal_count_total"
                  "Returns an estimate of the total number of tasks stolen from one thread's work queue by another. The reported value underestimates the actual total number of steals when the pool is not quiescent."
                  ["name"])]
         (doseq [[name pool] pools]
           (.addMetric mf [name] (.getStealCount ^ForkJoinPool pool)))
         mf)
       (let [mf (CounterMetricFamily.
                  "fork_join_pool_pool_size"
                  "Returns the number of worker threads that have started but not yet terminated."
                  ["name"])]
         (doseq [[name pool] pools]
           (.addMetric mf [name] (.getPoolSize ^ForkJoinPool pool)))
         mf)])))
