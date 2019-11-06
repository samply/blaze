(ns blaze.thread-pool-executor-collector
  (:require
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [io.prometheus.client
     Collector Collector$Describable GaugeMetricFamily CounterMetricFamily]
    [java.util.concurrent BlockingQueue ThreadPoolExecutor]))


(defn thread-pool-executor-collector [executors]
  (proxy [Collector Collector$Describable] []
    (collect []
      [(let [mf (GaugeMetricFamily.
                  "thread_pool_executor_active_count"
                  "Returns the approximate number of threads that are actively executing tasks."
                  ["name"])]
         (doseq [[name pool] executors]
           (.addMetric mf [name] (.getActiveCount ^ThreadPoolExecutor pool)))
         mf)
       (let [mf (CounterMetricFamily.
                  "thread_pool_executor_completed_tasks_total"
                  "Returns the approximate total number of tasks that have completed execution."
                  ["name"])]
         (doseq [[name pool] executors]
           (.addMetric mf [name] (.getCompletedTaskCount ^ThreadPoolExecutor pool)))
         mf)
       (let [mf (GaugeMetricFamily.
                  "thread_pool_executor_core_pool_size"
                  "Returns the core number of threads."
                  ["name"])]
         (doseq [[name pool] executors]
           (.addMetric mf [name] (.getCorePoolSize ^ThreadPoolExecutor pool)))
         mf)
       (let [mf (GaugeMetricFamily.
                  "thread_pool_executor_largest_pool_size"
                  "Returns the largest number of threads that have ever simultaneously been in the pool."
                  ["name"])]
         (doseq [[name pool] executors]
           (.addMetric mf [name] (.getLargestPoolSize ^ThreadPoolExecutor pool)))
         mf)
       (let [mf (GaugeMetricFamily.
                  "thread_pool_executor_maximum_pool_size"
                  "Returns the maximum allowed number of threads."
                  ["name"])]
         (doseq [[name pool] executors]
           (.addMetric mf [name] (.getMaximumPoolSize ^ThreadPoolExecutor pool)))
         mf)
       (let [mf (GaugeMetricFamily.
                  "thread_pool_executor_pool_size"
                  "Returns the current number of threads in the pool."
                  ["name"])]
         (doseq [[name pool] executors]
           (.addMetric mf [name] (.getPoolSize ^ThreadPoolExecutor pool)))
         mf)
       (let [mf (GaugeMetricFamily.
                  "thread_pool_executor_queue_size"
                  "Returns the current number of tasks in the queue."
                  ["name"])]
         (doseq [[name pool] executors]
           (.addMetric mf [name] (.size ^BlockingQueue (.getQueue ^ThreadPoolExecutor pool))))
         mf)])

    (describe []
      (.collect ^Collector this))))


(defmethod ig/init-key :blaze/thread-pool-executor-collector
  [_ {:keys [executors]}]
  (log/info "Init thread pool executor collector.")
  (->> executors
       (map
         (fn [[key executor]]
           [(namespace key) executor]))
       (thread-pool-executor-collector)))


(derive :blaze/thread-pool-executor-collector :blaze.metrics/collector)
