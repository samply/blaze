(ns blaze.db.tx-log.kafka-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.tx-log :as tx-log]
    [blaze.db.tx-log.kafka :as kafka]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [java-time :as jt]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [java.io Closeable]
    [java.time Instant]
    [org.apache.kafka.clients.consumer Consumer ConsumerRecord]
    [org.apache.kafka.clients.producer Producer RecordMetadata]
    [org.apache.kafka.common TopicPartition]
    [org.apache.kafka.common.errors
     AuthorizationException RecordTooLargeException]
    [org.apache.kafka.common.record TimestampType]))


(st/instrument)
(log/set-level! :trace)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def bootstrap-servers "bootstrap-servers-182741")
(def patient-0 {:fhir/type :fhir/Patient :id "0"})
(def patient-hash-0 (hash/generate patient-0))
(def tx-cmd {:op "create" :type "Patient" :id "0" :hash patient-hash-0})


(defn tx-log []
  (-> {:blaze.db.tx-log/kafka {:bootstrap-servers bootstrap-servers}}
      ig/init
      :blaze.db.tx-log/kafka))


(deftest tx-log-test
  (testing "submit"
    (with-redefs
      [kafka/create-producer
       (fn [{servers :bootstrap-servers}]
         (when-not (= bootstrap-servers servers)
           (throw (Error.)))
         (reify
           Producer
           (send [_ _ callback]
             (.onCompletion callback (RecordMetadata. nil 0 0 0 nil 0 0) nil))
           Closeable
           (close [_])))]
      (with-open [tx-log (tx-log)]
        (is (= 1 @(tx-log/submit tx-log [tx-cmd])))))
(int (/ (- 1522581 1048576) 1024))
    (testing "RecordTooLargeException"
      (with-redefs
        [kafka/create-producer
         (fn [{servers :bootstrap-servers}]
           (when-not (= bootstrap-servers servers)
             (throw (Error.)))
           (reify
             Producer
             (send [_ _ callback]
               (.onCompletion callback nil
                              (RecordTooLargeException. "msg-173357")))
             Closeable
             (close [_])))]
        (with-open [tx-log (tx-log)]
          (given @(-> (tx-log/submit tx-log [tx-cmd]) (ac/exceptionally ex-data))
            ::anom/category := ::anom/unsupported
            ::anom/message := "A transaction with 1 commands generated a Kafka message which is larger than the configured maximum of null bytes. In order to prevent this error, increase the maximum message size by setting DB_KAFKA_MAX_REQUEST_SIZE to a higher number. msg-173357"))))

    (* 3 1024 1024)

    (testing "AuthorizationException"
      (with-redefs
        [kafka/create-producer
         (fn [{servers :bootstrap-servers}]
           (when-not (= bootstrap-servers servers)
             (throw (Error.)))
           (reify
             Producer
             (send [_ _ callback]
               (.onCompletion callback nil
                              (AuthorizationException. "msg-175337")))
             Closeable
             (close [_])))]
        (with-open [tx-log (tx-log)]
          (given @(-> (tx-log/submit tx-log [tx-cmd]) (ac/exceptionally ex-data))
            ::anom/category := ::anom/fault
            ::anom/message := "msg-175337")))))

  (testing "an empty transaction log has no transaction data"
    (with-redefs
      [kafka/create-producer
       (fn [{servers :bootstrap-servers}]
         (when-not (= bootstrap-servers servers)
           (throw (Error.)))
         (reify
           Producer
           Closeable
           (close [_])))
       kafka/create-consumer
       (fn [{servers :bootstrap-servers}]
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
             (when-not (= (jt/millis 10) timeout)
               (throw (Error.))))
           Closeable
           (close [_])))]
      (with-open [tx-log (tx-log)
                  queue (tx-log/new-queue tx-log 1)]
        (is (empty? (tx-log/poll queue (jt/millis 10))))))))


(defn invalid-cbor-content
  "`0xA1` is the start of a map with one entry."
  []
  (byte-array [0xA1]))


(def hash-patient-0 (hash/generate {:fhir/type :fhir/Patient :id "0"}))


(defn- serialize [cmds]
  (.serialize kafka/serializer nil cmds))


(defn- deserialize [cmds]
  (.deserialize kafka/deserializer nil cmds))


(deftest serializer-test
  (let [cmd {:op "create" :type "Patient" :id "0" :hash hash-patient-0}]
    (is (= [cmd] (deserialize (serialize [cmd]))))))


(deftest deserializer-test
  (testing "empty value"
    (is (nil? (deserialize (byte-array 0)))))

  (testing "invalid cbor value"
    (is (nil? (deserialize (invalid-cbor-content)))))

  (testing "invalid map value"
    (is (nil? (deserialize (serialize {:a 1})))))

  (testing "success"
    (let [cmd {:op "create" :type "Patient" :id "0" :hash hash-patient-0}]
      (given (first (deserialize (serialize [cmd])))
        :op := "create"
        :type := "Patient"
        :id := "0"
        :hash := hash-patient-0))))


(defn consumer-record [offset timestamp timestamp-type value]
  (ConsumerRecord. "tx" 0 offset timestamp timestamp-type 0 0 0 nil value))


(deftest record-transformer-test
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
