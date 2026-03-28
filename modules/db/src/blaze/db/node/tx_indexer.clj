(ns blaze.db.node.tx-indexer
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.node.tx-indexer.expand :as expand]
   [blaze.db.node.tx-indexer.util :as tx-u]
   [blaze.db.node.tx-indexer.verify :as verify]
   [prometheus.alpha :as prom]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defn index-tx
  "Indexes a transaction by expanding and verifying its commands.

  Opens a batch database from `:db-before` in `context`, expands any conditional
  transaction commands (e.g. conditional creates/deletes) against it
  asynchronously, then verifies the expanded commands against the full context
  at transaction point `t`.

  Returns a CompletableFuture that completes with a collection of
  `blaze.db.kv/put-entry` values to be written to the KV store, or with an
  anomaly if verification fails (e.g. precondition not met, referential
  integrity violation, or duplicate commands)."
  {:arglists '([context tx-data])}
  [{:keys [db-before] :as context} {:keys [t tx-cmds]}]
  (log/trace "verify transaction commands with t =" t
             "based on db with t =" (d/basis-t db-before))
  (let [db-before (d/new-batch-db db-before)]
    (-> (expand/expand-tx-cmds db-before tx-cmds)
        (ac/then-apply
         (fn [tx-cmds]
           (with-open [_ (prom/timer tx-u/duration-seconds "verify-tx-cmds")]
             (verify/verify-tx-cmds (assoc context :db-before db-before) t tx-cmds))))
        (ac/when-complete (fn [_ _] (.close db-before))))))
