(ns blaze.dev.rocksdb
  (:require
    [blaze.db.kv.rocksdb :as rocksdb]
    [blaze.db.kv.rocksdb-spec]
    [blaze.dev :refer [system]]
    [clojure.spec.test.alpha :as st])
  (:import
    [org.rocksdb Env ThreadStatus]))


;; Spec Instrumentation
(st/instrument)


(defn index-kv-store []
  (get system [:blaze.db.kv/rocksdb :blaze.db/index-kv-store]))


(comment
  (rocksdb/compact-range! (index-kv-store) :resource-as-of-index)
  (rocksdb/compact-range! (index-kv-store) :type-as-of-index)
  (rocksdb/compact-range! (index-kv-store) :system-as-of-index)
  (rocksdb/compact-range! (index-kv-store) :search-param-value-index)
  (rocksdb/compact-range! (index-kv-store) :compartment-search-param-value-index)
  (rocksdb/compact-range! (index-kv-store) :resource-value-index)

  (mapv
    (fn [^ThreadStatus status]
      {:type (.name (.getThreadType status))
       :operation-type (.name (.getOperationType status))
       :operation-stage (.name (.getOperationStage status))
       :operation-elapsed-time (.getOperationElapsedTime status)
       :db (.getDbName status)
       :cf (.getCfName status)})
    (.getThreadList (Env/getDefault)))

  (rocksdb/tables (index-kv-store) :resource-as-of-index)

  (rocksdb/column-family-meta-data (index-kv-store) :search-param-value-index)
  (rocksdb/column-family-meta-data (index-kv-store) :compartment-search-param-value-index)
  (rocksdb/column-family-meta-data (index-kv-store) :resource-as-of-index)
  (rocksdb/column-family-meta-data (index-kv-store) :patient-last-change-index)

  )
