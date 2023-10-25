(ns blaze.db.kv.rocksdb.spec
  (:require
    [blaze.db.kv :as-alias kv]
    [blaze.db.kv.rocksdb :as-alias rocksdb]
    [blaze.db.kv.rocksdb.column-family-options :as-alias column-family-options]
    [blaze.db.kv.rocksdb.db-options :as-alias db-options]
    [blaze.db.kv.rocksdb.protocols :as p]
    [blaze.db.kv.rocksdb.table :as-alias table]
    [blaze.db.kv.rocksdb.write-options :as-alias write-options]
    [blaze.db.kv.spec]
    [clojure.spec.alpha :as s])
  (:import
    [org.rocksdb AbstractEventListener Cache Env Statistics]))


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


(s/def ::rocksdb/listener
  #(instance? AbstractEventListener %))


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


(s/def ::column-family-options/write-buffer-size-in-mb
  pos-int?)


(s/def ::column-family-options/max-write-buffer-number
  pos-int?)


(s/def ::column-family-options/level0-file-num-compaction-trigger
  pos-int?)


(s/def ::column-family-options/min-write-buffer-number-to-merge
  pos-int?)


(s/def ::column-family-options/max-bytes-for-level-base-in-mb
  pos-int?)


(s/def ::column-family-options/target-file-size-base-in-mb
  pos-int?)


(s/def ::column-family-options/block-size
  pos-int?)


(s/def ::column-family-options/bloom-filter?
  boolean?)


(s/def ::column-family-options/memtable-whole-key-filtering?
  boolean?)


(s/def ::column-family-options/optimize-filters-for-hits?
  boolean?)


(s/def ::column-family-options/reverse-comparator?
  boolean?)


(s/def ::column-family-options/merge-operator
  #{:put :uint64add :stringappend})


(s/def ::column-family-options/enable-blob-files?
  boolean?)


(s/def ::column-family-options/min-blob-size
  pos-int?)


(s/def ::rocksdb/column-family-options
  (s/keys :opt-un [::column-family-options/write-buffer-size-in-mb
                   ::column-family-options/max-write-buffer-number
                   ::column-family-options/level0-file-num-compaction-trigger
                   ::column-family-options/min-write-buffer-number-to-merge
                   ::column-family-options/max-bytes-for-level-base-in-mb
                   ::column-family-options/target-file-size-base-in-mb
                   ::column-family-options/block-size
                   ::column-family-options/bloom-filter?
                   ::column-family-options/memtable-whole-key-filtering?
                   ::column-family-options/optimize-filters-for-hits?
                   ::column-family-options/reverse-comparator?
                   ::column-family-options/merge-operator
                   ::column-family-options/enable-blob-files?
                   ::column-family-options/min-blob-size]))


(s/def ::rocksdb/column-families
  (s/map-of simple-keyword? (s/nilable ::rocksdb/column-family-options)))


(s/def ::table/data-size
  int?)


(s/def ::table/index-size
  int?)


(s/def ::rocksdb/table
  (s/keys :req-un [::table/data-size ::table/index-size]))
