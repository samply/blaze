(ns blaze.db.node.tx-indexer.expand-spec
  (:require
   [blaze.db.kv.spec]
   [blaze.db.node.tx-indexer.expand :as expand]
   [blaze.db.node.tx-indexer.verify :as verify]
   [blaze.db.node.tx-indexer.verify.spec]
   [blaze.db.spec]
   [blaze.db.tx-log.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef expand/expand-tx-cmds
  :args (s/cat :db-before :blaze.db/db :tx-cmds :blaze.db/tx-cmds)
  :ret (s/or :terminal-tx-cmds ::verify/tx-cmds :anomaly ::anom/anomaly))
