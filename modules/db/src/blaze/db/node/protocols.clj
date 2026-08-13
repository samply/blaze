(ns blaze.db.node.protocols)

(defprotocol Node
  (-db [node])
  (-sync [node] [node t])
  (-submit-tx [node tx-ops])
  (-tx-result [node t])
  (-subscribe-changes! [node type name subscriber]))
