(ns blaze.executors
  (:import
    [java.util.concurrent Executor Executors ThreadFactory]))


(set! *warn-on-reflection* true)


(defn executor? [x]
  (instance? Executor x))


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
