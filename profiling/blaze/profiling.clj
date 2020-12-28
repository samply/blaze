(ns blaze.profiling
  "Profiling namespace without test dependencies."
  (:require
    [blaze.system :as system]
    [clojure.tools.namespace.repl :refer [refresh]]
    [taoensso.timbre :as log]))


(defonce system nil)


(defn init []
  (alter-var-root #'system (constantly (system/init! (System/getenv))))
  nil)


(defn reset []
  (some-> system system/shutdown!)
  (refresh :after `init))


;; Init Development
(comment
  (init)
  (pst)
  )


;; Reset after making changes
(comment
  (reset)
  )


(comment
  (log/set-level! :trace)
  (log/set-level! :debug)
  (log/set-level! :info)
  )
