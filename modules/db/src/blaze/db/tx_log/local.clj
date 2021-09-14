(ns blaze.db.tx-log.local
  "A transaction log which is suitable only for standalone (single node) setups.

  Uses an exclusive key-value store to persist the transaction log using the
  default column family. The single key-value index is populated were keys are
  the point in time `t` of the transaction and the values are transaction
  commands and instants.

  Uses a single thread named `local-tx-log` to increment the point in time `t`,
  store the transaction and transfers it to listening queues."
  (:require
    [blaze.async.comp :as ac]
    [blaze.byte-string :as bs]
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.impl.iterators :as i]
    [blaze.db.kv :as kv]
    [blaze.db.tx-log :as tx-log]
    [blaze.db.tx-log.local.codec :as codec]
    [blaze.executors :as ex]
    [blaze.module :refer [reg-collector]]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [java-time :as time]
    [prometheus.alpha :as prom :refer [defhistogram]]
    [taoensso.timbre :as log])
  (:import
    [java.io Closeable]
    [java.time Instant]
    [java.util.concurrent
     ArrayBlockingQueue BlockingQueue ExecutorService TimeUnit]))


(set! *warn-on-reflection* true)


(defhistogram duration-seconds
  "Durations in transaction log."
  {:namespace "blaze"
   :subsystem "db"
   :name "tx_log_duration_seconds"}
  (take 16 (iterate #(* 2 %) 0.00001))
  "op")


(def ^:private ^:const max-poll-size 50)


(defn- tx-data [kv-store offset]
  (log/trace "fetch tx-data from storage offset =" offset)
  (with-open [snapshot (kv/new-snapshot kv-store)
              iter (kv/new-iterator snapshot)]
    (let [key (bs/from-byte-array (codec/encode-key offset))]
      (into [] (take max-poll-size) (i/kvs! iter codec/decode-tx-data key)))))


(defn- poll [^BlockingQueue queue timeout]
  (log/trace "poll in-memory queue with timeout =" timeout)
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
        (poll queue timeout))))

  Closeable
  (close [_]
    (log/trace "close queue")
    (unsubscribe!)))


(defn- store-tx-data! [kv-store {:keys [t instant tx-cmds]}]
  (log/trace "store transaction data with t =" t)
  (kv/put! kv-store (codec/encode-key t) (codec/encode-tx-data instant tx-cmds)))


(defn- transfer-tx-data! [queues tx-data]
  (log/trace "transfer transaction data to" (count queues) "queue(s)")
  (doseq [^BlockingQueue queue queues]
    (.put queue tx-data)))


(defn- submit!
  "Stores `tx-cmds` and transfers them to pollers on queues.

  Uses `state` to increment the point in time `t`. Stores transaction data
  consisting of the new `t`, the current time taken from `clock` and `tx-cmds`.
  Has to be run in a single thread in order to deliver transaction data in
  order."
  [kv-store clock state tx-cmds]
  (log/trace "submit" (count tx-cmds) "tx-cmds")
  (let [{:keys [t queues]} (swap! state update :t inc)
        tx-data {:t t :instant (Instant/now clock) :tx-cmds tx-cmds}]
    (store-tx-data! kv-store tx-data)
    (transfer-tx-data! (vals queues) [tx-data])
    t))


;; The state contains the following keys:
;;  * :t - the current point in time
;;  * :queues - a map of queues created through `new-queue`
(deftype LocalTxLog [kv-store clock executor state]
  tx-log/TxLog
  (-submit [_ tx-cmds]
    ;; ensures that the submit function is executed in a single thread
    (ac/supply-async
      #(with-open [_ (prom/timer duration-seconds "submit")]
         (submit! kv-store clock state tx-cmds))
      executor))

  (-last-t [_]
    (ac/completed-future (:t @state)))

  (-new-queue [_ offset]
    (let [key (Object.)
          queue (ArrayBlockingQueue. 10)
          queue-start (inc (:t (swap! state update :queues assoc key queue)))]
      (log/trace "new-queue offset =" offset ", queue-start =" queue-start)
      (->LocalQueue kv-store (volatile! offset) queue queue-start
                    #(swap! state update :queues dissoc key)))))


(defn- last-t
  "Returns the last (newest) point in time, the transaction log has persisted
  in `kv-store` or nil if the log is empty."
  [kv-store]
  (with-open [snapshot (kv/new-snapshot kv-store)
              iter (kv/new-iterator snapshot)]
    (kv/seek-to-last! iter)
    (let [buf (bb/allocate-direct Long/BYTES)]
      (when (kv/valid? iter)
        (kv/key! iter buf)
        (when (<= Long/BYTES (bb/remaining buf))
          (bb/get-long! buf))))))


(defmethod ig/pre-init-spec :blaze.db.tx-log/local [_]
  (s/keys :req-un [:blaze.db/kv-store :blaze/clock]))


(defmethod ig/init-key :blaze.db.tx-log/local
  [_ {:keys [kv-store clock]}]
  (log/info "Open local transaction log")
  (->LocalTxLog
    kv-store
    clock
    ;; it's important to have a single thread executor here. See docs of submit
    ;; function.
    (ex/single-thread-executor "local-tx-log")
    (atom {:t (or (last-t kv-store) 0)})))


(defmethod ig/halt-key! :blaze.db.tx-log/local
  [_ tx-log]
  (log/info "Start closing local transaction log")
  (let [executor (.executor ^LocalTxLog tx-log)]
    (.shutdown ^ExecutorService executor)
    (.awaitTermination ^ExecutorService executor 10 TimeUnit/SECONDS))
  (log/info "Done closing local transaction log"))


(derive :blaze.db.tx-log/local :blaze.db/tx-log)


(reg-collector ::duration-seconds
  duration-seconds)
