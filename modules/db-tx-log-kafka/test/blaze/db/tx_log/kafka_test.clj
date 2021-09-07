(ns blaze.db.tx-log.kafka-test
  (:require
    [blaze.anomaly-spec]
    [blaze.db.tx-log :as tx-log]
    [blaze.db.tx-log.kafka :as kafka]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.test-util :refer [given-failed-future given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [java-time :as time]
    [taoensso.timbre :as log])
  (:import
    [java.io Closeable]
    [java.time Duration]
    [org.apache.kafka.clients.consumer Consumer ConsumerRecords]
    [org.apache.kafka.clients.producer KafkaProducer Producer RecordMetadata]
    [org.apache.kafka.common TopicPartition]
    [org.apache.kafka.common.errors
     AuthorizationException RecordTooLargeException]
    [java.util Map]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def bootstrap-servers "bootstrap-servers-182741")
(def patient-0 {:fhir/type :fhir/Patient :id "0"})
(def patient-hash-0 (hash/generate patient-0))
(def tx-cmd {:op "create" :type "Patient" :id "0" :hash patient-hash-0})


(def system
  {::tx-log/kafka
   {:bootstrap-servers bootstrap-servers
    :last-t-executor (ig/ref ::kafka/last-t-executor)}
   ::kafka/last-t-executor {}})


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {::tx-log/kafka nil})
      :key := ::tx-log/kafka
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::tx-log/kafka {}})
      :key := ::tx-log/kafka
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :bootstrap-servers))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :last-t-executor))))

  (testing "invalid bootstrap servers"
    (given-thrown (ig/init {::tx-log/kafka {:bootstrap-servers ::invalid}})
      :key := ::tx-log/kafka
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :last-t-executor))
      [:explain ::s/problems 1 :pred] := `string?
      [:explain ::s/problems 1 :val] := ::invalid)))


(defn- no-op-producer [{servers :bootstrap-servers}]
  (assert (= bootstrap-servers servers))
  (reify
    Producer
    Closeable
    (close [_])))


(defn- no-op-consumer [{servers :bootstrap-servers}]
  (assert (= bootstrap-servers servers))
  (reify
    Consumer
    Closeable
    (close [_])))


(deftest tx-log-test
  (testing "submit"
    (with-redefs
      [kafka/create-producer
       (fn [{servers :bootstrap-servers}]
         (assert (= bootstrap-servers servers))
         (reify
           Producer
           (send [_ _ callback]
             (.onCompletion callback (RecordMetadata. nil 0 0 0 nil 0 0) nil))
           Closeable
           (close [_])))
       kafka/create-last-t-consumer no-op-consumer]
      (with-system [{tx-log ::tx-log/kafka} system]
        (is (= 1 @(tx-log/submit tx-log [tx-cmd])))))

    (testing "RecordTooLargeException"
      (with-redefs
        [kafka/create-producer
         (fn [{servers :bootstrap-servers}]
           (assert (= bootstrap-servers servers))
           (reify
             Producer
             (send [_ _ callback]
               (.onCompletion callback nil
                              (RecordTooLargeException. "msg-173357")))
             Closeable
             (close [_])))
         kafka/create-last-t-consumer no-op-consumer]
        (with-system [{tx-log ::tx-log/kafka} system]
          (given-failed-future (tx-log/submit tx-log [tx-cmd])
            ::anom/category := ::anom/unsupported
            ::anom/message := "A transaction with 1 commands generated a Kafka message which is larger than the configured maximum of null bytes. In order to prevent this error, increase the maximum message size by setting DB_KAFKA_MAX_REQUEST_SIZE to a higher number. msg-173357"))))

    (testing "AuthorizationException"
      (with-redefs
        [kafka/create-producer
         (fn [{servers :bootstrap-servers}]
           (assert (= bootstrap-servers servers))
           (reify
             Producer
             (send [_ _ callback]
               (.onCompletion callback nil
                              (AuthorizationException. "msg-175337")))
             Closeable
             (close [_])))
         kafka/create-last-t-consumer no-op-consumer]
        (with-system [{tx-log ::tx-log/kafka} system]
          (given-failed-future @(tx-log/submit tx-log [tx-cmd])
            ::anom/category := ::anom/fault
            ::anom/message := "msg-175337")))))

  (testing "an empty transaction log has no transaction data"
    (with-redefs
      [kafka/create-producer no-op-producer
       kafka/create-consumer
       (fn [{servers :bootstrap-servers}]
         (assert (= bootstrap-servers servers))
         (reify
           Consumer
           (^void seek [_ ^TopicPartition partition ^long offset]
             (assert (= (TopicPartition. "tx" 0) partition))
             (assert (= 0 offset)))
           (^ConsumerRecords poll [_ ^Duration duration]
             (assert (= (time/seconds 1) duration))
             (ConsumerRecords. (Map/of)))
           Closeable
           (close [_])))
       kafka/create-last-t-consumer no-op-consumer]
      (with-system [{tx-log ::tx-log/kafka} system]
        (with-open [queue (tx-log/new-queue tx-log 1)]
          (is (empty? (tx-log/poll! queue (time/seconds 1))))))))

  (testing "last-t"
    (with-redefs
      [kafka/create-producer no-op-producer
       kafka/create-last-t-consumer
       (fn [{servers :bootstrap-servers}]
         (assert (= bootstrap-servers servers))
         (reify
           Consumer
           (endOffsets [_ partitions]
             (assert (= (TopicPartition. "tx" 0) (first partitions)))
             (Map/of (first partitions) 104614))
           Closeable
           (close [_])))]
      (with-system [{tx-log ::tx-log/kafka} system]
        (is (= 104614 @(tx-log/last-t tx-log)))))))


(def config {:bootstrap-servers "localhost:9092"})


(deftest create-producer-test
  (is (instance? KafkaProducer (kafka/create-producer config))))


(deftest create-consumer-test
  (is (= "tx" (.topic (first (.assignment (kafka/create-consumer config)))))))
