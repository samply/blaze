(ns blaze.db.tx-log
  "Protocols for transaction log backend implementations.")


(defprotocol TxLog
  "The central transaction log shared between all nodes.

  There might be implementations of TxLog only suitable for single node setups."

  (-submit [tx-log tx-ops])

  (log-queue [tx-log from-t]
    "Returns a queue"))


(defn submit
  "Submits the transaction. Returns a deferred with the `t` of the potentially
  valid transaction or the `t` of the last realized transaction if the
  transaction resulted in a no-op."
  [tx-log tx-ops]
  (-submit tx-log tx-ops))


(defprotocol ResourceConsumer
  )
