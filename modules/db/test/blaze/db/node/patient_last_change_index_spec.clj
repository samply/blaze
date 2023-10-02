(ns blaze.db.node.patient-last-change-index-spec
  (:require
    [blaze.db.kv.spec]
    [blaze.db.node.patient-last-change-index :as node-plc]
    [blaze.db.node.tx-indexer-spec]
    [blaze.db.spec]
    [blaze.db.tx-log.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/fdef node-plc/index-entries
  :args (s/cat :node :blaze.db/node
               :tx-data :blaze.db/tx-data)
  :ret (s/or :entries (s/coll-of :blaze.db.kv/put-entry) :anomaly ::anom/anomaly))
