(ns blaze.db.node.protocols)

(defprotocol Node
  (-with-db [node f])
  (-with-sync-db [node f] [node t f])
  (-submit-tx [node tx-ops])
  (-tx-result [node t])
  (-subscribe-changes! [node type name subscriber]))
