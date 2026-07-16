(ns blaze.db.tx-log.local
  "A transaction log which is suitable only for standalone (single node) setups.

  Uses an exclusive key-value store to persist the transaction log using the
  default column family. The single key-value index is populated where keys are
  the point in time `t` of the transaction and the values are transaction
  commands and instants.

  The complete state of the transaction log is held in a single atom. On
  submit, the next `t` is assigned and the transaction data is put into a
  buffer in one `swap!`. If the buffer is full, submits are rejected with a
  busy anomaly. A single thread named `local-tx-log` stores all unstored
  transaction data of the buffer in one batch and advances the point in time
  `stored-t` up to which transaction data is durably stored. Queues only ever
  offer transaction data up to `stored-t` - from the buffer once they caught
  up and from storage while they are behind."
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

(defn- tx-data [kv-store offset]
  (log/trace "fetch tx-data from storage offset =" offset)
  (with-open [snapshot (kv/new-snapshot kv-store)]
    (let [start-key (bs/from-byte-array (codec/encode-key offset))
          tx-data (i/entries snapshot :default (map codec/decode-tx-data)
                             start-key)]
      (into [] (take max-poll-size) tx-data))))

(defn- assoc-local-payload [tx-data local-payload]
  (cond-> tx-data local-payload (assoc :local-payload local-payload)))

;; The state contains the following keys:
;;  * :t - the last assigned point in time
;;  * :stored-t - the highest `t` up to which all transaction data is stored,
;;                gaps of failed writes included
;;  * :buffer - a sorted map of `t` to transaction data that is not yet stored
;;              or retained for queues that didn't consume it yet
;;  * :queues - a map of queue keys to the next `t` the queue will offer from
;;              the buffer
;;  * :signal - a CompletableFuture that is completed and renewed whenever
;;              `stored-t` advances, waking up blocked pollers

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

(defn- trim
  "Removes stored transaction data no longer needed by any queue from the
  buffer."
  [{:keys [stored-t queues buffer] :as state}]
  (let [keep-t (reduce min (inc stored-t) (vals queues))]
    (assoc state :buffer (into (sorted-map) (subseq buffer >= keep-t)))))

(defn- advance-stored-t [state stored-t]
  (-> (assoc state :stored-t stored-t :signal (ac/future))
      (trim)))

(defn- remove-entries [state ts]
  (update state :buffer #(reduce dissoc % ts)))

(defn- unstored-entries [{:keys [stored-t buffer]}]
  (into [] (map val) (subseq buffer > stored-t)))

(defn- store-entries!
  "Stores `entries` in one batch. Returns an anomaly on failure."
  [kv-store entries]
  (log/trace "store" (count entries) "transaction data entries")
  (ba/try-anomaly
   (kv/put! kv-store (mapv (fn [{:keys [t instant tx-cmds]}]
                             (codec/encode-entry t instant tx-cmds))
                           entries))))

(defn- store!
  "Stores all currently unstored transaction data of the buffer in one batch,
  advances `stored-t` and completes the futures of the submitters.

  Has to be run in a single thread in order to store transaction data in
  order."
  [kv-store state]
  (let [entries (unstored-entries @state)]
    (when (seq entries)
      (let [stored-t (:t (peek entries))
            result (store-entries! kv-store entries)]
        (if (ba/anomaly? result)
          (let [[{:keys [signal]}] (swap-vals! state #(-> (remove-entries % (map :t entries))
                                                          (advance-stored-t stored-t)))]
            (ac/complete! signal nil)
            (run! #(ac/complete-exceptionally! (:future %) (ba/ex-anom result)) entries))
          (let [[{:keys [signal]}] (swap-vals! state advance-stored-t stored-t)]
            (ac/complete! signal nil)
            (run! #(ac/complete! (:future %) (:t %)) entries)))))))

(defn- take-buffered
  "Returns the transaction data the queue under `queue-key` can consume from
  the buffer - starting at its cursor and up to `stored-t`."
  [{:keys [buffer stored-t queues]} queue-key]
  (when-let [cursor (queues queue-key)]
    (into []
          (comp (take-while (fn [[t]] (<= t stored-t)))
                (take max-poll-size)
                (map val)
                (map #(dissoc % :future)))
          (subseq buffer >= cursor))))

(defn- advance-cursor
  "Advances the cursor of the queue under `queue-key` past the transaction
  data returned by `take-buffered`.

  Returns `state` unchanged if there is nothing to consume."
  [state queue-key]
  (let [tx-data (take-buffered state queue-key)]
    (if (seq tx-data)
      (-> (assoc-in state [:queues queue-key] (inc (:t (peek tx-data))))
          (trim))
      state)))

(defn- advance-cursor-to [state queue-key cursor]
  (-> (update-in state [:queues queue-key] max cursor)
      (trim)))

(defn- poll-buffer!
  "Returns the transaction data the queue under `queue-key` can consume from
  the buffer, advancing its cursor.

  If there is nothing to consume, waits up to `timeout` for `stored-t` to
  advance and tries a second time."
  [state queue-key timeout]
  (loop [wait? true]
    (let [[old new] (swap-vals! state advance-cursor queue-key)]
      (cond
        (not (identical? old new)) (take-buffered old queue-key)
        wait? (do (deref (:signal old) (time/as timeout :millis) nil)
                  (recur false))
        :else []))))

(deftype LocalQueue [kv-store state queue-key offset queue-start unsubscribe!]
  tx-log/Queue
  (-poll [_ timeout]
    (let [current-offset @offset]
      (if (< current-offset queue-start)
        (let [tx-data (tx-data kv-store current-offset)]
          (if-let [{last-t :t} (last tx-data)]
            (do (vreset! offset (inc last-t))
                (swap! state advance-cursor-to queue-key (inc last-t))
                tx-data)
            (do (vreset! offset queue-start)
                (poll-buffer! state queue-key timeout))))
        (poll-buffer! state queue-key timeout))))

  AutoCloseable
  (close [_]
    (log/trace "close queue")
    (unsubscribe!)))

(defn- register-queue
  "Registers a queue cursor under `queue-key`, starting at `offset` but at
  least past `stored-t`, so that the queue only offers transaction data from
  the buffer that is already stored when it becomes available."
  [{:keys [stored-t] :as state} queue-key offset]
  (assoc-in state [:queues queue-key] (max offset (inc stored-t))))

(defn- unregister-queue [state queue-key]
  (-> (update state :queues dissoc queue-key)
      (trim)))

(defn- busy-anomaly []
  (ba/busy (format "The transaction log buffer with a capacity of %d transactions is full. Please try again later." max-buffer-size)))

(deftype LocalTxLog [kv-store clock submit-executor state]
  tx-log/TxLog
  (-submit [_ tx-cmds local-payload]
    (log/trace "submit" (count tx-cmds) "tx-cmds")
    (let [timer (prom/timer duration-seconds "submit")
          future (ac/future)
          [old new] (swap-vals! state add-entry clock tx-cmds local-payload future)]
      (-> (if (identical? old new)
            (ac/completed-future (busy-anomaly))
            (do (ex/execute! submit-executor #(store! kv-store state))
                future))
          (ac/when-complete
           (fn [_ _]
             (prom/observe-duration! timer))))))

  (-last-t [_]
    (ac/completed-future (:t @state)))

  (-new-queue [_ offset]
    (let [queue-key (Object.)
          queue-start (get-in (swap! state register-queue queue-key offset)
                              [:queues queue-key])]
      (log/trace "new-queue offset =" offset "queue-start =" queue-start)
      (->LocalQueue kv-store state queue-key (volatile! offset) queue-start
                    #(swap! state unregister-queue queue-key))))

  AutoCloseable
  (close [_]
    (ex/shutdown! submit-executor)
    (when-not (ex/await-termination submit-executor 10 TimeUnit/SECONDS)
      (log/warn "Got timeout while stopping the submit executor"))))

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

(defmethod ig/init-key :blaze.db.tx-log/local
  [key {:keys [kv-store clock]}]
  (log/info "Open" (node-util/component-name key "local transaction log"))
  (let [t (or (last-t kv-store) 0)]
    (->LocalTxLog kv-store clock
                  (ex/single-thread-executor
                   (node-util/thread-name-template key "local-tx-log"))
                  (atom {:t t :stored-t t :buffer (sorted-map) :queues {}
                         :signal (ac/future)}))))

(defmethod ig/halt-key! :blaze.db.tx-log/local
  [key tx-log]
  (log/info "Close" (node-util/component-name key "local transaction log"))
  (.close ^AutoCloseable tx-log))

(reg-collector ::duration-seconds
  duration-seconds)
