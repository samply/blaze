(ns blaze.db.node.tx-indexer
  "Indices resources locally on each node.

  An indexer knows about all indexed transactions and their outcome.
  Transactions not already indexed are not visible to the node.

  Writes to indices which contains the transaction time. The writes will be
  sequential to ensure ACID properties. Uses the same key-value store as the
  resource indexer and node. The metric duration-seconds is exported."
  (:require
    [blaze.db.api :as d]
    [blaze.db.kv.spec]
    [blaze.db.node.tx-indexer.verify :as verify]
    [loom.alg]
    [loom.graph]
    [taoensso.timbre :as log]))


(defn- reference-graph [cmds]
  (loom.graph/digraph
    (into
      {}
      (map
        (fn [{:keys [type id refs]}]
          [[type id] refs]))
      cmds)))


(defn- reference-order
  "Returns a seq of `[type id]` tuples of `cmds` in reference dependency order."
  [cmds]
  (reverse (loom.alg/topsort (reference-graph cmds))))


(defn- index-by-type-id
  "Returns a map from `[type id]` tuples to commands."
  [cmds]
  (into {} (map (fn [{:keys [type id] :as cmd}] [[type id] cmd])) cmds))


(defn- sort-by-references [tx-cmds]
  (let [index (index-by-type-id tx-cmds)
        order (reference-order tx-cmds)]
    (into [] (comp (map index) (remove nil?)) order)))


(defn index-tx
  [db-before {:keys [t tx-cmds]}]
  (log/trace "verify transaction commands with t =" t
             "based on db with t =" (d/basis-t db-before))
  (verify/verify-tx-cmds db-before t (sort-by-references tx-cmds)))
