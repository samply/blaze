(ns blaze.metrics
  (:require
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [io.prometheus.client
     Collector Collector$MetricFamilySamples
     CollectorRegistry]
    [io.prometheus.client.hotspot
     StandardExports MemoryPoolsExports
     GarbageCollectorExports ThreadExports
     ClassLoadingExports VersionInfoExports]))


(s/def ::collectors
  (s/coll-of #(instance? Collector %)))


(defmethod ig/pre-init-spec :blaze.metrics/registry [_]
  (s/keys :req-un [::collectors]))


(defn register-collectors! [registry collectors]
  (doseq [^Collector collector collectors]
    (doseq [^Collector$MetricFamilySamples samples (.collect collector)]
      (log/debug "Register collector" (.-name samples)))
    (.register registry collector)))


(defmethod ig/init-key :blaze.metrics/registry
  [_ {:keys [collectors]}]
  (log/info "Init metrics registry")
  (doto (CollectorRegistry. true)
    (.register (StandardExports.))
    (.register (MemoryPoolsExports.))
    (.register (GarbageCollectorExports.))
    (.register (ThreadExports.))
    (.register (ClassLoadingExports.))
    (.register (VersionInfoExports.))
    (register-collectors! collectors)))
