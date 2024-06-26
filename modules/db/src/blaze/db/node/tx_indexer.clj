(ns blaze.db.node.tx-indexer
  (:require
   [blaze.db.api :as d]
   [blaze.db.node.tx-indexer.verify :as verify]
   [taoensso.timbre :as log]))

(defn index-tx
  [search-param-registry db-before {:keys [t tx-cmds]}]
  (log/trace "verify transaction commands with t =" t
             "based on db with t =" (d/basis-t db-before))
  (verify/verify-tx-cmds search-param-registry db-before t tx-cmds))
