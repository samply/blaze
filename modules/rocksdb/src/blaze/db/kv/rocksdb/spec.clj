(ns blaze.db.kv.rocksdb.spec
  (:require
    [clojure.spec.alpha :as s])
  (:import
    [org.rocksdb Cache Statistics]))


(s/def :blaze.db.kv.rocksdb/dir
  string?)


(s/def :blaze.db.kv.rocksdb/block-cache
  #(instance? Cache %))


(s/def :blaze.db.kv.rocksdb/stats
  #(instance? Statistics %))
