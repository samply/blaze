(ns blaze.profiling
  "Profiling namespace without test dependencies."
  (:require
    [blaze.system :as system]
    [blaze.db.cache-collector :as cc]
    [blaze.db.kv.rocksdb :as rocksdb]
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

;; Transaction Cache
(comment
  (str (cc/-stats (:blaze.db/tx-cache system)))
  (resource-cache/invalidate-all! (:blaze.db/tx-cache system))
  )

;; Resource Cache
(comment
  (str (cc/-stats (:blaze.db/resource-cache system)))
  (resource-cache/invalidate-all! (:blaze.db/resource-cache system))
  )

;; DB
(comment
  (str (system [:blaze.db.kv.rocksdb/stats :blaze.db.index-kv-store/stats]))

  (def index-db (system [:blaze.db.kv/rocksdb :blaze.db/index-kv-store]))
  (rocksdb/get-property index-db "rocksdb.stats")
  (rocksdb/get-property index-db :search-param-value-index "rocksdb.stats")
  (rocksdb/get-property index-db :resource-value-index "rocksdb.stats")
  (rocksdb/get-property index-db :compartment-search-param-value-index "rocksdb.stats")
  (rocksdb/get-property index-db :compartment-resource-type-index "rocksdb.stats")
  (rocksdb/get-property index-db :tx-success-index "rocksdb.stats")
  (rocksdb/get-property index-db :tx-error-index "rocksdb.stats")
  (rocksdb/get-property index-db :t-by-instant-index "rocksdb.stats")
  (rocksdb/get-property index-db :resource-id-index "rocksdb.stats")
  (rocksdb/get-property index-db :resource-as-of-index "rocksdb.stats")
  (rocksdb/get-property index-db :type-as-of-index "rocksdb.stats")
  (rocksdb/get-property index-db :system-as-of-index "rocksdb.stats")
  (rocksdb/get-property index-db :type-stats-index "rocksdb.stats")
  (rocksdb/get-property index-db :system-stats-index "rocksdb.stats")

  (def resource-db (system [:blaze.db.kv/rocksdb :blaze.db/resource-kv-store]))
  (rocksdb/get-property resource-db "rocksdb.stats")

  (def transaction-db (system [:blaze.db.kv/rocksdb :blaze.db/transaction-kv-store]))
  (rocksdb/get-property transaction-db "rocksdb.stats")
  )
