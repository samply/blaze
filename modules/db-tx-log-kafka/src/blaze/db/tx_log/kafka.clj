(ns blaze.db.tx-log.kafka
  (:require
    [blaze.anomaly :refer [ex-anom]]
    [blaze.async.comp :as ac]
    [blaze.byte-string :as bs]
    [blaze.db.tx-log :as tx-log]
    [blaze.db.tx-log.kafka.config :as c]
    [blaze.db.tx-log.kafka.log :as l]
    [blaze.db.tx-log.kafka.spec]
    [blaze.db.tx-log.spec]
    [blaze.module :refer [reg-collector]]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [jsonista.core :as j]
    [prometheus.alpha :as prom :refer [defhistogram]]
    [taoensso.timbre :as log])
  (:import
    [java.io Closeable]
    [java.time Duration Instant]
    [java.util Map]
    [com.fasterxml.jackson.dataformat.cbor CBORFactory]
    [org.apache.kafka.clients.consumer Consumer KafkaConsumer ConsumerRecord]
    [org.apache.kafka.clients.producer Producer KafkaProducer ProducerRecord RecordMetadata Callback]
    [org.apache.kafka.common TopicPartition]
    [org.apache.kafka.common.errors RecordTooLargeException]
    [org.apache.kafka.common.record TimestampType]
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


(deftype KafkaTxLog [config ^Producer producer]
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

  (-new-queue [_ offset]
    (log/trace "new-queue offset =" offset)
    (let [consumer (create-consumer config)]
      (.seek ^Consumer consumer tx-partition ^long (dec offset))
      consumer))

  Closeable
  (close [_]
    (.close producer)))


(defn- record->t
  "Returns the point in time `t` of the transaction data received by a consumer.

  The `t` is calculated from the offset by incrementing it because Kafka
  offsets start at zero were `t`s start at one."
  [record]
  (inc (.offset ^ConsumerRecord record)))


(defn invalid-timestamp-type-msg [t timestamp-type]
  (format "Skip transaction with point in time of %d because the timestamp type is `%s` instead of `LogAppendTime`."
          t timestamp-type))


(defn- invalid-tx-data-msg [t cause]
  (format "Skip transaction with point in time of %d because tx-data is invalid: %s"
          t (str/replace cause #"\s" " ")))


(def record-transformer
  (mapcat
    (fn [^ConsumerRecord record]
      (let [t (record->t record)]
        (if (= TimestampType/LOG_APPEND_TIME (.timestampType record))
          (let [tx-cmds (.value record)]
            (if (s/valid? :blaze.db/tx-cmds tx-cmds)
              [{:t t
                :instant (Instant/ofEpochMilli (.timestamp record))
                :tx-cmds tx-cmds}]
              (log/warn (invalid-tx-data-msg t (s/explain-str :blaze.db/tx-cmds tx-cmds)))))
          (log/warn (invalid-timestamp-type-msg t (.timestampType record))))))))


(extend-protocol tx-log/Queue
  KafkaConsumer
  (-poll [consumer timeout]
    (into [] record-transformer (.poll consumer ^Duration timeout))))


(defmethod ig/pre-init-spec :blaze.db.tx-log/kafka [_]
  (s/keys :req-un [::bootstrap-servers]
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
  (->KafkaTxLog config (create-producer config)))


(defmethod ig/halt-key! :blaze.db.tx-log/kafka
  [_ tx-log]
  (log/info "Close Kafka transaction log")
  (.close ^Closeable tx-log))


(derive :blaze.db.tx-log/kafka :blaze.db/tx-log)


(reg-collector ::duration-seconds
  duration-seconds)
