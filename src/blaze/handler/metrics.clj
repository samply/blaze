(ns blaze.handler.metrics
  (:require
    [prometheus.alpha :as prom]))

(defn metrics-handler
  "Returns a handler function that dumps the metrics associated with `registry`
   in a format consumable by prometheus."
  [registry]
  (fn [_]
    (prom/dump-metrics registry)))
