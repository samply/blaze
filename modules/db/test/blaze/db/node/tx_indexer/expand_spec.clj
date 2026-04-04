(ns blaze.db.node.tx-indexer.expand-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.kv.spec]
   [blaze.db.node.tx-indexer.expand :as expand]
   [blaze.db.search-param-registry-spec]
   [blaze.db.spec]
   [blaze.db.tx-log.spec]
   [clojure.spec.alpha :as s]))

(s/fdef expand/expand-tx-cmds
  :args (s/cat :db-before :blaze.db/db :tx-cmds :blaze.db/tx-cmds)
  :ret ac/completable-future?)
