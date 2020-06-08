(ns blaze.db.indexer.tx-spec
  (:require
    [blaze.db.impl.index-spec]
    [blaze.db.indexer.tx :as tx]
    [blaze.db.kv.spec]
    [clojure.spec.alpha :as s]))


(s/fdef tx/verify-tx-cmds
  :args (s/cat :kv-store :blaze.db/kv-store
               :t :blaze.db/t
               :tx-instant :blaze.db.tx/instant
               :tx-cmds :blaze.db/tx-cmds))


(s/fdef tx/init-tx-indexer
  :args (s/cat :kv-store :blaze.db/kv-store))
