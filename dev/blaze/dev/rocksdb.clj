(ns blaze.dev.rocksdb
  (:require
    [blaze.db.kv.rocksdb :as rocksdb]
    [blaze.db.kv.rocksdb-spec]
    [blaze.dev :refer [system]]
    [clojure.spec.test.alpha :as st]))


;; Spec Instrumentation
(st/instrument)


(comment
  (rocksdb/compact-range!
    (get system :blaze.db.kv/rocksdb)
    :resource-as-of-index
    true
    1)

  (rocksdb/compact-range!
    (get system :blaze.db.kv/rocksdb)
    :search-param-value-index
    true
    1)
  )
