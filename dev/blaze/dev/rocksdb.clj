(ns blaze.dev.rocksdb
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.kv.rocksdb :as rocksdb]
   [blaze.db.kv.rocksdb-spec]
   [blaze.dev :refer [system]]
   [clojure.spec.test.alpha :as st])
  (:import
   [org.rocksdb Env ThreadStatus]))

;; Spec Instrumentation
(st/instrument)

(defn index-kv-store []
  (get system [:blaze.db.kv/rocksdb :blaze.db.main/index-kv-store]))

(defn resource-kv-store []
  (get system [:blaze.db.kv/rocksdb :blaze.db/resource-kv-store]))

(comment
  (ac/supply-async #(rocksdb/compact-range! (index-kv-store) :resource-as-of-index))

  (doseq [index [:search-param-value-index
                 :resource-value-index
                 :compartment-search-param-value-index
                 :compartment-resource-type-index
                 :active-search-params
                 :tx-success-index
                 :tx-error-index
                 :t-by-instant-index
                 :resource-as-of-index
                 :type-as-of-index
                 :system-as-of-index
                 :patient-last-change-index
                 :type-stats-index
                 :system-stats-index
                 :cql-bloom-filter
                 :cql-bloom-filter-by-t]]
    (ac/supply-async #(rocksdb/compact-range! (index-kv-store) index)))

  (ac/supply-async #(rocksdb/compact-range! (resource-kv-store) :default))

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
  (rocksdb/column-family-meta-data (index-kv-store) :patient-last-change-index))
