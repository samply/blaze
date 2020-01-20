(ns blaze.handler.metrics
  (:require
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [prometheus.alpha :as prom]
    [taoensso.timbre :as log])
  (:import
    [io.prometheus.client CollectorRegistry]))


(defn metrics-handler
  "Returns a handler function that dumps the metrics associated with `registry`
   in a format consumable by prometheus."
  [registry]
  (fn [_]
    (prom/dump-metrics registry)))


(s/def ::registry
  #(instance? CollectorRegistry %))


(defmethod ig/pre-init-spec :blaze.handler/metrics [_]
  (s/keys :req-un [::registry]))


(defmethod ig/init-key :blaze.handler/metrics
  [_ {:keys [registry]}]
  (log/info "Init metrics handler")
  (metrics-handler registry))
