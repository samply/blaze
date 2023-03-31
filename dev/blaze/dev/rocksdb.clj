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
  (rocksdb/compact-range!
    (index-kv-store)
    :resource-as-of-index
    true
    1)

  (rocksdb/compact-range!
    (index-kv-store)
    :search-param-value-index
    true
    1)

  (mapv
    (fn [^ThreadStatus status]
      {:type (.name (.getThreadType status))
       :operation-type (.name (.getOperationType status))
       :operation-stage (.name (.getOperationStage status))
       :operation-elapsed-time (.getOperationElapsedTime status)
       :db (.getDbName status)
       :cf (.getCfName status)})
    (.getThreadList (Env/getDefault)))

  (rocksdb/table-properties (index-kv-store) :resource-as-of-index)

  )
