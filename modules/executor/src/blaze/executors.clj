(ns blaze.executors
  (:import
    [java.util.concurrent Executor ExecutorService Executors ThreadFactory]))


(set! *warn-on-reflection* true)


(defn executor? [x]
  (instance? Executor x))


(defn executor-service? [x]
  (instance? ExecutorService x))


(defn execute!
  "Executes the function `f` at some time in the future."
  [executor f]
  (.execute ^Executor executor f))


(defn shutdown! [executor-service]
  (.shutdown ^ExecutorService executor-service))


(defn shutdown? [executor-service]
  (.isShutdown ^ExecutorService executor-service))


(defn terminated?
  "Returns true if all tasks have completed following shut down.

  Note that this function returns never true unless either `shutdown` or
  `shutdown-now` was called first."
  [executor-service]
  (.isTerminated ^ExecutorService executor-service))


(defn await-termination
  "Blocks until all tasks have completed execution after a shutdown request, or
  the timeout occurs, or the current thread is interrupted, whichever happens
  first."
  [executor-service timeout unit]
  (.awaitTermination ^ExecutorService executor-service timeout unit))


(defn- thread-name!
  [thread-counter name-template]
  (format name-template (swap! thread-counter inc)))


(defn cpu-bound-pool
  "Returns a thread pool with a fixed number of threads which is the number of
  available processors."
  [name-template]
  (let [thread-counter (atom 0)]
    (Executors/newFixedThreadPool
      (.availableProcessors (Runtime/getRuntime))
      (reify ThreadFactory
        (newThread [_ r]
          (Thread. ^Runnable r ^String (thread-name! thread-counter
                                                     name-template)))))))


(defn io-pool
  "Returns a thread pool with a fixed number of threads which is suitable for
  I/O."
  [n name-template]
  (let [thread-counter (atom 0)]
    (Executors/newFixedThreadPool
      n
      (reify ThreadFactory
        (newThread [_ r]
          (Thread. ^Runnable r ^String (thread-name! thread-counter
                                                     name-template)))))))


(defn single-thread-executor
  ([]
   (Executors/newSingleThreadExecutor))
  ([name]
   (Executors/newSingleThreadExecutor
     (reify ThreadFactory
       (newThread [_ r]
         (Thread. ^Runnable r ^String name))))))
