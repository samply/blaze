(ns blaze.executors
  (:require
    [manifold.executor :as me])
  (:import
    [java.util.concurrent Executor Executors ThreadFactory]))


(set! *warn-on-reflection* true)


(defn executor? [x]
  (instance? Executor x))


(def ^:private thread-counts (atom {}))


(defn- inc-and-get-thread-count [name-template]
  (get (swap! thread-counts update name-template (fnil inc 0)) name-template))


(defn- thread-name [name-template]
  (format name-template (inc-and-get-thread-count name-template)))


(defn manifold-cpu-bound-pool
  "Returns a thread pool with a fixed number of threads which is the number of
  available processors.

  Sets `manifold.executor/executor-thread-local` to this executor to ensure
  deferreds are always executed on this executor."
  [name-template]
  (let [ep (promise)
        e (Executors/newFixedThreadPool
            (.availableProcessors (Runtime/getRuntime))
            (me/thread-factory #(thread-name name-template) ep))]
    (deliver ep e)
    e))


(defn cpu-bound-pool
  "Returns a thread pool with a fixed number of threads which is the number of
  available processors."
  [name-template]
  (Executors/newFixedThreadPool
    (.availableProcessors (Runtime/getRuntime))
    (reify ThreadFactory
      (newThread [_ r]
        (Thread. ^Runnable r ^String (thread-name name-template))))))


(defn io-pool
  "Returns a thread pool with a fixed number of threads which is suitable for
  I/O."
  [n name-template]
  (Executors/newFixedThreadPool
    n
    (reify ThreadFactory
      (newThread [_ r]
        (Thread. ^Runnable r ^String (thread-name name-template))))))


(defn single-thread-executor
  ([]
   (Executors/newSingleThreadExecutor))
  ([name]
   (Executors/newSingleThreadExecutor
     (reify ThreadFactory
       (newThread [_ r]
         (Thread. ^Runnable r ^String name))))))
