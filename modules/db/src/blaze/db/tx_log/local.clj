(ns blaze.db.tx-log.local
  "A transaction log which is suitable only for standalone (single node) setups.

  Uses an exclusive key-value store to persist the transaction log using the
  default column family. The single key-value index is populated were keys are
  the point in time `t` of the transaction and the values are transaction
  commands and instants.

  Uses a single thread names `local-tx-log` to increment the point in time `t`,
  store the transaction and transfers it to listening queues."
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.iterators :as i]
    [blaze.db.kv :as kv]
    [blaze.db.tx-log :as tx-log]
    [blaze.db.tx-log.spec]
    [blaze.executors :as ex]
    [blaze.fhir.hash :as hash]
    [blaze.module :refer [reg-collector]]
    [cheshire.core :as cheshire]
    [cheshire.generate :refer [JSONable]]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [integrant.core :as ig]
    [prometheus.alpha :as prom :refer [defhistogram]]
    [taoensso.timbre :as log])
  (:import
    [java.io Closeable]
    [java.nio ByteBuffer]
    [java.time Clock Duration Instant]
    [java.util.concurrent
     ArrayBlockingQueue BlockingQueue ExecutorService TimeUnit]
    [com.fasterxml.jackson.core JsonGenerator]
    [com.google.common.hash HashCode$BytesHashCode]))


(set! *warn-on-reflection* true)


(defhistogram duration-seconds
  "Durations in transaction log."
  {:namespace "blaze"
   :subsystem "db"
   :name "tx_log_duration_seconds"}
  (mapcat #(list % (* 2.5 %) (* 5 %) (* 7.5 %)) (take 6 (iterate #(* 10 %) 0.00001)))
  "op")


(extend-protocol JSONable
  HashCode$BytesHashCode
  (to-json [hash-code jg]
    (.writeBinary ^JsonGenerator jg (.asBytes hash-code)))
  Instant
  (to-json [instant jg]
    (.writeNumber ^JsonGenerator jg (.toEpochMilli instant))))


(defn- parse-cbor [value t]
  (try
    (cheshire/parse-cbor value keyword)
    (catch Exception e
      (log/warn (format "Skip transaction with point in time of %d because their was an error while parsing tx-data: %s" t (ex-message e))))))


(defn- decode-hash [tx-cmd]
  (update tx-cmd :hash hash/decode))


(defn- decode-instant [x]
  (when (int? x)
    (Instant/ofEpochMilli x)))


(defn- decode-tx-data
  ([]
   [(ByteBuffer/allocateDirect codec/t-size)
    ;; TODO: transaction cmds can get arbitrarily big
    (ByteBuffer/allocateDirect (* 1024 1024))])
  ([^ByteBuffer kb ^ByteBuffer vb]
   (let [t (.getLong kb)
         value (byte-array (.remaining vb))]
     (.get vb value)
     (-> (parse-cbor value t)
         (update :tx-cmds #(mapv decode-hash %))
         (update :instant decode-instant)
         (assoc :t t)))))


(defn encode-t [t]
  (-> (ByteBuffer/allocate Long/BYTES)
      (.putLong t)
      (.array)))


(defn encode-tx-data [instant tx-cmds]
  (cheshire/generate-cbor
    {:instant instant
     :tx-cmds tx-cmds}))


(def ^:private ^:const max-poll-size 50)


(defn- invalid-tx-data-msg [t cause]
  (format "Skip transaction with point in time of %d because tx-data is invalid: %s"
          t (str/replace cause #"\s" " ")))


(def ^:private remove-invalid-tx-data
  (filter
    (fn [{:keys [t] :as tx-data}]
      (if (s/valid? :blaze.db/tx-data tx-data)
        true
        (log/warn (invalid-tx-data-msg t (s/explain-str :blaze.db/tx-data tx-data)))))))


(defn- tx-data [kv-store offset]
  (log/trace "fetch tx-data from storage offset =" offset)
  (with-open [snapshot (kv/new-snapshot kv-store)
              iter (kv/new-iterator snapshot)]
    (into []
          (comp (take max-poll-size)
                remove-invalid-tx-data)
          (i/kvs iter decode-tx-data (encode-t offset)))))


(defn- poll [^BlockingQueue queue ^Duration timeout]
  (log/trace "poll in-memory queue with timeout =" timeout)
  (.poll queue (.toMillis timeout) TimeUnit/MILLISECONDS))


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


(defn- store-tx-data [kv-store {:keys [t instant tx-cmds]}]
  (log/trace "store transaction data with t =" t)
  (kv/put kv-store (encode-t t) (encode-tx-data instant tx-cmds)))


(defn- transfer-tx-data [queues tx-data]
  (log/trace "transfer transaction data to" (count queues) "queue(s)")
  (doseq [^BlockingQueue queue queues]
    (.put queue tx-data)))


(defn- submit
  "Stores `tx-cmds` and transfers them to pollers on queues.

  Uses `state` to increment the point in time `t`. Stores transaction data
  consisting of the new `t`, the current time and `tx-cmds`. Has to be run in a
  single thread in order to deliver transaction data in order."
  [kv-store clock state tx-cmds]
  (log/trace "submit" (count tx-cmds) "tx-cmds")
  (let [{:keys [t queues]} (swap! state update :t inc)
        instant (Instant/now clock)
        tx-data {:t t :instant instant :tx-cmds tx-cmds}]
    (store-tx-data kv-store tx-data)
    (transfer-tx-data (vals queues) [tx-data])
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
         (submit kv-store clock state tx-cmds))
      executor))

  (-new-queue [_ offset]
    (let [key (Object.)
          queue (ArrayBlockingQueue. 10)
          queue-start (inc (:t (swap! state update :queues assoc key queue)))]
      (log/trace "new-queue offset =" offset ", queue-start =" queue-start)
      (->LocalQueue kv-store (volatile! offset) queue queue-start
                    #(swap! state update :queues dissoc key)))))


(defn- last-t
  "Returns the last (newest) point in time, the transaction log has persisted
  in `kv-store` ot nil if the log is empty."
  [kv-store]
  (with-open [snapshot (kv/new-snapshot kv-store)
              iter (kv/new-iterator snapshot)]
    (kv/seek-to-last! iter)
    (let [kb (ByteBuffer/allocateDirect Long/BYTES)]
      (when (kv/valid? iter)
        (kv/key iter kb)
        (when (<= Long/BYTES (.remaining kb))
          (.getLong kb))))))


(defn new-local-tx-log
  "Returns a transaction log which is suitable only for single node setups."
  [kv-store clock executor]
  (->LocalTxLog kv-store clock executor (atom {:t (or (last-t kv-store) 0)})))


(defmethod ig/pre-init-spec :blaze.db.tx-log/local [_]
  (s/keys :req-un [:blaze.db/kv-store]))


(defmethod ig/init-key :blaze.db.tx-log/local
  [_ {:keys [kv-store]}]
  (log/info "Open local transaction log")
  (new-local-tx-log
    kv-store
    (Clock/systemDefaultZone)
    ;; it's important to have a single thread executor here. See docs of submit
    ;; function.
    (ex/single-thread-executor "local-tx-log")))


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
