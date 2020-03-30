(ns blaze.db.indexer
  "Protocols for indexer backend implementations.")


(defprotocol Resource
  "A ResourceIndexer knows how to index resources independent from
  transactions."

  (-index-resources [indexer hash-and-resources]))


(defn index-resources
  "Returns a deferred."
  [indexer hash-and-resources]
  (-index-resources indexer hash-and-resources))


(defprotocol Tx
  "Indices resources locally on each node.

  An indexer knows about all indexed transactions and their outcome.
  Transactions not already indexed are not visible to the node"

  (last-t [indexer]
    "Returns the last known `t`.")

  (-submit-tx [indexer t tx-instant tx-cmds])

  (tx-result [indexer t]
    "Waits for the transaction with `t` to be indexed and returns a success
     deferred with the requested `t` or an error deferred with an anomaly in
     case the transaction errored."))


(defn submit-tx
  "Has to be run single threaded. Blocks."
  [indexer t tx-instant tx-cmds]
  (-submit-tx indexer t tx-instant tx-cmds))
