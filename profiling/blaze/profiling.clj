(ns blaze.profiling
  "Profiling namespace without test dependencies."
  (:require
    [blaze.system :as system]
    [blaze.db.cache-collector :as cc]
    [blaze.db.resource-cache :as resource-cache]
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


;; Resource Handle Cache
(comment
  (str (cc/-stats (:blaze.db/resource-handle-cache system)))
  (.invalidateAll ^Cache (:blaze.db/resource-handle-cache system))
  )

;; Resource Cache
(comment
  (str (cc/-stats (:blaze.db/resource-cache system)))
  (resource-cache/invalidate-all! (:blaze.db/resource-cache system))
  )
