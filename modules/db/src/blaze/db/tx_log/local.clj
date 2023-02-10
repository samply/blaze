(ns blaze.db.tx-log.local
  "A transaction log which is suitable for standalone (single node) setups.

  Uses an exclusive key-value store to persist the transaction log using the
  default column family. The single key-value index is populated where keys are
  the point in time `t` of the transaction and the values are transaction
  commands and instants.

  Instead of storing the resource contents in the transaction log index itself,
  a hash of each resource content is generated and stored instead. The resource
  contents are than stored in the resource store for later retrieval."
  (:require
    [blaze.async.comp :as ac]
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.db.impl.iterators :as i]
    [blaze.db.kv :as kv]
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store.spec]
    [blaze.db.tx-log :as tx-log]
    [blaze.db.tx-log.local.codec :as codec]
    [blaze.fhir.hash :as hash]
    [blaze.module :refer [reg-collector]]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [java-time.api :as time]
    [prometheus.alpha :as prom :refer [defhistogram]]
    [taoensso.timbre :as log])
  (:import
    [java.lang AutoCloseable]
    [java.time Instant]
    [java.util.concurrent ArrayBlockingQueue BlockingQueue TimeUnit]
    [java.util.concurrent.locks Lock ReentrantLock]))


(set! *warn-on-reflection* true)


(defhistogram duration-seconds
  "Durations in local transaction log."
  {:namespace "blaze"
   :subsystem "db_tx_log"
   :name "duration_seconds"}
  (take 16 (iterate #(* 2 %) 0.00001))
  "op")


(def ^:private ^:const max-poll-size 50)


(defn- load-resource [resource-store {:keys [hash] :as tx-cmd}]
  (cond-> tx-cmd hash (assoc :resource (rs/get resource-store hash))))


(defn- load-resources [resource-store tx-cmds]
  (mapv (partial load-resource resource-store) tx-cmds))


(defn- tx-data-xf [resource-store]
  (comp (map #(update % :tx-cmds (partial load-resources resource-store)))
        (take max-poll-size)))


(defn- tx-data [kv-store resource-store offset]
  (log/trace "fetch tx-data from storage offset =" offset)
  (with-open [snapshot (kv/new-snapshot kv-store)
              iter (kv/new-iterator snapshot)]
    (let [key (bs/from-byte-array (codec/encode-key offset))]
      (into [] (tx-data-xf resource-store) (i/kvs! iter codec/decode-tx-data key)))))


(defn- poll! [^BlockingQueue queue timeout]
  (log/trace "poll in-memory queue with timeout =" timeout)
  (.poll queue (time/as timeout :millis) TimeUnit/MILLISECONDS))


(deftype LocalQueue
  [kv-store resource-store offset queue queue-start unsubscribe!]
  tx-log/Queue
  (-poll [_ timeout]
    (let [current-offset @offset]
      (if (< current-offset queue-start)
        (let [tx-data (tx-data kv-store resource-store current-offset)]
          (when-let [tx-data (last tx-data)]
            (vreset! offset (inc (:t tx-data))))
          tx-data)
        (poll! queue timeout))))

  AutoCloseable
  (close [_]
    (log/trace "close queue")
    (unsubscribe!)))


(defn- prepare-entries [tx-cmds]
  (reduce
    (fn [r {:keys [resource] :as tx-cmd}]
      (if resource
        (let [hash (hash/generate resource)]
          (-> (update r :entries assoc hash resource)
              (update :tx-cmds conj (assoc tx-cmd :hash hash))))
        (update r :tx-cmds conj tx-cmd)))
    {:entries {}
     :tx-cmds []}
    tx-cmds))


(defn- store-resources! [resource-store tx-cmds]
  (let [{:keys [entries tx-cmds]} (prepare-entries tx-cmds)]
    (-> (rs/put! resource-store entries)
        (ac/then-apply (fn [_] tx-cmds)))))


(defn- remove-resources [tx-cmds]
  (mapv #(dissoc % :resource) tx-cmds))


(defn- store-tx-data! [kv-store {:keys [t instant tx-cmds]}]
  (log/trace "store transaction data with t =" t)
  (kv/put! kv-store (codec/encode-key t)
           (codec/encode-tx-data instant (remove-resources tx-cmds))))


(defn- wrap-resource-in-future [{:keys [resource] :as tx-cmd}]
  (cond-> tx-cmd resource (update :resource ac/completed-future)))


(defn- wrap-resources-in-futures* [tx-cmds]
  (mapv wrap-resource-in-future tx-cmds))


(defn- wrap-resources-in-futures [tx-data]
  (update tx-data :tx-cmds wrap-resources-in-futures*))


(defn- transfer-tx-data! [queues tx-data]
  (log/trace "transfer transaction data to" (count queues) "queue(s)")
  (run! #(.put ^BlockingQueue % [(wrap-resources-in-futures tx-data)]) queues))


(defn- submit!
  "Stores `tx-cmds` and transfers them to pollers on queues.

  Uses `state` to increment the point in time `t`. Stores transaction data
  consisting of the new `t`, the current time taken from `clock` and `tx-cmds`.
  Has to be run in a single thread in order to deliver transaction data in
  order."
  [kv-store clock state tx-cmds]
  (let [{:keys [t queues]} (swap! state update :t inc)
        tx-data {:t t :instant (Instant/now clock) :tx-cmds tx-cmds}]
    (store-tx-data! kv-store tx-data)
    (transfer-tx-data! (vals queues) tx-data)
    t))


;; The state contains the following keys:
;;  * :t - the current point in time
;;  * :queues - a map of queues created through `new-queue`
(deftype LocalTxLog [kv-store resource-store clock ^Lock submit-lock state]
  tx-log/TxLog
  (-submit [_ tx-cmds]
    (log/trace "submit" (count tx-cmds) "tx-cmds")
    (with-open [_ (prom/timer duration-seconds "submit")]
      (-> (store-resources! resource-store tx-cmds)
          (ac/then-apply
            (fn [tx-cmds]
              (.lock submit-lock)
              (try
                (submit! kv-store clock state tx-cmds)
                (finally
                  (.unlock submit-lock))))))))

  (-last-t [_]
    (ac/completed-future (:t @state)))

  (-new-queue [_ offset]
    (let [key (Object.)
          queue (ArrayBlockingQueue. 10)
          queue-start (inc (:t (swap! state update :queues assoc key queue)))]
      (log/trace "new-queue offset =" offset ", queue-start =" queue-start)
      (->LocalQueue kv-store resource-store (volatile! offset) queue queue-start
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
  (s/keys :req-un [:blaze.db/kv-store :blaze.db/resource-store :blaze/clock]))


(defmethod ig/init-key :blaze.db.tx-log/local
  [_ {:keys [kv-store resource-store clock]}]
  (log/info "Open local transaction log")
  (->LocalTxLog kv-store resource-store clock (ReentrantLock.)
                (atom {:t (or (last-t kv-store) 0)})))


(derive :blaze.db.tx-log/local :blaze.db/tx-log)


(reg-collector ::duration-seconds
  duration-seconds)
