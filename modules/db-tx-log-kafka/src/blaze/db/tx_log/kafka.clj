(ns blaze.db.tx-log.kafka
  (:require
    [blaze.async.comp :as ac]
    [blaze.byte-string :as bs]
    [blaze.db.tx-log :as tx-log]
    [blaze.db.tx-log.kafka.spec]
    [blaze.db.tx-log.spec]
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
    [java.time Duration Instant]
    [java.util Map]
    [com.fasterxml.jackson.core JsonGenerator]
    [com.google.common.hash HashCode$BytesHashCode]
    [org.apache.kafka.clients.consumer Consumer KafkaConsumer ConsumerRecord]
    [org.apache.kafka.clients.producer Producer KafkaProducer ProducerRecord RecordMetadata Callback]
    [org.apache.kafka.common TopicPartition]
    [org.apache.kafka.common.serialization Serializer Deserializer]
    [org.apache.kafka.common.record TimestampType]))


(set! *warn-on-reflection* true)


(defhistogram duration-seconds
  "Durations in Kafka transaction log."
  {:namespace "blaze"
   :subsystem "db_tx_log"
   :name "duration_seconds"}
  (take 12 (iterate #(* 2 %) 0.0001))
  "op")


(extend-protocol JSONable
  HashCode$BytesHashCode
  (to-json [hash-code jg]
    (.writeBinary ^JsonGenerator jg (.asBytes hash-code))))


(deftype CborSerializer []
  Serializer
  (serialize [_ _ data]
    (cheshire/generate-cbor data)))


(def ^Serializer serializer (CborSerializer.))


(defn- default-producer-config [max-request-size]
  {"enable.idempotence" "true"
   "acks" "all"
   "compression.type" "snappy"
   "delivery.timeout.ms" "60000"
   "max.request.size" (str max-request-size)})


(defn- producer-config ^Map [bootstrap-servers max-request-size]
  (assoc (default-producer-config max-request-size) "bootstrap.servers" bootstrap-servers))


(defn create-producer [bootstrap-servers max-request-size]
  (KafkaProducer. (producer-config bootstrap-servers max-request-size) serializer serializer))


(defn- parse-cbor [data]
  (try
    (cheshire/parse-cbor data keyword)
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


(def ^:private default-consumer-config
  {"enable.auto.commit" "false"
   "isolation.level" "read_committed"
   "auto.offset.reset" "earliest"})


(defn- consumer-config ^Map [bootstrap-servers]
  (assoc default-consumer-config "bootstrap.servers" bootstrap-servers))


(def ^:private ^TopicPartition tx-partition
  (TopicPartition. "tx" 0))


(defn create-consumer [bootstrap-servers]
  (doto (KafkaConsumer. (consumer-config bootstrap-servers)
                        deserializer deserializer)
    (.assign [tx-partition])))


(defn- metadata->t
  "Returns the point in time `t` of the transaction data send by the producer.

  The `t` is calculated from the offset by incrementing it because Kafka
  offsets start at zero were `t`s start at one."
  [metadata]
  (inc (.offset ^RecordMetadata metadata)))


(deftype KafkaTxLog [bootstrap-servers ^Producer producer]
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
                   (ac/complete-exceptionally! future e)
                   (ac/complete! future (metadata->t metadata))))))
      future))

  (-new-queue [_ offset]
    (log/trace "new-queue offset =" offset)
    (let [consumer (create-consumer bootstrap-servers)]
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


(defn new-kafka-tx-log
  "Creates a new local database node."
  [bootstrap-servers max-request-size]
  (->KafkaTxLog bootstrap-servers (create-producer bootstrap-servers max-request-size)))


(defmethod ig/pre-init-spec :blaze.db.tx-log/kafka [_]
  (s/keys :req-un [:blaze.db.tx-log.kafka/bootstrap-servers]
          :opt-un [::max-request-size]))


(defmethod ig/init-key :blaze.db.tx-log/kafka
  [_ {:keys [bootstrap-servers max-request-size]
      :or {max-request-size 1048576}}]
  (log/info "Open Kafka transaction log with bootstrap servers:" bootstrap-servers)
  (new-kafka-tx-log bootstrap-servers max-request-size))


(defmethod ig/halt-key! :blaze.db.tx-log/kafka
  [_ tx-log]
  (log/info "Close Kafka transaction log")
  (.close ^Closeable tx-log))


(derive :blaze.db.tx-log/kafka :blaze.db/tx-log)


(reg-collector ::duration-seconds
  duration-seconds)
