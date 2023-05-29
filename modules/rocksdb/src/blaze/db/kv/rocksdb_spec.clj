(ns blaze.db.kv.rocksdb-spec
  (:require
    [blaze.db.kv :as-alias kv]
    [blaze.db.kv.rocksdb :as rocksdb]
    [blaze.db.kv.rocksdb.spec]
    [clojure.spec.alpha :as s]))


(s/fdef rocksdb/get-property
  :args (s/cat :store ::kv/rocksdb
               :name-or-column-family (s/alt :name string? :column-family simple-keyword?)
               :name (s/? string?))
  :ret string?)


(s/fdef rocksdb/get-property
  :args (s/cat :store ::kv/rocksdb
               :name-or-column-family (s/alt :name string? :column-family simple-keyword?)
               :name (s/? string?))
  :ret int?)
