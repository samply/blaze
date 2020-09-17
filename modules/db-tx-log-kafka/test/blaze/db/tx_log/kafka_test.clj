(ns blaze.db.tx-log.kafka-test
  (:require
    [blaze.db.tx-log :as tx-log]
    [blaze.db.tx-log.kafka :as kafka :refer [new-kafka-tx-log]]
    [blaze.fhir.hash :as hash]
    [cheshire.core :as cheshire]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]])
  (:import
    [java.io Closeable]
    [java.time Duration Instant]
    [org.apache.kafka.clients.consumer Consumer ConsumerRecord]
    [org.apache.kafka.clients.producer Producer RecordMetadata]
    [org.apache.kafka.common TopicPartition]
    [org.apache.kafka.common.record TimestampType]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def bootstrap-servers "bootstrap-servers-182741")
(def patient-0 {:fhir/type :fhir/Patient :id "0"})
(def patient-hash-0 (hash/generate patient-0))
(def tx-cmd {:op "create" :type "Patient" :id "0" :hash patient-hash-0})


(deftest tx-log
  (testing "submit"
    (with-redefs
      [kafka/create-producer
       (fn [servers _]
         (when-not (= bootstrap-servers servers)
           (throw (Error.)))
         (reify
           Producer
           (send [_ _ callback]
             (.onCompletion callback (RecordMetadata. nil 0 0 0 nil 0 0) nil))
           Closeable
           (close [_])))]
      (with-open [tx-log (new-kafka-tx-log bootstrap-servers 1048576)]
        (is (= 1 @(tx-log/submit tx-log [tx-cmd]))))))

  (testing "an empty transaction log has no transaction data"
    (with-redefs
      [kafka/create-producer
       (fn [servers _]
         (when-not (= bootstrap-servers servers)
           (throw (Error.)))
         (reify
           Producer
           Closeable
           (close [_])))
       kafka/create-consumer
       (fn [servers]
         (when-not (= bootstrap-servers servers)
           (throw (Error.)))
         (reify
           Consumer
           (^void seek [_ ^TopicPartition partition ^long offset]
             (when-not (= (TopicPartition. "tx" 0) partition)
               (throw (Error.)))
             (when-not (= 0 offset)
               (throw (Error.))))
           tx-log/Queue
           (-poll [_ timeout]
             (when-not (= (Duration/ofMillis 10) timeout)
               (throw (Error.))))
           Closeable
           (close [_])))]
      (with-open [tx-log (new-kafka-tx-log bootstrap-servers 1048576)
                  queue (tx-log/new-queue tx-log 1)]
        (is (empty? (tx-log/poll queue (Duration/ofMillis 10))))))))


(defn invalid-cbor-content
  "`0xA1` is the start of a map with one entry."
  []
  (byte-array [0xA1]))


(def hash-patient-0 (hash/generate {:fhir/type :fhir/Patient :id "0"}))


(deftest deserializer
  (testing "empty value"
    (is (nil? (.deserialize kafka/deserializer nil (byte-array 0)))))

  (testing "invalid cbor value"
    (is (nil? (.deserialize kafka/deserializer nil (invalid-cbor-content)))))

  (testing "invalid map value"
    (is (nil? (.deserialize kafka/deserializer nil (cheshire/generate-cbor {:a 1})))))

  (testing "success"
    (let [cmd {:op "create" :type "Patient" :id "0" :hash (hash/encode hash-patient-0)}]
      (given (first (.deserialize kafka/deserializer nil (cheshire/generate-cbor [cmd])))
        :op := "create"
        :type := "Patient"
        :id := "0"
        :hash := hash-patient-0))))


(defn consumer-record [offset timestamp timestamp-type value]
  (ConsumerRecord. "tx" 0 offset timestamp timestamp-type 0 0 0 nil value))


(deftest record-transformer
  (testing "skips record with wrong timestamp type"
    (let [cmd {:op "create" :type "Patient" :id "0" :hash hash-patient-0}]
      (is (empty? (into [] kafka/record-transformer [(consumer-record 0 0 TimestampType/CREATE_TIME [cmd])])))))

  (testing "skips record with invalid transaction commands"
    (is (empty? (into [] kafka/record-transformer [(consumer-record 0 0 TimestampType/LOG_APPEND_TIME [{:op "create"}])]))))

  (testing "success"
    (let [cmd {:op "create" :type "Patient" :id "0" :hash hash-patient-0}]
      (given (first (into [] kafka/record-transformer [(consumer-record 0 0 TimestampType/LOG_APPEND_TIME [cmd])]))
        :t := 1
        :instant := (Instant/ofEpochSecond 0)
        :tx-cmds := [cmd]))))
