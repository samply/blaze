(ns blaze.db.kv.rocksdb.impl.spec
  (:require
    [clojure.spec.alpha :as s])
  (:import
    [org.rocksdb ColumnFamilyHandle WriteBatchInterface]))


(s/def :blaze.db.kv.rocksdb.impl/column-family-handle
  #(instance? ColumnFamilyHandle %))


(s/def :blaze.db.kv.rocksdb.impl/write-batch
  #(instance? WriteBatchInterface %))
