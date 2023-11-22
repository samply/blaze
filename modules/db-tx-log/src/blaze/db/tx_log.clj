(ns blaze.db.tx-log
  "Protocols for transaction log backend implementations."
  (:import
   [java.lang AutoCloseable]))

(defprotocol TxLog
  "The central transaction log shared between all nodes.

  There might be implementations of TxLog only suitable for single node setups."

  (-submit [tx-log tx-cmds local-payload])

  (-last-t [tx-log])

  (-new-queue [tx-log offset]))

(defn submit
  "Submits `tx-cmds` (transaction commands) to `tx-log`.

  Returns a CompletableFuture that will complete with point in time `t` of the
  potentially valid transaction or will complete exceptionally with an anomaly
  in case of errors.

  The `local-payload` will be embedded under :local-payload in the transaction
  data returned from `poll!` if the transaction log supports this feature and
  the poller is on the same node as the submitter.

  Note: This function only ensures that the transaction is committed into the
  log and will be handled in the future. So a positive return value doesn't mean
  that the transaction itself was successful."
  [tx-log tx-cmds local-payload]
  (-submit tx-log tx-cmds local-payload))

(defn last-t
  "Returns a CompletableFuture that will complete with the last point in time
  `t` submitted to `tx-log`."
  [tx-log]
  (-last-t tx-log))

(defn new-queue
  "Returns a new queue starting at `offset`.

  The queue has to be closed after usage."
  ^AutoCloseable
  [tx-log offset]
  (-new-queue tx-log offset))

(defprotocol Queue
  (-poll [queue timeout]))

(defn poll!
  "Retrieves and removes the head, a collection of transaction data, of `queue`,
  waiting up to `timeout` if necessary for transaction data to become available.

  Transaction data optionally contains :local-payload if the transaction was
  submitted on the same node and the transaction log supports this feature."
  [queue timeout]
  (-poll queue timeout))
