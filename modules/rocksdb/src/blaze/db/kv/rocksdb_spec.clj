(ns blaze.db.kv.rocksdb-spec
  (:require
    [blaze.db.kv.rocksdb :as rocksdb]
    [clojure.spec.alpha :as s])
  (:import
    [org.rocksdb Cache Statistics]))


(s/def :blaze.db.kv.rocksdb/block-cache
  #(instance? Cache %))


(s/def :blaze.db.kv.rocksdb/stats
  #(instance? Statistics %))


(s/fdef rocksdb/init-rocksdb-kv-store
  :args (s/cat :dir string?
               :block-cache :blaze.db.kv.rocksdb/block-cache
               :stats :blaze.db.kv.rocksdb/stats
               :opts map? :column-families map?))


(s/fdef rocksdb/create-rocksdb-kv-store
  :args (s/cat :dir string? :column-families map?))
