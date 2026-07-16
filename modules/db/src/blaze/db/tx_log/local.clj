(ns blaze.db.tx-log.local
  "A transaction log which is suitable only for standalone (single node) setups.

  Uses an exclusive key-value store to persist the transaction log using the
  default column family. The single key-value index is populated where keys are
  the point in time `t` of the transaction and the values are transaction
  commands and instants.

  Uses a lock to increment the point in time `t` and store the transaction
  data one submit at a time. Pollers read recently stored transaction data,
  including any local payload, from an in-memory tail and older transaction
  data from storage. Submitting blocks while the tail is full until the
  poller acknowledges transaction data via its offset."
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.byte-string :as bs]
   [blaze.db.impl.iterators :as i]
   [blaze.db.kv :as kv]
   [blaze.db.node.util :as node-util]
   [blaze.db.tx-log :as tx-log]
   [blaze.db.tx-log.local.codec :as codec]
   [blaze.module :as m :refer [reg-collector]]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [java-time.api :as time]
   [prometheus.alpha :as prom :refer [defhistogram]]
   [taoensso.timbre :as log])
  (:import
   [com.google.common.primitives Longs]
   [java.util.concurrent.locks Lock ReentrantLock]))

(set! *warn-on-reflection* true)

(defhistogram duration-seconds
  "Durations in local transaction log."
  {:namespace "blaze"
   :subsystem "db_tx_log"}
  (take 16 (iterate #(* 2 %) 0.00001))
  "op")

(def ^:private ^:const max-poll-size 50)
(def ^:private ^:const max-tail-size 10)

(defn- stored-tx-data [kv-store offset]
  (log/trace "fetch tx-data from storage offset =" offset)
  (with-open [snapshot (kv/new-snapshot kv-store)]
    (let [start-key (bs/from-byte-array (codec/encode-key offset))
          tx-data (i/entries snapshot :default (map codec/decode-tx-data)
                             start-key)]
      (into [] (take max-poll-size) tx-data))))

(defn- store-tx-data! [kv-store {:keys [t instant tx-cmds]}]
  (log/trace "store transaction data with t =" t)
  (kv/put! kv-store [(codec/encode-entry t instant tx-cmds)]))

(defn- assoc-local-payload [tx-data local-payload]
  (cond-> tx-data local-payload (assoc :local-payload local-payload)))

(defn- add-to-tail
  "Adds `tx-data` to the tail, advances `:tail-t` and renews the signal."
  [{:keys [tail] :as state} {:keys [t] :as tx-data}]
  (assoc state :tail (assoc tail t tx-data) :tail-t t :signal (ac/future)))

(defn- await-space!
  "Blocks until the tail has space for another transaction.

  Space becomes available when the poller acknowledges transaction data via
  its offset."
  [state]
  (let [{:keys [tail space-signal]} @state]
    (when (<= max-tail-size (count tail))
      (log/trace "await space in the tail")
      @space-signal
      (recur state))))

(defn- submit!
  "Stores `tx-cmds` and transfers them to the in-memory tail.

  Uses `state` to increment the point in time `t`. Stores transaction data
  consisting of the new `t`, the current time taken from `clock` and `tx-cmds`.
  Blocks while the tail is full. Has to be run one submit at a time in order
  to store transaction data in order."
  [kv-store clock state tx-cmds local-payload]
  (let [{:keys [t]} (swap! state update :t inc)
        tx-data {:t t :instant (time/instant clock) :tx-cmds tx-cmds}]
    (store-tx-data! kv-store tx-data)
    (await-space! state)
    (let [tx-data (assoc-local-payload tx-data local-payload)
          [{:keys [signal]}] (swap-vals! state add-to-tail tx-data)]
      (ac/complete! signal nil))
    t))

(defn- trim-tail
  "Removes transaction data acknowledged by the poller (`t` below `offset`)
  from the tail and renews the space signal."
  [{:keys [tail] :as state} offset]
  (cond-> state
    (some-> (ffirst tail) (< offset))
    (-> (update :tail #(into (sorted-map) (subseq % >= offset)))
        (assoc :space-signal (ac/future)))))

(defn- tail-tx-data
  "Returns the transaction data from the in-memory tail starting at `offset`
  or nil if the tail doesn't cover `offset`."
  [{:keys [tail]} offset]
  (when-let [[first-t] (first tail)]
    (when (<= first-t offset)
      (into [] (comp (take max-poll-size) (map val)) (subseq tail >= offset)))))

(deftype LocalTxLog [kv-store clock ^Lock submit-lock state]
  tx-log/TxLog
  (-submit [_ tx-cmds local-payload]
    (log/trace "submit" (count tx-cmds) "tx-cmds")
    (with-open [_ (prom/timer duration-seconds "submit")]
      (.lock submit-lock)
      (try
        (-> (submit! kv-store clock state tx-cmds local-payload)
            ba/try-anomaly
            ac/completed-future)
        (finally
          (.unlock submit-lock)))))

  (-last-t [_]
    (ac/completed-future (:t @state)))

  (-poll [_ offset timeout]
    (log/trace "poll transaction data with offset =" offset)
    (let [[{old-space-signal :space-signal} trimmed-state]
          (swap-vals! state trim-tail offset)]
      (when-not (identical? old-space-signal (:space-signal trimmed-state))
        (ac/complete! old-space-signal nil))
      (loop [{:keys [tail-t signal] :as current-state} trimmed-state
             wait? true]
        (if (<= offset tail-t)
          (or (tail-tx-data current-state offset)
              (stored-tx-data kv-store offset))
          (when wait?
            (deref signal (time/as timeout :millis) nil)
            (recur @state false)))))))

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
  * :tail         - a sorted map of `t` to the most recently stored
                    transaction data, including any local payload; entries
                    are removed once the poller acknowledges them via its
                    offset; while it holds max-tail-size entries, submitting
                    blocks
  * :tail-t       - the point in time of the last transaction data added to
                    the tail; in contrast to :t, which is incremented before
                    the transaction data is stored, storage is guaranteed to
                    contain all transaction data up to :tail-t, so pollers
                    wait for it instead of :t
  * :signal       - a CompletableFuture that is completed and renewed
                    whenever new transaction data is stored, waking up
                    blocked pollers
  * :space-signal - a CompletableFuture that is completed and renewed
                    whenever the poller acknowledges transaction data, waking
                    up submitters blocked on a full tail"
  [kv-store]
  (let [t (or (last-t kv-store) 0)]
    {:t t
     :tail (sorted-map)
     :tail-t t
     :signal (ac/future)
     :space-signal (ac/future)}))

(defmethod ig/init-key :blaze.db.tx-log/local
  [key {:keys [kv-store clock]}]
  (log/info "Open" (node-util/component-name key "local transaction log"))
  (->LocalTxLog kv-store clock (ReentrantLock.) (atom (init-state kv-store))))

(reg-collector ::duration-seconds
  duration-seconds)
