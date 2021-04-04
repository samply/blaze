(ns blaze.db.node.tx-indexer.sort-spec
  (:require
    [blaze.db.node.tx-indexer.sort :as sort]
    [blaze.db.tx-log.spec]
    [clojure.spec.alpha :as s]))


(s/fdef sort/sort-by-references
  :args (s/cat :tx-cmds :blaze.db/tx-cmds)
  :ret :blaze.db/tx-cmds)
