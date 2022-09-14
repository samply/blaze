(ns blaze.db.kv.rocksdb.spec
  (:require
    [blaze.db.kv.rocksdb.db-options :as-alias db-options]
    [blaze.db.kv.rocksdb.write-options :as-alias write-options]
    [clojure.spec.alpha :as s])
  (:import
    [org.rocksdb Cache Env Statistics]))


(s/def :blaze.db.kv.rocksdb/dir
  string?)


(s/def :blaze.db.kv.rocksdb/block-cache
  #(instance? Cache %))


(s/def :blaze.db.kv.rocksdb/env
  #(instance? Env %))


(s/def :blaze.db.kv.rocksdb/stats
  #(instance? Statistics %))


(s/def ::db-options/wal-dir
  string?)


(s/def ::db-options/max-background-jobs
  nat-int?)


(s/def ::db-options/compaction-readahead-size
  nat-int?)


(s/def ::db-options/manual-wal-flush?
  boolean?)


(s/def :blaze.db.kv.rocksdb/db-options
  (s/keys :opt-un [::db-options/wal-dir
                   ::db-options/max-background-jobs
                   ::db-options/compaction-readahead-size
                   ::db-options/manual-wal-flush?]))


(s/def ::write-options/sync?
  boolean?)


(s/def ::write-options/disable-wal?
  boolean?)


(s/def :blaze.db.kv.rocksdb/write-options
  (s/keys :opt-un [::write-options/sync? ::write-options/disable-wal?]))


(s/def :blaze.db.kv.rocksdb/opts
  (s/merge :blaze.db.kv.rocksdb/db-options :blaze.db.kv.rocksdb/write-options))
