(ns blaze.db.kv.rocksdb.impl-spec
  (:require
    [blaze.db.kv.rocksdb.impl :as impl]
    [blaze.db.kv.rocksdb.impl.spec]
    [blaze.db.kv.rocksdb.spec]
    [blaze.db.kv.spec]
    [clojure.spec.alpha :as s]))


(s/fdef impl/column-family-descriptor
  :args (s/cat :block-cache :blaze.db.kv.rocksdb/block-cache
               :opts (s/tuple keyword? (s/nilable map?))))


(s/fdef impl/db-options
  :args (s/cat :stats :blaze.db.kv.rocksdb/stats :opts (s/nilable map?)))


(s/fdef impl/write-options
  :args (s/cat :opts (s/nilable map?)))


(s/fdef impl/put-wb!
  :args (s/cat :cfhs (s/map-of keyword? ::impl/column-family-handle)
               :wb ::impl/write-batch
               :entries (s/coll-of :blaze.db.kv/put-entry :kind sequential?)))


(s/fdef impl/delete-wb!
  :args (s/cat :wb ::impl/write-batch :ks (s/coll-of bytes?)))


(s/fdef impl/write-wb!
  :args (s/cat :cfhs (s/map-of keyword? ::impl/column-family-handle)
               :wb ::impl/write-batch
               :entries (s/coll-of :blaze.db.kv/write-entry :kind sequential?)))
