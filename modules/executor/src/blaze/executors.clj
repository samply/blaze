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


(defn cpu-bound-pool
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


(defn cpu-bound-dedicated-pool
  "Returns a thread pool with a fixed number of threads which is the number of
  available processors.

  If used with `manifold.executor/future-with`, following deferreds from
  chaining are executed on the outer thread pool not on this one."
  [name-template]
  (Executors/newFixedThreadPool
    (.availableProcessors (Runtime/getRuntime))
    (reify ThreadFactory
      (newThread [_ r]
        (Thread. ^Runnable r ^String (thread-name name-template))))))


(defn io-pool
  "Returns a thread pool with a fixed number of threads which is suitable for
  I/O.

  Sets `manifold.executor/executor-thread-local` to this executor to ensure
  deferreds are always executed on this executor."
  [n name-template]
  (let [ep (promise)
        e (Executors/newFixedThreadPool
            n
            (me/thread-factory #(thread-name name-template) ep))]
    (deliver ep e)
    e))


(defn single-thread-executor
  ([]
   (Executors/newSingleThreadExecutor))
  ([name]
   (Executors/newSingleThreadExecutor
     (reify ThreadFactory
       (newThread [_ r]
         (Thread. ^Runnable r ^String name))))))
