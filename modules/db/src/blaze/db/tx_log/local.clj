(ns blaze.db.tx-log.local
  "A transaction log which is suitable only for standalone (single node) setups.

  Uses an exclusive key-value store to persist the transaction log using the
  default column family. The single key-value index is populated where keys are
  the point in time `t` of the transaction and the values are transaction
  commands and instants.

  Uses a single thread named `local-tx-log` to increment the point in time `t`,
  store the transaction and transfers it to listening queues."
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
   [java.lang AutoCloseable]
   [java.util.concurrent ArrayBlockingQueue BlockingQueue TimeUnit]
   [java.util.concurrent.locks Lock ReentrantLock]))

(set! *warn-on-reflection* true)

(defhistogram duration-seconds
  "Durations in local transaction log."
  {:namespace "blaze"
   :subsystem "db_tx_log"}
  (take 16 (iterate #(* 2 %) 0.00001))
  "op")

(def ^:private ^:const max-poll-size 50)

(defn- tx-data [kv-store offset]
  (log/trace "fetch tx-data from storage offset =" offset)
  (with-open [snapshot (kv/new-snapshot kv-store)]
    (let [start-key (bs/from-byte-array (codec/encode-key offset))
          tx-data (i/entries snapshot :default (map codec/decode-tx-data)
                             start-key)]
      (into [] (take max-poll-size) tx-data))))

(defn- poll! [^BlockingQueue queue timeout]
  (log/trace "poll in-memory queue with timeout =" (str timeout))
  (.poll queue (time/as timeout :millis) TimeUnit/MILLISECONDS))

(deftype LocalQueue [kv-store offset queue queue-start unsubscribe!]
  tx-log/Queue
  (-poll [_ timeout]
    (let [current-offset @offset]
      (if (< current-offset queue-start)
        (let [tx-data (tx-data kv-store current-offset)]
          (when-let [tx-data (last tx-data)]
            (vreset! offset (inc (:t tx-data))))
          tx-data)
        (poll! queue timeout))))

  AutoCloseable
  (close [_]
    (log/trace "close queue")
    (unsubscribe!)))

(defn- store-tx-data! [kv-store {:keys [t instant tx-cmds]}]
  (log/trace "store transaction data with t =" t)
  (kv/put! kv-store [(codec/encode-entry t instant tx-cmds)]))

(defn- transfer-tx-data! [queues tx-data]
  (log/trace "transfer transaction data to" (count queues) "queue(s)")
  (run! #(.put ^BlockingQueue % tx-data) queues))

(defn- assoc-local-payload [tx-data local-payload]
  (cond-> tx-data local-payload (assoc :local-payload local-payload)))

(defn- submit!
  "Stores `tx-cmds` and transfers them to pollers on queues.

  Uses `state` to increment the point in time `t`. Stores transaction data
  consisting of the new `t`, the current time taken from `clock` and `tx-cmds`.
  Has to be run in a single thread in order to deliver transaction data in
  order."
  [kv-store clock state tx-cmds local-payload]
  (let [{:keys [t queues]} (swap! state update :t inc)
        tx-data {:t t :instant (time/instant clock) :tx-cmds tx-cmds}]
    (store-tx-data! kv-store tx-data)
    (transfer-tx-data! (vals queues) [(assoc-local-payload tx-data local-payload)])
    t))

;; The state contains the following keys:
;;  * :t - the current point in time
;;  * :queues - a map of queues created through `new-queue`
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

  (-new-queue [_ offset]
    (let [key (Object.)
          queue (ArrayBlockingQueue. 10)
          queue-start (inc (:t (swap! state update :queues assoc key queue)))]
      (log/trace "new-queue offset =" offset "queue-start =" queue-start)
      (->LocalQueue kv-store (volatile! offset) queue queue-start
                    #(swap! state update :queues dissoc key)))))

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
  (->LocalTxLog kv-store clock (ReentrantLock.)
                (atom {:t (or (last-t kv-store) 0)})))

(reg-collector ::duration-seconds
  duration-seconds)
