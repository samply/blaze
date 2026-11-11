(ns blaze.db.node.tx-indexer.expand-spec
  (:require
   [blaze.db.kv.spec]
   [blaze.db.node.tx-indexer.expand :as expand]
   [blaze.db.node.tx-indexer.expand.spec]
   [blaze.db.node.tx-indexer.util-spec]
   [blaze.db.node.tx-indexer.verify :as-alias verify]
   [blaze.db.node.tx-indexer.verify.spec]
   [blaze.db.search-param-registry-spec]
   [blaze.db.spec]
   [blaze.db.tx-log.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef expand/expand-tx-cmds
  :args (s/cat :context ::expand/context :tx-cmds :blaze.db/tx-cmds)
  :ret (s/or :tx-cmds ::verify/tx-cmds :anomaly ::anom/anomaly))
