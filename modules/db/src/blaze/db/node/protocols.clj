(ns blaze.db.node.protocols)

(defprotocol Node
  (-db [node])
  (-sync [node] [node t])
  (-submit-tx [node tx-ops])
  (-tx-result [node t])
  (-subscription-publisher [node type]))
