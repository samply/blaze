(ns blaze.db.tx-log.local
  "A transaction log which is suitable only for standalone (single node) setups.

  Uses an exclusive key-value store to persist the transaction log using the
  default column family. The single key-value index is populated where keys are
  the point in time `t` of the transaction and the values are transaction
  commands and instants.

  The complete state of the transaction log is held in a single atom. On
  submit, the next `t` is assigned and the transaction data is buffered in one
  `swap!`, without blocking the calling thread. If the buffer is full, submits
  are rejected with a busy anomaly. A store loop running on a single thread
  named `local-tx-log` waits on a signal completed by each submit, stores all
  unstored transaction data of the buffer in one batch and advances the point
  in time `stored-t` up to which transaction data is durably stored.
  Pollers only ever see transaction data up to `stored-t` - from the buffer,
  including any local payload, once they caught up and from storage while they
  are behind. Stored transaction data is retained in the buffer until the
  poller acknowledges it via its offset."
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.byte-string :as bs]
   [blaze.db.impl.iterators :as i]
   [blaze.db.kv :as kv]
   [blaze.db.node.util :as node-util]
   [blaze.db.tx-log :as tx-log]
   [blaze.db.tx-log.local.codec :as codec]
   [blaze.executors :as ex]
   [blaze.module :as m :refer [reg-collector]]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [java-time.api :as time]
   [prometheus.alpha :as prom :refer [defhistogram]]
   [taoensso.timbre :as log])
  (:import
   [com.google.common.primitives Longs]
   [java.lang AutoCloseable]
   [java.util.concurrent TimeUnit]))

(set! *warn-on-reflection* true)

(defhistogram duration-seconds
  "Durations in local transaction log."
  {:namespace "blaze"
   :subsystem "db_tx_log"}
  (take 16 (iterate #(* 2 %) 0.00001))
  "op")

(def ^:private ^:const max-poll-size 50)
(def ^:private ^:const max-buffer-size 1024)

(defn- stored-tx-data [kv-store offset]
  (log/trace "fetch tx-data from storage offset =" offset)
  (with-open [snapshot (kv/new-snapshot kv-store)]
    (let [start-key (bs/from-byte-array (codec/encode-key offset))
          tx-data (i/entries snapshot :default (map codec/decode-tx-data)
                             start-key)]
      (into [] (take max-poll-size) tx-data))))

(defn- assoc-local-payload [tx-data local-payload]
  (cond-> tx-data local-payload (assoc :local-payload local-payload)))

(defn- add-entry
  "Assigns the next `t` and puts the transaction data into the buffer.

  Returns `state` unchanged if the buffer is full."
  [{:keys [t buffer] :as state} clock tx-cmds local-payload future]
  (if (< (count buffer) max-buffer-size)
    (let [t (inc t)]
      (-> (assoc state :t t)
          (update :buffer assoc t (-> {:t t :instant (time/instant clock)
                                       :tx-cmds tx-cmds :future future}
                                      (assoc-local-payload local-payload)))))
    state))

(defn- unstored-entries
  "Returns all entries of the buffer that are not yet stored."
  [{:keys [stored-t buffer]}]
  (into [] (map val) (subseq buffer > stored-t)))

(defn- store-entries!
  "Stores `entries` in one batch. Returns an anomaly on failure."
  [kv-store entries]
  (log/trace "store" (count entries) "transaction data entries")
  (ba/try-anomaly
   (kv/put! kv-store (mapv (fn [{:keys [t instant tx-cmds]}]
                             (codec/encode-entry t instant tx-cmds))
                           entries))))

(defn- advance-stored-t
  "Advances `stored-t` and renews the signal, waking up blocked pollers."
  [state stored-t]
  (assoc state :stored-t stored-t :signal (ac/future)))

(defn- remove-entries [state ts]
  (update state :buffer #(reduce dissoc % ts)))

(defn- store!
  "Stores `entries` in one batch, advances `stored-t` and completes the
  futures of the submitters.

  If storing fails, the affected entries are removed from the buffer, leaving
  a gap in `t`, and their futures complete exceptionally."
  [kv-store state entries]
  (let [stored-t (:t (peek entries))
        result (store-entries! kv-store entries)]
    (if (ba/anomaly? result)
      (let [[{:keys [signal]}]
            (swap-vals! state #(-> (remove-entries % (map :t entries))
                                   (advance-stored-t stored-t)))]
        (ac/complete! signal nil)
        (run! #(ac/complete-exceptionally! (:future %) (ba/ex-anom result))
              entries))
      (let [[{:keys [signal]}] (swap-vals! state advance-stored-t stored-t)]
        (ac/complete! signal nil)
        (run! #(ac/complete! (:future %) (:t %)) entries)))))

(defn- renew-store-signal [state]
  (assoc state :store-signal (ac/future)))

(defn- store-loop!
  "Runs the store loop until the transaction log is closed and the buffer
  contains no unstored transaction data anymore.

  Stores all currently unstored transaction data of the buffer in one batch
  or, if there is none, waits on the store signal that is completed on each
  submit. Renews the store signal before looking at the buffer, so that no
  submit is missed. Has to be run in a single thread in order to store
  transaction data in order."
  [kv-store state]
  (loop []
    (let [{:keys [closed? store-signal] :as current-state}
          (swap! state renew-store-signal)
          entries (unstored-entries current-state)]
      (cond
        (seq entries) (do (store! kv-store state entries) (recur))
        (not closed?) (do (ac/join store-signal) (recur))))))

(defn- trim-buffer
  "Removes transaction data acknowledged by the poller (`t` below `offset`)
  from the buffer.

  Never removes transaction data that is not yet stored."
  [{:keys [stored-t buffer] :as state} offset]
  (let [keep-t (min offset (inc stored-t))]
    (cond-> state
      (some-> (ffirst buffer) (< keep-t))
      (assoc :buffer (into (sorted-map) (subseq buffer >= keep-t))))))

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

(defn- busy-anomaly []
  (ba/busy (format "The transaction log buffer with a capacity of %d transactions is full. Please try again later." max-buffer-size)))

(deftype LocalTxLog [kv-store clock storer-executor state]
  tx-log/TxLog
  (-submit [_ tx-cmds local-payload]
    (log/trace "submit" (count tx-cmds) "tx-cmds")
    (let [timer (prom/timer duration-seconds "submit")
          future (ac/future)
          [old {:keys [store-signal] :as new}]
          (swap-vals! state add-entry clock tx-cmds local-payload future)]
      (-> (if (identical? old new)
            (ac/completed-future (busy-anomaly))
            (do (ac/complete! store-signal nil)
                future))
          (ac/when-complete
           (fn [_ _]
             (prom/observe-duration! timer))))))

  (-last-t [_]
    (ac/completed-future (:t @state)))

  (-poll [_ offset timeout]
    (log/trace "poll transaction data with offset =" offset)
    (loop [{:keys [stored-t signal] :as current-state}
           (swap! state trim-buffer offset)
           wait? true]
      (if (<= offset stored-t)
        (or (buffered-tx-data current-state offset)
            (stored-tx-data kv-store offset))
        (when wait?
          (deref signal (time/as timeout :millis) nil)
          (recur @state false)))))

  AutoCloseable
  (close [_]
    (ex/shutdown! storer-executor)
    (let [{:keys [store-signal]} (swap! state assoc :closed? true)]
      (ac/complete! store-signal nil))
    (when-not (ex/await-termination storer-executor 10 TimeUnit/SECONDS)
      (log/warn "Got timeout while stopping the storer thread"))))

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
                    them via its offset; while it holds max-buffer-size
                    entries, submits are rejected with a busy anomaly
  * :signal       - a CompletableFuture that is completed and renewed
                    whenever :stored-t advances, waking up blocked pollers
  * :store-signal - a CompletableFuture that is completed on each submit and
                    renewed by the store loop, waking it up to store new
                    transaction data
  * :closed?      - true once the transaction log is closed, terminating the
                    store loop"
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
  (let [state (atom (init-state kv-store))
        storer-executor (ex/single-thread-executor
                         (node-util/thread-name-template key "local-tx-log"))]
    (ex/execute! storer-executor #(store-loop! kv-store state))
    (->LocalTxLog kv-store clock storer-executor state)))

(defmethod ig/halt-key! :blaze.db.tx-log/local
  [key tx-log]
  (log/info "Close" (node-util/component-name key "local transaction log"))
  (.close ^AutoCloseable tx-log))

(reg-collector ::duration-seconds
  duration-seconds)
