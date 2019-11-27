(ns blaze.core
  (:require
    [blaze.system :as system]
    [taoensso.timbre :as log]))


(defn- max-memory []
  (quot (.maxMemory (Runtime/getRuntime)) (* 1024 1024)))


(defn- available-processors []
  (.availableProcessors (Runtime/getRuntime)))


(defn init! [config]
  (try
    (system/init! config)
    (catch Exception e
      (log/error
        (cond->
          (str "Error while initializing Blaze `" (or (ex-message e) "unknown")
               "`")
          (ex-cause e)
          (str " cause `" (ex-message (ex-cause e)) "`")
          (seq config)
          (str " config: " config)))
      (System/exit 1))))


(def system nil)


(defn init-system! [config]
  (let [sys (init! config)]
    (alter-var-root #'system (constantly sys))
    sys))


(defn shutdown-system! []
  (when-let [sys system]
    (system/shutdown! sys)
    (alter-var-root #'system (constantly nil))
    nil))


(defn- add-shutdown-hook [f]
  (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable f)))


(defn- duration-s [start]
  (format "%.1f" (/ (double (- (System/nanoTime) start)) 1e9)))


(defn -main [& _]
  (add-shutdown-hook shutdown-system!)
  (let [start (System/nanoTime)
        {:blaze/keys [version]} (init-system! (System/getenv))]
    (log/info "JVM version:" (System/getProperty "java.version"))
    (log/info "Maximum available memory:" (max-memory) "MiB")
    (log/info "Number of available processors:" (available-processors))
    (log/info "Successfully started Blaze version" version "in"
              (duration-s start) "seconds")))
