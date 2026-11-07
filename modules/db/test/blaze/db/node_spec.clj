(ns blaze.db.node-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.async.comp-spec]
   [blaze.db.impl.index.resource-handle-spec]
   [blaze.db.impl.index.t-by-instant-spec]
   [blaze.db.impl.index.tx-error-spec]
   [blaze.db.impl.index.tx-success-spec]
   [blaze.db.impl.thread-spec]
   [blaze.db.kv-spec]
   [blaze.db.node :as node]
   [blaze.db.node.resource-indexer]
   [blaze.db.node.resource-indexer-spec]
   [blaze.db.node.spec]
   [blaze.db.node.tx-indexer-spec]
   [blaze.db.node.util-spec]
   [blaze.db.resource-store-spec]
   [blaze.db.search-param-registry-spec]
   [blaze.db.spec]
   [blaze.db.tx-log-spec]
   [blaze.util-spec]
   [clojure.spec.alpha :as s]))

(s/fdef node/submit-tx
  :args (s/cat :node :blaze.db/node :tx-ops :blaze.db/tx-ops)
  :ret ac/completable-future?)
