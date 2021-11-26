(ns blaze.metrics.registry
  (:require
    [blaze.metrics.core :as metrics]
    [blaze.metrics.spec]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [io.prometheus.client CollectorRegistry]
    [io.prometheus.client.hotspot
     StandardExports MemoryPoolsExports
     GarbageCollectorExports ThreadExports
     ClassLoadingExports VersionInfoExports]))


(set! *warn-on-reflection* true)


(defmethod ig/pre-init-spec :blaze.metrics/registry [_]
  (s/keys :opt-un [:blaze.metrics/collectors]))


(defn- register-collectors! [registry collectors]
  (run!
    (fn [collector]
      (run! #(log/debug "Register collector" (:name %)) (metrics/collect collector))
      (metrics/register! registry collector))
    collectors))


(defmethod ig/init-key :blaze.metrics/registry
  [_ {:keys [collectors] :or {collectors []}}]
  (log/info "Init metrics registry")
  (doto (CollectorRegistry. true)
    (.register (StandardExports.))
    (.register (MemoryPoolsExports.))
    (.register (GarbageCollectorExports.))
    (.register (ThreadExports.))
    (.register (ClassLoadingExports.))
    (.register (VersionInfoExports.))
    (register-collectors! collectors)))
