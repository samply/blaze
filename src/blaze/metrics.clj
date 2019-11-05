(ns blaze.metrics
  (:require
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [io.prometheus.client
     Collector GaugeMetricFamily CounterMetricFamily
     Collector$Describable Collector$MetricFamilySamples CollectorRegistry]
    [io.prometheus.client.hotspot
     StandardExports MemoryPoolsExports
     GarbageCollectorExports ThreadExports
     ClassLoadingExports VersionInfoExports]
    [java.util.concurrent BlockingQueue ForkJoinPool ThreadPoolExecutor]))


(s/def ::collectors
  (s/coll-of #(instance? Collector %)))


(defmethod ig/pre-init-spec :blaze.metrics/registry [_]
  (s/keys :req-un [::collectors]))


(defmethod ig/init-key :blaze.metrics/registry
  [_ {:keys [collectors]}]
  (log/info "Init metrics registry")
  (let [registry
        (doto (CollectorRegistry. true)
          (.register (StandardExports.))
          (.register (MemoryPoolsExports.))
          (.register (GarbageCollectorExports.))
          (.register (ThreadExports.))
          (.register (ClassLoadingExports.))
          (.register (VersionInfoExports.)))]
    (doseq [collector collectors]
      (doseq [^Collector$MetricFamilySamples samples
              (.describe ^Collector$Describable collector)]
        (log/debug "Register collector" (.name samples)))
      (.register registry collector))
    registry))
