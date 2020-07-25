(ns blaze.db.node.tx-indexer.verify-spec
  (:require
    [blaze.db.impl.index-spec]
    [blaze.db.kv.spec]
    [blaze.db.node.tx-indexer.verify :as verify]
    [blaze.db.spec]
    [blaze.db.tx-log.spec]
    [clojure.spec.alpha :as s]))


(s/fdef verify/verify-tx-cmds
  :args (s/cat :kv-store :blaze.db/kv-store
               :t :blaze.db/t
               :tx-cmds :blaze.db/tx-cmds))
