(ns blaze.db.node.tx-indexer-spec
  (:require
    [blaze.db.kv.spec]
    [blaze.db.node.tx-indexer :as tx-indexer]
    [blaze.db.node.tx-indexer.spec]
    [blaze.db.spec]
    [blaze.db.tx-log.spec]
    [clojure.spec.alpha :as s]))


(s/fdef tx-indexer/last-t
  :args (s/cat :kv-store :blaze.db/kv-store)
  :ret (s/nilable :blaze.db/t))


(s/fdef tx-indexer/index-tx
  :args (s/cat :kv-store :blaze.db/kv-store
               :tx-data :blaze.db/tx-data))
