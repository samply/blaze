(ns blaze.db.node-spec
  (:require
    [blaze.db.indexer-spec]
    [blaze.db.kv-spec]
    [blaze.db.node :as node]
    [blaze.db.search-param-registry-spec]
    [blaze.db.tx-log-spec]
    [clojure.spec.alpha :as s])
  (:import
    [com.github.benmanes.caffeine.cache LoadingCache]))


(s/fdef node/init-node
  :args (s/cat :tx-log :blaze.db/tx-log
               :tx-indexer :blaze.db.indexer/tx
               :kv-store :blaze.db/kv-store
               :resource-cache #(instance? LoadingCache %)
               :search-param-registry :blaze.db/search-param-registry))
