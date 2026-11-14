(ns blaze.db.node.tx-indexer.expand.spec
  (:require
   [blaze.db.node.tx-indexer.expand :as-alias expand]
   [blaze.db.node.tx-indexer.verify :as-alias verify]
   [blaze.db.node.tx-indexer.verify.spec]
   [clojure.spec.alpha :as s]))

(s/def ::expand/context
  (s/keys :req-un [::verify/db-before ::verify/stats ::verify/pool]))
