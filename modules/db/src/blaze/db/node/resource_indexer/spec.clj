(ns blaze.db.node.resource-indexer.spec
  (:require
    [blaze.db.kv.spec]
    [blaze.db.resource-store.spec]
    [blaze.db.search-param-registry.spec]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db.node.resource-indexer/executor
  ex/executor?)


(s/def :blaze.db.node.resource-indexer/num-threads
  pos-int?)


(s/def :blaze.db.node/resource-indexer
  (s/keys
    :req-un
    [:blaze.db/kv-store
     :blaze.db/resource-store
     :blaze.db/search-param-registry
     :blaze.db.node.resource-indexer/executor]))
