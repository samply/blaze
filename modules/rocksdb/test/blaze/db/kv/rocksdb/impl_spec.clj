(ns blaze.db.kv.rocksdb.impl-spec
  (:require
   [blaze.coll.spec :as cs]
   [blaze.db.kv.rocksdb :as-alias rocksdb]
   [blaze.db.kv.rocksdb.impl :as impl]
   [blaze.db.kv.rocksdb.impl.spec]
   [blaze.db.kv.rocksdb.spec]
   [blaze.db.kv.spec]
   [clojure.spec.alpha :as s]))

(s/fdef impl/column-family-descriptor
  :args (s/cat :block-cache (s/nilable ::rocksdb/block-cache)
               :opts (s/tuple simple-keyword? (s/nilable ::rocksdb/column-family-options))))

(s/fdef impl/db-options
  :args (s/cat :stats (s/nilable ::rocksdb/stats)
               :listener ::rocksdb/listener
               :opts (s/nilable ::rocksdb/db-options)))

(s/fdef impl/write-options
  :args (s/cat :opts (s/nilable ::rocksdb/write-options)))

(s/fdef impl/put-wb!
  :args (s/cat :cfhs (s/map-of keyword? ::impl/column-family-handle)
               :wb ::impl/write-batch
               :entries (cs/coll-of :blaze.db.kv/put-entry)))

(s/fdef impl/delete-wb!
  :args (s/cat :cfhs (s/map-of keyword? ::impl/column-family-handle)
               :wb ::impl/write-batch
               :entries (cs/coll-of :blaze.db.kv/delete-entry)))

(s/fdef impl/write-wb!
  :args (s/cat :cfhs (s/map-of keyword? ::impl/column-family-handle)
               :wb ::impl/write-batch
               :entries (cs/coll-of :blaze.db.kv/write-entry)))
