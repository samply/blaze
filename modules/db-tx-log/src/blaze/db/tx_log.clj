(ns blaze.db.tx-log
  "Protocols for transaction log backend implementations."
  (:import
    [java.io Closeable]))


(defprotocol TxLog
  "The central transaction log shared between all nodes.

  There might be implementations of TxLog only suitable for single node setups."

  (-submit [tx-log tx-cmds])

  (-last-t [tx-log])

  (-new-queue [tx-log offset]))


(defn submit
  "Submits `tx-cmds` (transaction commands) to `tx-log`.

  Returns a CompletableFuture that will complete with point in time `t` of the
  potentially valid transaction or complete exceptionally with an anomaly in
  case of errors.

  Note: This function only ensures that the transaction is committed into the
  log and will be handled in the future. So a positive return value doesn't mean
  that the transaction itself was successful."
  [tx-log tx-cmds]
  (-submit tx-log tx-cmds))


(defn last-t
  "Returns the last `t` submitted to `tx-log`."
  [tx-log]
  (-last-t tx-log))


(defn new-queue
  "Returns a new queue starting at `offset`.

  The queue has to be closed after usage."
  ^Closeable
  [tx-log offset]
  (-new-queue tx-log offset))


(defprotocol Queue
  (-poll [queue timeout]))


(defn poll!
  "Retrieves and removes the head, a collection of transaction data, of `queue`,
  waiting up to `timeout` if necessary for transaction data to become available."
  [queue timeout]
  (-poll queue timeout))
