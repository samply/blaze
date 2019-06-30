(ns blaze.executors
  (:import
    [java.util.concurrent Executor Executors]))


(defn executor? [x]
  (instance? Executor x))


(defn cpu-bound-pool
  "Returns a thread pool with a fixed number of threads which is the number of
  available processors."
  []
  (Executors/newFixedThreadPool (.availableProcessors (Runtime/getRuntime))))


(defn single-thread-executor []
  (Executors/newSingleThreadExecutor))
