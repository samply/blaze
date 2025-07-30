(ns blaze.dev.rocksdb
  (:require
   [blaze.byte-string :as bs]
   [blaze.db.impl.codec :as codec]
   [blaze.db.kv :as kv]
   [blaze.db.kv.rocksdb :as rocksdb]
   [blaze.db.kv.rocksdb-spec]
   [blaze.dev :refer [system]]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
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
  @(kv/compact! (index-kv-store) :resource-as-of-index)

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
    @(kv/compact! (index-kv-store) index))

  @(kv/compact! (resource-kv-store) :default)

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

(comment
  (time (let [seek-key (sp-vr/encode-seek-key
                  (codec/c-hash "code")
                  (codec/tid "Observation")
                  (codec/v-hash "http://loinc.org|49765-1" #_"http://loinc.org|9843-4" ))]
    (kv/estimate-storage-size (index-kv-store) :search-param-value-index
                              [seek-key (bs/concat seek-key (bs/from-hex "FF"))])))
  )
