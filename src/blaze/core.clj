(ns blaze.core
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [env-tools.alpha :as env-tools]
    [blaze.system :as system]
    [phrase.alpha :refer [defphraser phrase-first]]
    [spec-coerce.alpha :refer [coerce]]
    [taoensso.timbre :as log])
  (:gen-class))


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
          {:ex-data (ex-data e)
           :msg (ex-message e)}
          (ex-cause e)
          (assoc :cause-msg (ex-message (ex-cause e)))))
      (System/exit 1))))


(def system nil)


(defn init-system! [config]
  (let [sys (init! config)]
    (alter-var-root #'system (constantly sys))
    sys))


(defn -main [& _]
  (let [config (env-tools/build-config :system/config)
        coerced-config (coerce :system/config config)]
    (if (s/valid? :system/config coerced-config)
      (do
        (init-system! coerced-config)
        (log/info "JVM version:" (System/getProperty "java.version"))
        (log/info "Maximum available memory:" (max-memory))
        (log/info "Number of available processors:" (available-processors)))
      (log/error (phrase-first nil :system/config config)))))


(defphraser #(contains? % key)
  [_ _ key]
  (str "Missing env var: " (str/replace (str/upper-case (name key)) \- \_)))
