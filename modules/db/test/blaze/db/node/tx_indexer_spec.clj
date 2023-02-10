(ns blaze.db.node.tx-indexer-spec
  (:require
    [blaze.db.kv.spec]
    [blaze.db.node.tx-indexer :as tx-indexer]
    [blaze.db.node.tx-indexer.verify-spec]
    [blaze.db.spec]
    [blaze.db.tx-log.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/def :blaze.db.node.tx-indexer/tx-data
  (s/keys :req-un [:blaze.db/t :blaze.db.tx/instant :blaze.db.node.tx-indexer/tx-cmds]))


(s/fdef tx-indexer/index-tx
  :args (s/cat :db-before :blaze.db/db :tx-data :blaze.db.node.tx-indexer/tx-data)
  :ret (s/or :entries (s/coll-of :blaze.db.kv/put-entry) :anomaly ::anom/anomaly))
