(ns blaze.db.tx-log.local-spec
  (:require
    [blaze.db.impl.codec-spec]
    [blaze.db.indexer-spec]
    [blaze.db.spec]
    [blaze.db.tx-log.local :as tx-log]
    [blaze.db.tx-log.local.references-spec]
    [clojure.spec.alpha :as s])
  (:import
    [java.time Clock]))


(s/fdef tx-log/init-local-tx-log
  :args (s/cat :resource-indexer :blaze.db.indexer/resource-indexer
               :resource-indexer-batch-size nat-int?
               :tx-indexer :blaze.db.indexer/tx-indexer
               :clock #(instance? Clock %)))
