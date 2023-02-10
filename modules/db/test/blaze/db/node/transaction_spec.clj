(ns blaze.db.node.transaction-spec
  (:require
    [blaze.anomaly-spec]
    [blaze.db.node.transaction :as tx]
    [blaze.db.node.transaction.spec]
    [clojure.spec.alpha :as s]))


(s/fdef tx/prepare-ops
  :args (s/cat :context :blaze.db.node.transaction/context :tx-ops :blaze.db/tx-ops)
  :ret :blaze.db/submit-tx-cmds)
