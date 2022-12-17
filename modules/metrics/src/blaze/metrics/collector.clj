(ns blaze.metrics.collector
  (:gen-class
    :extends io.prometheus.client.Collector
    :constructors {[Object] []}
    :init init
    :state fn
    :main false))


(set! *warn-on-reflection* true)


(defn -init [fn]
  [[] fn])


(defn -collect-void [this]
  ((.-fn ^blaze.metrics.collector this)))
