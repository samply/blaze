(ns blaze.metrics.handler
  (:require
   [blaze.metrics.spec]
   [blaze.module :as m]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [prometheus.alpha :as prom]
   [taoensso.timbre :as log]))

(defmethod m/pre-init-spec :blaze.metrics/handler [_]
  (s/keys :req-un [:blaze.metrics/registry]))

(defmethod ig/init-key :blaze.metrics/handler
  [_ {:keys [registry]}]
  (log/info "Init metrics handler")
  (fn [_]
    (prom/dump-metrics registry)))
