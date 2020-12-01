(ns blaze.db.node.tx-indexer-spec
  (:require
    [blaze.db.kv.spec]
    [blaze.db.node.tx-indexer :as tx-indexer]
    [blaze.db.spec]
    [blaze.db.tx-log.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/fdef tx-indexer/index-tx
  :args (s/cat :db-before :blaze.db/db :tx-data :blaze.db/tx-data)
  :ret (s/or :entries (s/coll-of :blaze.db.kv/put-entry) :anomaly ::anom/anomaly))
