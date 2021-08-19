(ns blaze.db.tx-log.kafka
  (:require
    [blaze.anomaly :refer [ex-anom]]
    [blaze.async.comp :as ac]
    [blaze.byte-string :as bs]
    [blaze.db.tx-log :as tx-log]
    [blaze.db.tx-log.kafka.config :as c]
    [blaze.db.tx-log.kafka.log :as l]
    [blaze.db.tx-log.kafka.spec]
    [blaze.db.tx-log.kafka.util :as u]
    [blaze.db.tx-log.spec]
    [blaze.executors :as ex]
    [blaze.module :refer [reg-collector]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [jsonista.core :as j]
    [prometheus.alpha :as prom :refer [defhistogram]]
    [taoensso.timbre :as log])
  (:import
    [java.io Closeable]
    [java.time Duration]
    [java.util Map]
    [java.util.concurrent TimeUnit ExecutorService]
    [com.fasterxml.jackson.dataformat.cbor CBORFactory]
    [org.apache.kafka.clients.consumer Consumer KafkaConsumer]
    [org.apache.kafka.clients.producer Producer KafkaProducer ProducerRecord RecordMetadata Callback]
    [org.apache.kafka.common TopicPartition]
    [org.apache.kafka.common.errors RecordTooLargeException]
    [org.apache.kafka.common.serialization Serializer Deserializer]))


(set! *warn-on-reflection* true)


(defhistogram duration-seconds
  "Durations in Kafka transaction log."
  {:namespace "blaze"
   :subsystem "db_tx_log"
   :name "duration_seconds"}
  (take 12 (iterate #(* 2 %) 0.0001))
  "op")


(def ^:private cbor-object-mapper
  (j/object-mapper
    {:factory (CBORFactory.)
     :decode-key-fn true
     :modules [bs/object-mapper-module]}))


(deftype CborSerializer []
  Serializer
  (serialize [_ _ data]
    (j/write-value-as-bytes data cbor-object-mapper)))


(def ^Serializer serializer (CborSerializer.))


(defn create-producer [config]
  (KafkaProducer. ^Map (c/producer-config config) serializer serializer))


(defn- parse-cbor [data]
  (try
    (j/read-value data cbor-object-mapper)
    (catch Exception e
      (log/warn (format "Error while parsing tx-data: %s" (ex-message e))))))


(defn decode-hashes [cmds]
  (when (sequential? cmds)
    (mapv #(update % :hash bs/from-byte-array) cmds)))


(deftype CborDeserializer []
  Deserializer
  (deserialize [_ _ data]
    (decode-hashes (parse-cbor data))))


(def ^Deserializer deserializer (CborDeserializer.))


(def ^:private ^TopicPartition tx-partition
  (TopicPartition. "tx" 0))


(defn create-consumer [config]
  (doto (KafkaConsumer. ^Map (c/consumer-config config)
                        deserializer deserializer)
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
    (ex-anom
      #::anom{:category ::anom/unsupported
              :message (record-too-large-msg max-request-size num-of-tx-cmds
                                             (ex-message e))})
    (ex-anom #::anom{:category ::anom/fault :message (ex-message e)})))


(defn- end-offset [^Consumer consumer]
  (with-open [_ (prom/timer duration-seconds "end-offset")]
    (get (.endOffsets consumer [tx-partition]) tx-partition)))


(deftype KafkaTxLog [config ^Producer producer last-t-consumer last-t-executor]
  tx-log/TxLog
  (-submit [_ tx-cmds]
    (log/trace "submit" (count tx-cmds) "tx-cmds")
    (let [timer (prom/timer duration-seconds "submit")
          future (ac/future)]
      (.send producer (ProducerRecord. "tx" tx-cmds)
             (reify Callback
               (onCompletion [_ metadata e]
                 (prom/observe-duration! timer)
                 (if e
                   (ac/complete-exceptionally! future (producer-error e config (count tx-cmds)))
                   (ac/complete! future (metadata->t metadata))))))
      future))

  (-last-t [_]
    (ac/supply-async #(end-offset last-t-consumer) last-t-executor))

  (-new-queue [_ offset]
    (log/trace "new-queue offset =" offset)
    (let [consumer (create-consumer config)]
      (.seek ^Consumer consumer tx-partition ^long (dec offset))
      consumer))

  Closeable
  (close [_]
    (.close producer)
    (.close ^Closeable last-t-consumer)))


(extend-protocol tx-log/Queue
  Consumer
  (-poll [consumer timeout]
    (into [] u/record-transformer (.poll consumer ^Duration timeout))))


(defn create-last-t-consumer [config]
  (doto (KafkaConsumer. ^Map (c/consumer-config config)
                        deserializer deserializer)
    (.assign [tx-partition])))


(defmethod ig/pre-init-spec :blaze.db.tx-log/kafka [_]
  (s/keys :req-un [::bootstrap-servers
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
  [_ config]
  (log/info (l/init-msg config))
  (->KafkaTxLog config (create-producer config) (create-last-t-consumer config)
                (:last-t-executor config)))


(defmethod ig/halt-key! :blaze.db.tx-log/kafka
  [_ tx-log]
  (log/info "Closing Kafka transaction log...")
  (.close ^Closeable tx-log)
  (log/info "Kafka transaction log was closed successfully"))


(derive :blaze.db.tx-log/kafka :blaze.db/tx-log)


(defmethod ig/init-key ::last-t-executor
  [_ _]
  (log/info "Init last-t executor")
  (ex/single-thread-executor "db-tx-log-kafka-last-t"))


(defmethod ig/halt-key! ::last-t-executor
  [_ ^ExecutorService executor]
  (log/info "Stopping last-t executor...")
  (.shutdown executor)
  (if (.awaitTermination executor 10 TimeUnit/SECONDS)
    (log/info "Last-t executor was stopped successfully")
    (log/warn "Got timeout while stopping the last-t executor")))


(reg-collector ::duration-seconds
  duration-seconds)
