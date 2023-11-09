(ns blaze.db.tx-log.kafka-test
  (:require
    [blaze.anomaly-spec]
    [blaze.db.tx-log :as tx-log]
    [blaze.db.tx-log.kafka :as kafka]
    [blaze.executors :as ex]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.test-util :refer [given-failed-future]]
    [blaze.metrics.spec]
    [blaze.module.test-util :refer [with-system]]
    [blaze.test-util :as tu :refer [given-thrown]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [java-time.api :as time]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [java.lang AutoCloseable]
    [java.time Duration]
    [java.util Map]
    [org.apache.kafka.clients.consumer Consumer ConsumerRecords]
    [org.apache.kafka.clients.producer KafkaProducer Producer RecordMetadata]
    [org.apache.kafka.common TopicPartition]
    [org.apache.kafka.common.errors
     AuthorizationException RecordTooLargeException]))


(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(def bootstrap-servers "bootstrap-servers-182741")
(def patient-0 {:fhir/type :fhir/Patient :id "0"})
(def patient-hash-0 (hash/generate patient-0))
(def tx-cmd {:op "create" :type "Patient" :id "0" :hash patient-hash-0})


(def config
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


(deftest duration-seconds-collector-init-test
  (with-system [{collector ::kafka/duration-seconds} {::kafka/duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))


(defn- no-op-producer [{servers :bootstrap-servers}]
  (assert (= bootstrap-servers servers))
  (reify
    Producer
    AutoCloseable
    (close [_])))


(defn- no-op-consumer [{servers :bootstrap-servers}]
  (assert (= bootstrap-servers servers))
  (reify
    Consumer
    AutoCloseable
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
             (.onCompletion callback (RecordMetadata. nil 0 0 0 0 0) nil))
           AutoCloseable
           (close [_])))
       kafka/create-last-t-consumer no-op-consumer]
      (with-system [{tx-log ::tx-log/kafka} config]
        (is (= 1 @(tx-log/submit tx-log [tx-cmd] nil)))))

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
             AutoCloseable
             (close [_])))
         kafka/create-last-t-consumer no-op-consumer]
        (with-system [{tx-log ::tx-log/kafka} config]
          (given-failed-future (tx-log/submit tx-log [tx-cmd] nil)
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
             AutoCloseable
             (close [_])))
         kafka/create-last-t-consumer no-op-consumer]
        (with-system [{tx-log ::tx-log/kafka} config]
          (given-failed-future @(tx-log/submit tx-log [tx-cmd] nil)
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
             (assert (zero? offset)))
           (^ConsumerRecords poll [_ ^Duration duration]
             (assert (= (time/seconds 1) duration))
             (ConsumerRecords. (Map/of)))
           AutoCloseable
           (close [_])))
       kafka/create-last-t-consumer no-op-consumer]
      (with-system [{tx-log ::tx-log/kafka} config]
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
           AutoCloseable
           (close [_])))]
      (with-system [{tx-log ::tx-log/kafka} config]
        (is (= 104614 @(tx-log/last-t tx-log)))))))


(def producer-config {:bootstrap-servers "localhost:9092"})


(deftest create-producer-test
  (is (instance? KafkaProducer (kafka/create-producer producer-config))))


(deftest create-consumer-test
  (given (.assignment (kafka/create-consumer producer-config))
    count := 1
    [0] := (TopicPartition. "tx" 0)))


(deftest last-t-executor-shutdown-timeout-test
  (let [{::kafka/keys [last-t-executor] :as system}
        (ig/init {::kafka/last-t-executor {}})]

    ;; will produce a timeout, because the function runs 11 seconds
    (ex/execute! last-t-executor #(Thread/sleep 11000))

    ;; ensure that the function is called before the scheduler is halted
    (Thread/sleep 100)

    (ig/halt! system)

    ;; the scheduler is shut down
    (is (ex/shutdown? last-t-executor))

    ;; but it isn't terminated yet
    (is (not (ex/terminated? last-t-executor)))))
