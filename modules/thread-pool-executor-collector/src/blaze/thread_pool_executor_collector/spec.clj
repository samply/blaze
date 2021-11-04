(ns blaze.thread-pool-executor-collector.spec
  (:require
    [clojure.spec.alpha :as s])
  (:import
    [java.util.concurrent ThreadPoolExecutor]))


(defn thread-pool-executor? [x]
  (instance? ThreadPoolExecutor x))


(s/def :blaze.thread-pool-executor-collector/executors
  (s/map-of keyword? thread-pool-executor?))
