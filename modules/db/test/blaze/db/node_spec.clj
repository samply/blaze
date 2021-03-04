(ns blaze.db.node-spec
  (:require
    [blaze.async.comp-spec]
    [blaze.db.impl.index.t-by-instant-spec]
    [blaze.db.impl.index.tx-error-spec]
    [blaze.db.impl.index.tx-success-spec]
    [blaze.db.kv-spec]
    [blaze.db.node :as node]
    [blaze.db.node.resource-indexer]
    [blaze.db.node.resource-indexer-spec]
    [blaze.db.node.tx-indexer-spec]
    [blaze.db.resource-store-spec]
    [blaze.db.search-param-registry-spec]
    [blaze.db.spec]
    [blaze.db.tx-log-spec]
    [clojure.spec.alpha :as s]
    [java-time :as jt]))


(s/fdef node/new-node
  :args (s/cat :tx-log :blaze.db/tx-log
               :resource-handle-cache :blaze.db/resource-handle-cache
               :tx-cache :blaze.db/tx-cache
               :resource-indexer-executor ::node/resource-indexer-executor
               :resource-indexer-batch-size ::node/resource-indexer-batch-size
               :indexer-executor ::node/indexer-executor
               :kv-store :blaze.db/kv-store
               :resource-store :blaze.db/resource-store
               :search-param-registry :blaze.db/search-param-registry
               :poll-timeout jt/duration?))
