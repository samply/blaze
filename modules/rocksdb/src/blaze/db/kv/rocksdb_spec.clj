(ns blaze.db.kv.rocksdb-spec
  (:require
    [blaze.db.kv :as-alias kv]
    [blaze.db.kv.rocksdb :as rocksdb]
    [blaze.db.kv.rocksdb.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/fdef rocksdb/column-families
  :args (s/cat :store ::kv/rocksdb)
  :ret (s/coll-of simple-keyword?))


(s/fdef rocksdb/drop-column-family!
  :args (s/cat :store ::kv/rocksdb :column-family simple-keyword?))


(s/fdef rocksdb/property
  :args (s/cat :store ::kv/rocksdb
               :name-or-column-family (s/alt :name string? :column-family simple-keyword?)
               :name (s/? string?))
  :ret string?)


(s/fdef rocksdb/long-property
  :args (s/cat :store ::kv/rocksdb
               :name-or-column-family (s/alt :name string? :column-family simple-keyword?)
               :name (s/? string?))
  :ret int?)


(s/fdef rocksdb/tables
  :args (s/cat :store ::kv/rocksdb :column-family (s/? simple-keyword?))
  :ret (s/or :tables (s/coll-of ::rocksdb/table) :anomaly ::anom/anomaly))


(s/fdef rocksdb/tables
  :args (s/cat :store ::kv/rocksdb :column-family (s/? simple-keyword?))
  :ret (s/or :properties ::rocksdb/table :anomaly ::anom/anomaly))
