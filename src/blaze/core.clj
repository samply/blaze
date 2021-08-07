(ns blaze.core
  (:require
    [blaze.system :as system]
    [clojure.string :as str]
    [taoensso.timbre :as log]))


(defn- max-memory []
  (quot (.maxMemory (Runtime/getRuntime)) (* 1024 1024)))


(defn- available-processors []
  (.availableProcessors (Runtime/getRuntime)))


(defn- config-msg [config]
  (->> (sort-by key config)
       (map (fn [[k v]] (str k " = " v)))
       (str/join ",\n      ")))


(defn init! [config]
  (try
    (system/init! config)
    (catch Exception e
      (log/error
        (cond->
          (str "Error while initializing Blaze.\n\n    " (ex-message e))
          (ex-cause e)
          (str "\n\n    Cause: " (ex-message (ex-cause e)))
          (ex-cause (ex-cause e))
          (str "\n\n      Cause: " (ex-message (ex-cause (ex-cause e))))
          (seq config)
          (str "\n\n    Config:\n      " (config-msg config))))
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
