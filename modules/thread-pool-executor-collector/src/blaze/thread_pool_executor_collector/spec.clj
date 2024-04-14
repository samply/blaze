(ns blaze.thread-pool-executor-collector.spec
  (:require
   [clojure.spec.alpha :as s])
  (:import
   [java.util.concurrent ThreadPoolExecutor]))

(defn thread-pool-executor? [x]
  (instance? ThreadPoolExecutor x))

(s/def :blaze.thread-pool-executor-collector/executors
  (s/map-of (s/or :simple-key keyword? :key (s/tuple keyword? keyword?))
            thread-pool-executor?))
