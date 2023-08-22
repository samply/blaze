(ns blaze.db.kv.rocksdb.spec
  (:require
    [blaze.db.kv :as-alias kv]
    [blaze.db.kv.rocksdb :as-alias rocksdb]
    [blaze.db.kv.rocksdb.db-options :as-alias db-options]
    [blaze.db.kv.rocksdb.protocols :as p]
    [blaze.db.kv.rocksdb.table :as-alias table]
    [blaze.db.kv.rocksdb.write-options :as-alias write-options]
    [blaze.db.kv.spec]
    [clojure.spec.alpha :as s])
  (:import
    [org.rocksdb Cache Env Statistics]))


(s/def ::kv/rocksdb
  (s/and :blaze.db/kv-store #(satisfies? p/Rocks %)))


(s/def ::rocksdb/dir
  string?)


(s/def ::rocksdb/block-cache
  #(instance? Cache %))


(s/def ::rocksdb/env
  #(instance? Env %))


(s/def ::rocksdb/stats
  #(instance? Statistics %))


(s/def ::db-options/wal-dir
  string?)


(s/def ::db-options/max-background-jobs
  nat-int?)


(s/def ::db-options/compaction-readahead-size
  nat-int?)


(s/def ::rocksdb/db-options
  (s/keys :opt-un [::db-options/wal-dir
                   ::db-options/max-background-jobs
                   ::db-options/compaction-readahead-size]))


(s/def ::write-options/sync?
  boolean?)


(s/def ::write-options/disable-wal?
  boolean?)


(s/def ::rocksdb/write-options
  (s/keys :opt-un [::write-options/sync? ::write-options/disable-wal?]))


(s/def ::rocksdb/opts
  (s/merge ::rocksdb/db-options ::rocksdb/write-options))


(s/def ::table/data-size
  int?)


(s/def ::table/index-size
  int?)


(s/def ::rocksdb/table
  (s/keys :req-un [::table/data-size ::table/index-size]))
