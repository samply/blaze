(ns blaze.thread-pool-executor-collector
  (:require
    [blaze.thread-pool-executor-collector.spec]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [io.prometheus.client Collector GaugeMetricFamily CounterMetricFamily]
    [java.util.concurrent BlockingQueue ThreadPoolExecutor]))


(set! *warn-on-reflection* true)


(defn- gauge-metric-family [name help f executors]
  (let [mf (GaugeMetricFamily. ^String name ^String help ["name"])]
    (run! (fn [[name pool]] (.addMetric mf [name] (f pool))) executors)
    mf))


(defn- counter-metric-family [name help f executors]
  (let [mf (CounterMetricFamily. ^String name ^String help ["name"])]
    (run! (fn [[name pool]] (.addMetric mf [name] (f pool))) executors)
    mf))


(defn- thread-pool-executor-collector [executors]
  (proxy [Collector] []
    (collect []
      [(gauge-metric-family
         "thread_pool_executor_active_count"
         "Returns the approximate number of threads that are actively executing tasks."
         #(.getActiveCount ^ThreadPoolExecutor %)
         executors)
       (counter-metric-family
         "thread_pool_executor_completed_tasks_total"
         "Returns the approximate total number of tasks that have completed execution."
         #(.getCompletedTaskCount ^ThreadPoolExecutor %)
         executors)
       (gauge-metric-family
         "thread_pool_executor_core_pool_size"
         "Returns the core number of threads."
         #(.getCorePoolSize ^ThreadPoolExecutor %)
         executors)
       (gauge-metric-family
         "thread_pool_executor_largest_pool_size"
         "Returns the largest number of threads that have ever simultaneously been in the pool."
         #(.getLargestPoolSize ^ThreadPoolExecutor %)
         executors)
       (gauge-metric-family
         "thread_pool_executor_maximum_pool_size"
         "Returns the maximum allowed number of threads."
         #(.getMaximumPoolSize ^ThreadPoolExecutor %)
         executors)
       (gauge-metric-family
         "thread_pool_executor_pool_size"
         "Returns the current number of threads in the pool."
         #(.getPoolSize ^ThreadPoolExecutor %)
         executors)
       (gauge-metric-family
         "thread_pool_executor_queue_size"
         "Returns the current number of tasks in the queue."
         #(.size ^BlockingQueue (.getQueue ^ThreadPoolExecutor %))
         executors)])))


(defmethod ig/pre-init-spec :blaze/thread-pool-executor-collector [_]
  (s/keys :req-un [::executors]))


(defmethod ig/init-key :blaze/thread-pool-executor-collector
  [_ {:keys [executors]}]
  (log/info "Init thread pool executor collector")
  (->> executors
       (map
         (fn [[key executor]]
           (let [name (str (namespace key) "." (name key))]
             (log/debug "Collecting from" name "executor")
             [name executor])))
       (thread-pool-executor-collector)))


(derive :blaze/thread-pool-executor-collector :blaze.metrics/collector)
