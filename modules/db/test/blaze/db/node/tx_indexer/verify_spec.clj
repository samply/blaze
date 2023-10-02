(ns blaze.db.node.tx-indexer.verify-spec
  (:require
   [blaze.byte-string-spec]
   [blaze.db.impl.index-spec]
   [blaze.db.impl.index.resource-as-of-spec]
   [blaze.db.impl.index.rts-as-of-spec]
   [blaze.db.impl.index.system-stats-spec]
   [blaze.db.impl.index.type-stats-spec]
   [blaze.db.kv.spec]
   [blaze.db.node.tx-indexer.verify :as verify]
   [blaze.db.search-param-registry.spec]
   [blaze.db.spec]
   [blaze.db.tx-log.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef verify/verify-tx-cmds
  :args (s/cat :search-param-registry :blaze.db/search-param-registry
               :db-before :blaze.db/db
               :t :blaze.db/t
               :cmds :blaze.db/tx-cmds)
  :ret (s/or :entries (s/coll-of :blaze.db.kv/put-entry)
             :anomaly ::anom/anomaly))
