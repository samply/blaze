(ns blaze.db.tx-log.local
  "A transaction log which is suitable only for standalone (single node) setups.

  Uses an exclusive key-value store to persist the transaction log using the
  default column family. The single key-value index is populated where keys are
  the point in time `t` of the transaction and the values are transaction
  commands and instants.

  The complete state of the transaction log is held in a single atom. On
  submit, the next `t` is assigned and the transaction data is buffered in one
  `swap!`, without blocking the calling thread. After the transaction log is
  closed, submits are rejected with an unavailable anomaly. The buffer isn't
  bounded itself. It only holds transaction data the poller hasn't acknowledged
  yet, so it is bound by the maximum number of in-flight transactions the node
  allows. A store loop running on a single daemon thread named
  `local-tx-log-storer` waits on a signal completed by each submit, stores all
  unstored transaction data of the buffer in one batch and advances the point
  in time `stored-t` up to which transaction data is durably stored. Closing
  waits until that thread stored all transaction data buffered before the close
  and exited. Pollers only ever see transaction data up to `stored-t` - from
  the buffer, including any local payload, once they caught up and from storage
  while they are behind. Stored transaction data is retained in the buffer
  until the poller acknowledges it via its offset. A poll that finds no
  transaction data waits for its timeout instead of returning none right away,
  so that a poller sitting on the gap in `t` a failed store leaves behind
  doesn't spin.

  The number of entries in the buffer is exposed as the gauge
  `blaze_db_tx_log_buffer_entries`, split by the state of the entries. The
  `unstored` entries wait for the store loop, so they grow if storing is slower
  than submitting. The `retained` entries are already stored and wait for the
  poller to acknowledge them, so they grow if indexing is slower than storing.
  Both together are the memory the buffer occupies."
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.byte-string :as bs]
   [blaze.db.impl.iterators :as i]
   [blaze.db.impl.thread :as thread]
   [blaze.db.kv :as kv]
   [blaze.db.node.util :as node-util]
   [blaze.db.tx-log :as tx-log]
   [blaze.db.tx-log.local.codec :as codec]
   [blaze.db.tx-log.local.spec]
   [blaze.metrics.core :as metrics]
   [blaze.module :as m :refer [reg-collector]]
   [blaze.time :as bt]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [prometheus.alpha :as prom :refer [defhistogram]]
   [taoensso.timbre :as log])
  (:import
   [com.google.common.primitives Longs]
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

(defhistogram duration-seconds
  "Durations in local transaction log.

  The `node` label distinguishes the individual nodes like main and admin.

  The op `submit` measures the time from the start of the submit until the
  transaction data is durably stored, including the time spent waiting in the
  buffer for the store loop. Submits rejected because the transaction log is
  closed are not observed, because their near-zero durations would distort the
  submit latency.

  The op `store-round` measures one round of the store loop that had
  transaction data to store, from looking at the buffer until the futures of
  the submitters are completed. It covers everything the store loop does
  besides waiting for transaction data to arrive, so the rate of its sum is the
  utilization of the storer thread: at 100 % the loop never waits."
  {:namespace "blaze"
   :subsystem "db_tx_log"}
  (take 16 (iterate #(* 2 %) 0.00001))
  "node" "op")

(def ^:private ^:const max-poll-size
  "The maximum number of transactions one poll returns, the same as the default
  `max.poll.records` of the Kafka consumer, so that the indexing loop of the
  node sees batches of the same size with both transaction logs."
  500)

(defhistogram store-batch-entries
  "Number of transaction data entries stored in one batch.

  The store loop stores all unstored transaction data of the buffer in one
  batch. So the number of entries per batch shows how many submits are
  coalesced.

  The `node` label distinguishes the individual nodes like main and admin."
  {:namespace "blaze"
   :subsystem "db_tx_log"}
  (take 11 (iterate #(* 2 %) 1))
  "node")

(defn- stored-tx-data [kv-store offset]
  (log/trace "fetch tx-data from storage offset =" offset)
  (with-open [snapshot (kv/new-snapshot kv-store)]
    (let [start-key (bs/from-byte-array (codec/encode-key offset))
          tx-data (i/entries snapshot :default (map codec/decode-tx-data)
                             start-key)]
      (into [] (take max-poll-size) tx-data))))

(defn- tx-data [tx-cmds local-payload future]
  (cond-> {:tx-cmds tx-cmds :future future}
    local-payload (assoc :local-payload local-payload)))

(defn- add-entry
  "Assigns the next `t` and puts the transaction data into the buffer.

  Returns `state` unchanged if the transaction log is closed."
  [{:keys [closed? t] :as state} clock tx-data]
  (if closed?
    state
    (let [t (inc t)
          tx-data (assoc tx-data :t t :instant (bt/instant clock))]
      (-> (assoc state :t t)
          (update :buffer assoc t tx-data)))))

(defn- unstored-entries
  "Returns all entries of the buffer that are not yet stored."
  [{:keys [stored-t buffer]}]
  (mapv val (subseq buffer > stored-t)))

(defn- encode-entry [{:keys [t instant tx-cmds]}]
  (codec/encode-entry t instant tx-cmds))

(defn- store-entries!
  "Stores `entries` in one atomic batch, observing the number of entries stored.
  Returns an anomaly on failure."
  [node-name kv-store entries]
  (let [num-entries (count entries)]
    (log/trace "store" num-entries "transaction data entries")
    (ba/try-anomaly
     (kv/put! kv-store (mapv encode-entry entries))
     (prom/observe! store-batch-entries node-name num-entries))))

(defn- advance-stored-t
  "Advances `stored-t` and renews the signal, waking up blocked pollers."
  [state stored-t]
  (assoc state :stored-t stored-t :signal (ac/future)))

(defn- remove-entries [state ts]
  (update state :buffer #(reduce dissoc % ts)))

(defn- fail-entries!
  "Removes `entries` from the buffer, leaving a gap in `t`, advances `stored-t`
  and completes the futures of the submitters exceptionally with `anomaly`.

  Completes the futures asynchronously, like `complete-entries!` does, because
  the store loop keeps running after a failure."
  [state entries anomaly]
  (let [[{:keys [signal]}]
        (swap-vals! state #(-> (remove-entries % (map :t entries))
                               (advance-stored-t (:t (peek entries)))))
        supplier (constantly anomaly)]
    (ac/complete! signal nil)
    (run! #(ac/complete-async! (:future %) supplier) entries)))

(defn- complete-entries!
  "Advances `stored-t` and completes the futures of the submitters with the `t`
  of their transaction data.

  Completes the futures asynchronously, because otherwise all the work
  depending on them would run on the storer thread, delaying the durable
  storage of the transaction data of every other submitter."
  [state entries]
  (let [[{:keys [signal]}]
        (swap-vals! state advance-stored-t (:t (peek entries)))]
    (ac/complete! signal nil)
    (run! (fn [{:keys [future t]}] (ac/complete-async! future (constantly t)))
          entries)))

(defn- store!
  "Stores `entries` in one atomic batch, advances `stored-t` and completes the
  futures of the submitters.

  If storing fails, the affected entries are removed from the buffer, leaving
  a gap in `t`, and their futures complete exceptionally.

  Never throws. An unexpected error is logged and handled like a failure to
  store, because a throw would terminate the store loop, leaving the
  submitters of `entries` and all future ones waiting on futures that are
  never completed."
  [node-name kv-store state entries]
  (try
    (let [result (store-entries! node-name kv-store entries)]
      (if (ba/anomaly? result)
        (fail-entries! state entries result)
        (complete-entries! state entries)))
    (catch Throwable e
      (log/error "Unexpected error while storing transaction data:"
                 (ex-message e))
      (fail-entries! state entries (ba/anomaly e)))))

(defn- renew-store-signal [state]
  (assoc state :store-signal (ac/future)))

(defn- store-loop
  "Runs the store loop until the transaction log is closed and the buffer
  contains no unstored transaction data anymore.

  Stores all currently unstored transaction data of the buffer in one batch
  or, if there is none, waits on the store signal that is completed on each
  submit. Renews the store signal before looking at the buffer, so that no
  submit is missed. Has to be run in a single thread in order to store
  transaction data in order.

  Storing never throws, so that the loop keeps running after an unexpected
  error.

  Completes `store-finished` when it exits, even if it exits because of an
  unexpected error, so that closing the transaction log never waits forever.

  Observes the duration of every round that had transaction data to store,
  starting before the buffer is examined, so that everything but the waiting
  is measured. Rounds that only wait are not observed, because their duration
  is idle time."
  [node-name kv-store state store-finished]
  (log/trace "enter storer")
  (try
    (loop []
      (let [timer (prom/timer duration-seconds node-name "store-round")
            {:keys [closed? store-signal] :as current-state}
            (swap! state renew-store-signal)
            entries (unstored-entries current-state)]
        (cond
          (seq entries) (do (store! node-name kv-store state entries)
                            (prom/observe-duration! timer)
                            (recur))
          (not closed?) (do (ac/join store-signal) (recur)))))
    (finally
      (ac/complete! store-finished true)
      (log/trace "exit storer"))))

(defn- trim-buffer
  "Removes transaction data acknowledged by the poller (`t` below `offset`)
  from the buffer.

  Never removes transaction data that is not yet stored."
  [{:keys [stored-t buffer] :as state} offset]
  (let [keep-t (min offset (inc stored-t))]
    (cond-> state
      (some-> (ffirst buffer) (< keep-t))
      (remove-entries (take-while #(< % keep-t) (keys buffer))))))

(defn- buffered-tx-data
  "Returns the transaction data from the buffer starting at `offset`, limited
  to already stored entries, or nil if the buffer doesn't cover `offset`."
  [{:keys [stored-t buffer]} offset]
  (when-let [[first-t] (first buffer)]
    (when (<= first-t offset)
      (into []
            (comp (take-while (fn [[t]] (<= t stored-t)))
                  (take max-poll-size)
                  (map val)
                  (map #(dissoc % :future)))
            (subseq buffer >= offset)))))

(deftype LocalTxLog [node-name kv-store clock state store-finished]
  tx-log/TxLog
  (-submit [_ tx-cmds local-payload]
    (log/trace "submit" (count tx-cmds) "tx-cmds")
    (let [future (ac/future)
          tx-data (tx-data tx-cmds local-payload future)
          [old new] (swap-vals! state add-entry clock tx-data)]
      (if (identical? old new)
        (ac/completed-future (ba/unavailable "The transaction log is closed."))
        (let [timer (prom/timer duration-seconds node-name "submit")]
          (ac/complete! (:store-signal new) nil)
          (ac/when-complete
           future
           (fn [_ _]
             (prom/observe-duration! timer)))))))

  (-last-t [_]
    (ac/completed-future (:t @state)))

  (-poll [_ offset timeout]
    (log/trace "poll transaction data with offset =" offset)
    (loop [{:keys [stored-t signal] :as current-state}
           (swap! state trim-buffer offset)
           wait? true]
      (let [tx-data (when (<= offset stored-t)
                      (or (buffered-tx-data current-state offset)
                          (stored-tx-data kv-store offset)))]
        (if (seq tx-data)
          tx-data
          (when wait?
            (deref signal (bt/as-millis timeout) nil)
            (recur @state false))))))

  AutoCloseable
  (close [_]
    (let [{:keys [store-signal]} (swap! state assoc :closed? true)]
      (ac/complete! store-signal nil))
    ;; the wait isn't bounded by a timeout, because the storer thread stores the
    ;; transaction data buffered before the close first. A timeout would only let
    ;; Integrant shut down the key-value store under a still running store loop,
    ;; which is what this wait exists to prevent
    @store-finished))

(defn- last-t
  "Returns the last (newest) point in time, the transaction log has persisted
  in `kv-store` or nil if the log is empty."
  [kv-store]
  (with-open [snapshot (kv/new-snapshot kv-store)
              iter (kv/new-iterator snapshot :default)]
    (kv/seek-to-last! iter)
    (when (kv/valid? iter)
      (Longs/fromByteArray (kv/key iter)))))

(defmethod m/pre-init-spec :blaze.db.tx-log/local [_]
  (s/keys :req-un [:blaze.db/kv-store :blaze/clock]))

(defn- init-state
  "The state contains the following keys:

  * :t            - the last assigned point in time
  * :stored-t     - the highest `t` up to which transaction data is durably
                    stored, gaps of failed writes included; pollers only ever
                    see transaction data up to :stored-t
  * :buffer       - a sorted map of `t` to buffered transaction data,
                    including any local payload: entries not yet stored plus
                    stored entries retained until the poller acknowledges
                    them via its offset
  * :signal       - a CompletableFuture that is completed and renewed
                    whenever :stored-t advances, waking up blocked pollers
  * :store-signal - a CompletableFuture that is completed on each submit and
                    renewed by the store loop, waking it up to store new
                    transaction data
  * :closed?      - true once the transaction log is closed, terminating the
                    store loop and rejecting further submits with an
                    unavailable anomaly"
  [kv-store]
  (let [t (or (last-t kv-store) 0)]
    {:t t
     :stored-t t
     :buffer (sorted-map)
     :signal (ac/future)
     :store-signal (ac/future)
     :closed? false}))

(defmethod ig/init-key :blaze.db.tx-log/local
  [key {:keys [kv-store clock]}]
  (log/info "Open" (node-util/component-name key "local transaction log"))
  (let [node-name (node-util/node-name key)
        state (atom (init-state kv-store))
        store-finished (ac/future)]
    (thread/start-thread! #(store-loop node-name kv-store state store-finished)
                          (node-util/thread-name key "local-tx-log-storer"))
    (->LocalTxLog node-name kv-store clock state store-finished)))

(defmethod ig/halt-key! :blaze.db.tx-log/local
  [key tx-log]
  (log/info "Close" (node-util/component-name key "local transaction log"))
  (.close ^AutoCloseable tx-log))

(def ^:private buffer-samples-xf
  "Transducer from local transaction logs to the number of entries in their
  buffer, `unstored` and `retained`, under the label values of that
  transaction log."
  (mapcat
   (fn [tx-log]
     ;; both numbers are derived from a single state snapshot, so they can't
     ;; contradict each other by racing writes of the store loop
     (let [node-name (.-node-name ^LocalTxLog tx-log)
           {:keys [stored-t buffer]} @(.-state ^LocalTxLog tx-log)
           unstored (count (subseq buffer > stored-t))]
       [{:label-values [node-name "unstored"] :value (double unstored)}
        {:label-values [node-name "retained"]
         :value (double (- (count buffer) unstored))}]))))

(defmethod m/pre-init-spec ::buffer-collector [_]
  (s/keys :req-un [::tx-logs]))

(defmethod ig/init-key ::buffer-collector
  [_ {:keys [tx-logs]}]
  (metrics/collector
    [(metrics/gauge-metric
      "blaze_db_tx_log_buffer_entries"
      "The number of transaction data entries in the buffer of the local transaction log, either not yet stored or stored and retained until the poller acknowledges them."
      ["node" "state"]
      (into [] buffer-samples-xf (vals tx-logs)))]))

(derive ::buffer-collector :blaze.metrics/collector)

(reg-collector ::duration-seconds
  duration-seconds)

(reg-collector ::store-batch-entries
  store-batch-entries)
