(ns blaze.db.kv.rocksdb.metrics.spec
  (:require
    [blaze.db.kv :as-alias kv]
    [blaze.db.kv.rocksdb.metrics :as-alias metrics]
    [blaze.db.kv.rocksdb.spec]
    [clojure.spec.alpha :as s]))


(s/def ::metrics/stats
  (s/map-of string? :blaze.db.kv.rocksdb/stats))


(s/def ::metrics/stores
  (s/map-of string? ::kv/rocksdb))
