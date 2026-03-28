(ns blaze.db.node.tx-indexer-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.node.tx-indexer :as tx-indexer]
   [blaze.db.node.tx-indexer.verify :as-alias verify]
   [blaze.db.node.tx-indexer.verify.spec]
   [blaze.db.search-param-registry.spec]
   [blaze.db.spec]
   [blaze.db.tx-log.spec]
   [clojure.spec.alpha :as s]))

(s/fdef tx-indexer/index-tx
  :args (s/cat :context ::verify/context :tx-data :blaze.db/tx-data)
  :ret ac/completable-future?)
