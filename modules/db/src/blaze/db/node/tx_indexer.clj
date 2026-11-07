(ns blaze.db.node.tx-indexer
  (:require
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.db.node.tx-indexer.expand :as expand]
   [blaze.db.node.tx-indexer.util :as tx-u]
   [blaze.db.node.tx-indexer.verify :as verify]
   [prometheus.alpha :as prom]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defn- distinct-expansion?
  "Returns true if the expansion of `tx-cmds` acts on every resource at most
  once, so that it doesn't have to be checked for duplicates.

  That is the case for a single command, because the expansion of a command
  acts on every resource at most once (see `expand/expand`). The commands of
  several commands can act on the same resource, like a patient purge and an
  update of one of the resources about the patient."
  [tx-cmds]
  (= 1 (count tx-cmds)))

(defn index-tx
  "Indexes a transaction by expanding and verifying its commands.

  Expands any conditional transaction commands (e.g. conditional
  creates/deletes) against the database value `:db-before` and its `:stats` in
  `context` asynchronously, then verifies the expanded commands against the
  full context at transaction point `t`. Both run their parallel parts on the
  `:pool` of `context`, a ForkJoinPool. The commands are verified against the
  `permissions` of the transaction, absent permissions allowing to write no
  AccessControl code.

  Returns a CompletableFuture that completes with a collection of
  `blaze.db.kv/put-entry` values to be written to the KV store, or with an
  anomaly if verification fails (e.g. precondition not met, referential
  integrity violation, or duplicate commands)."
  {:arglists '([context tx-data])}
  [{:keys [db-before] :as context} {:keys [t tx-cmds permissions]}]
  (log/trace "verify transaction commands with t =" t
             "based on db with t =" (d/t db-before))
  (do-sync [expanded-tx-cmds (expand/expand-tx-cmds context tx-cmds)]
    (with-open [_ (prom/timer tx-u/duration-seconds "verify-tx-cmds")]
      (verify/verify-tx-cmds
       (cond-> context
         permissions (assoc :permissions permissions)
         (distinct-expansion? tx-cmds) (assoc :distinct-tx-cmds true))
       t expanded-tx-cmds))))
