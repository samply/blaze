(ns blaze.db.node-spec
  (:require
    [blaze.async-comp-spec]
    [blaze.db.kv-spec]
    [blaze.db.node :as node]
    [blaze.db.node.resource-indexer]
    [blaze.db.node.resource-indexer-spec]
    [blaze.db.node.tx-indexer-spec]
    [blaze.db.resource-store-spec]
    [blaze.db.search-param-registry-spec]
    [blaze.db.spec]
    [blaze.db.tx-log-spec]
    [clojure.spec.alpha :as s])
  (:import
    [java.time Duration]))


(s/fdef node/new-node
  :args (s/cat :tx-log :blaze.db/tx-log
               :resource-indexer-executor ::node/resource-indexer-executor
               :resource-indexer-batch-size ::node/resource-indexer-batch-size
               :indexer-executor ::node/indexer-executor
               :kv-store :blaze.db/kv-store
               :resource-store :blaze.db/resource-store
               :search-param-registry :blaze.db/search-param-registry
               :poll-timeout #(instance? Duration %)))
