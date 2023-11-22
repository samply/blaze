(ns blaze.db.kv.rocksdb.metrics-spec
  (:require
   [blaze.db.kv.rocksdb :as-alias rocksdb]
   [blaze.db.kv.rocksdb.metrics :as metrics]
   [blaze.db.kv.rocksdb.metrics.spec]
   [blaze.db.kv.rocksdb.spec]
   [clojure.spec.alpha :as s]))

(s/fdef metrics/stats-collector
  :args (s/cat :stats ::metrics/stats))

(s/fdef metrics/block-cache-collector
  :args (s/cat :block-cache ::rocksdb/block-cache))

(s/fdef metrics/table-reader-collector
  :args (s/cat :stores (s/nilable ::metrics/stores)))
