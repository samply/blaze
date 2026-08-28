(ns blaze.queued-thread-pool-collector.spec
  (:require
   [clojure.spec.alpha :as s])
  (:import
   [org.eclipse.jetty.util.thread QueuedThreadPool]))

(defn queued-thread-pool? [x]
  (instance? QueuedThreadPool x))

(s/def :blaze.queued-thread-pool-collector/thread-pool
  queued-thread-pool?)
