(ns blaze.db.impl.thread
  "Thread creation for the loops of the database components.")

(set! *warn-on-reflection* true)

(defn start-thread!
  "Starts a daemon thread with `name` that runs `f`."
  [^Runnable f ^String name]
  (.start (doto (Thread. f name) (.setDaemon true))))
