(ns blaze.db.node.tx-indexer.verify-spec
  (:require
   [blaze.db.impl.index.rts-as-of-spec]
   [blaze.db.kv.spec]
   [blaze.db.node.tx-indexer.verify :as verify]
   [blaze.db.node.tx-indexer.verify.spec]
   [blaze.db.spec]
   [blaze.db.tx-log.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef verify/verify-tx-cmds
  :args (s/cat :context ::verify/context :t :blaze.db/t :tx-cmds ::verify/tx-cmds)
  :ret (s/or :entries (s/coll-of :blaze.db.kv/put-entry) :anomaly ::anom/anomaly))
