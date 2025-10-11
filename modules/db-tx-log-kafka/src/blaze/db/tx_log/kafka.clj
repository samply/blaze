(ns blaze.db.tx-log.kafka
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.tx-log :as tx-log]
   [blaze.db.tx-log.kafka.codec :as codec]
   [blaze.db.tx-log.kafka.config :as c]
   [blaze.db.tx-log.kafka.log :as l]
   [blaze.db.tx-log.kafka.spec]
   [blaze.db.tx-log.kafka.util :as u]
   [blaze.executors :as ex]
   [blaze.module :as m :refer [reg-collector]]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [prometheus.alpha :as prom :refer [defhistogram]]
   [taoensso.timbre :as log])
  (:import
   [java.lang AutoCloseable]
   [java.time Duration]
   [java.util Map]
   [java.util.concurrent TimeUnit]
   [org.apache.kafka.clients.consumer Consumer KafkaConsumer]
   [org.apache.kafka.clients.producer Callback KafkaProducer Producer ProducerRecord RecordMetadata]
   [org.apache.kafka.common TopicPartition]
   [org.apache.kafka.common.errors RecordTooLargeException]))

(set! *warn-on-reflection* true)

(defhistogram duration-seconds
  "Durations in Kafka transaction log."
  {:namespace "blaze"
   :subsystem "db_tx_log"}
  (take 12 (iterate #(* 2 %) 0.0001))
  "op")

(defn create-producer [config]
  (KafkaProducer. ^Map (c/producer-config config)
                  codec/serializer codec/serializer))

(defn create-consumer ^Consumer [tx-partition config]
  (doto (KafkaConsumer. ^Map (c/consumer-config config)
                        codec/deserializer codec/deserializer)
    (.assign [tx-partition])))

(defn- metadata->t
  "Returns the point in time `t` of the transaction data send by the producer.

  The `t` is calculated from the offset by incrementing it because Kafka
  offsets start at zero were `t`s start at one."
  [metadata]
  (inc (.offset ^RecordMetadata metadata)))

(defn record-too-large-msg [max-request-size num-of-tx-cmds msg]
  (format "A transaction with %d commands generated a Kafka message which is larger than the configured maximum of %d bytes. In order to prevent this error, increase the maximum message size by setting DB_KAFKA_MAX_REQUEST_SIZE to a higher number. %s"
          num-of-tx-cmds max-request-size msg))

(defn- producer-error
  [e {:keys [max-request-size]} num-of-tx-cmds]
  (condp identical? (class e)
    RecordTooLargeException
    (ba/ex-anom
     (ba/unsupported
      (record-too-large-msg max-request-size num-of-tx-cmds
                            (ex-message e))))
    (ba/ex-anom (ba/fault (ex-message e)))))

(defn- end-offset [^Consumer consumer tx-partition]
  (with-open [_ (prom/timer duration-seconds "end-offset")]
    (get (.endOffsets consumer [tx-partition]) tx-partition)))

(deftype KafkaTxLog [config ^TopicPartition partition ^Producer producer
                     last-t-consumer last-t-executor]
  tx-log/TxLog
  (-submit [_ tx-cmds _]
    (log/trace "submit" (count tx-cmds) "tx-cmds")
    (let [timer (prom/timer duration-seconds "submit")
          future (ac/future)]
      (.send producer (ProducerRecord. (:topic config) tx-cmds)
             (reify Callback
               (onCompletion [_ metadata e]
                 (prom/observe-duration! timer)
                 (if e
                   (ac/complete-exceptionally! future (producer-error e config (count tx-cmds)))
                   (ac/complete! future (metadata->t metadata))))))
      future))

  (-last-t [_]
    (ac/supply-async #(end-offset last-t-consumer partition) last-t-executor))

  (-new-queue [_ offset]
    (log/trace "new-queue offset =" offset)
    (doto (create-consumer partition config)
      (.seek partition ^long (dec offset))))

  AutoCloseable
  (close [_]
    (.close producer)
    (.close ^AutoCloseable last-t-consumer)))

(extend-protocol tx-log/Queue
  Consumer
  (-poll [consumer timeout]
    (into [] u/record-transformer (.poll consumer ^Duration timeout))))

(def create-last-t-consumer
  create-consumer)

(defmethod m/pre-init-spec :blaze.db.tx-log/kafka [_]
  (s/keys :req-un [::bootstrap-servers
                   ::topic
                   ::last-t-executor]
          :opt-un [::max-request-size
                   ::compression-type
                   ::security-protocol
                   ::truststore-location
                   ::truststore-password
                   ::keystore-location
                   ::keystore-password
                   ::key-password]))

(defmethod ig/init-key :blaze.db.tx-log/kafka
  [key {:keys [topic] :as config}]
  (log/info (l/init-msg key config))
  (let [partition (TopicPartition. topic 0)]
    (->KafkaTxLog config partition (create-producer config)
                  (create-last-t-consumer partition config)
                  (:last-t-executor config))))

(defmethod ig/halt-key! :blaze.db.tx-log/kafka
  [_ tx-log]
  (log/info "Closing Kafka transaction log...")
  (.close ^AutoCloseable tx-log)
  (log/info "Kafka transaction log was closed successfully"))

(defmethod ig/init-key ::last-t-executor
  [_ _]
  (log/info "Init last-t executor")
  (ex/single-thread-executor "db-tx-log-kafka-last-t"))

(defmethod ig/halt-key! ::last-t-executor
  [_ executor]
  (log/info "Stopping last-t executor...")
  (ex/shutdown! executor)
  (if (ex/await-termination executor 10 TimeUnit/SECONDS)
    (log/info "Last-t executor was stopped successfully")
    (log/warn "Got timeout while stopping the last-t executor")))

(reg-collector ::duration-seconds
  duration-seconds)
