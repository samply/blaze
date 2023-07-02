(ns blaze.db.kv.rocksdb-spec
  (:require
    [blaze.db.kv :as-alias kv]
    [blaze.db.kv.rocksdb :as rocksdb]
    [blaze.db.kv.rocksdb.spec]
    [clojure.spec.alpha :as s]))


(s/fdef rocksdb/column-families
  :args (s/cat :store ::kv/rocksdb)
  :ret (s/coll-of simple-keyword?))


(s/fdef rocksdb/get-property
  :args (s/cat :store ::kv/rocksdb
               :name-or-column-family (s/alt :name string? :column-family simple-keyword?)
               :name (s/? string?))
  :ret string?)


(s/fdef rocksdb/get-long-property
  :args (s/cat :store ::kv/rocksdb
               :name-or-column-family (s/alt :name string? :column-family simple-keyword?)
               :name (s/? string?))
  :ret int?)


(s/fdef rocksdb/table-properties
  :args (s/cat :store ::kv/rocksdb :column-family (s/? simple-keyword?))
  :ret int?)
