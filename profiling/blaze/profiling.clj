(ns blaze.profiling
  "Profiling namespace without test dependencies."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.cache-collector.protocols :as ccp]
   [blaze.db.kv.rocksdb :as rocksdb]
   [blaze.db.resource-cache :as rc]
   [blaze.elm.expression :as-alias expr]
   [blaze.elm.expression.cache :as ec]
   [blaze.system :as system]
   [blaze.util :refer [str]]
   [clojure.repl :refer [pst]]
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
  system
  (pst))

;; Reset after making changes
(comment
  (reset)
  system)

(comment
  (log/set-level! :trace)
  (log/set-level! :debug)
  (log/set-level! :info))

;; Transaction Cache
(comment
  (str (ccp/-stats (:blaze.db/tx-cache system)))
  (rc/invalidate-all! (:blaze.db/tx-cache system)))

;; Resource Cache
(comment
  (str (ccp/-stats (:blaze.db/resource-cache system)))
  (rc/invalidate-all! (:blaze.db/resource-cache system)))

;; CQL Expression Cache
(comment
  (into [] (ec/list-by-t (::expr/cache system)))
  (str (ccp/-stats (::expr/cache system))))

;; DB
(comment
  (str (system [:blaze.db.kv.rocksdb/stats :blaze.db.index-kv-store/stats]))

  (def index-db (system [:blaze.db.kv/rocksdb :blaze.db/index-kv-store]))
  (rocksdb/property index-db "rocksdb.stats")
  (rocksdb/property index-db :search-param-value-index "rocksdb.stats")
  (rocksdb/property index-db :resource-value-index "rocksdb.stats")
  (rocksdb/property index-db :compartment-search-param-value-index "rocksdb.stats")
  (rocksdb/property index-db :compartment-resource-type-index "rocksdb.stats")
  (rocksdb/property index-db :tx-success-index "rocksdb.stats")
  (rocksdb/property index-db :tx-error-index "rocksdb.stats")
  (rocksdb/property index-db :t-by-instant-index "rocksdb.stats")
  (rocksdb/property index-db :resource-as-of-index "rocksdb.stats")
  (rocksdb/property index-db :type-as-of-index "rocksdb.stats")
  (rocksdb/property index-db :system-as-of-index "rocksdb.stats")
  (rocksdb/property index-db :patient-last-change-index "rocksdb.stats")
  (rocksdb/property index-db :type-stats-index "rocksdb.stats")
  (rocksdb/property index-db :system-stats-index "rocksdb.stats")

  (def resource-db (system [:blaze.db.kv/rocksdb :blaze.db/resource-kv-store]))
  (rocksdb/property resource-db "rocksdb.stats")

  (def transaction-db (system [:blaze.db.kv/rocksdb :blaze.db/transaction-kv-store]))
  (rocksdb/property transaction-db "rocksdb.stats"))
