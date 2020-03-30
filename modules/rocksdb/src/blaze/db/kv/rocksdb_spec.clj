(ns blaze.db.kv.rocksdb-spec
  (:require
    [blaze.db.kv.rocksdb :as rocksdb]
    [clojure.spec.alpha :as s])
  (:import
    [org.rocksdb Cache]))


(s/def :blaze.db.kv.rocksdb/block-cache
  #(instance? Cache %))


(s/fdef rocksdb/init-rocksdb-kv-store
  :args (s/cat :dir string? :block-cache :blaze.db.kv.rocksdb/block-cache
               :opts map? :column-families map?))


(s/fdef rocksdb/create-rocksdb-kv-store
  :args (s/cat :dir string? :column-families map?))
