(ns blaze.db.tx-log.kafka.util-test
  (:require
    [blaze.db.tx-log.kafka.util :as u]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant]
    [org.apache.kafka.clients.consumer ConsumerRecord]
    [org.apache.kafka.common.record TimestampType]
    [org.apache.kafka.common.header.internals RecordHeaders]
    [java.util Optional]))


(st/instrument)
(tu/init-fhir-specs)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def hash-patient-0 (hash/generate {:fhir/type :fhir/Patient :id "0"}))


(defn consumer-record [offset timestamp timestamp-type value]
  (ConsumerRecord. "tx" 0 ^long offset ^long timestamp
                   ^TimestampType timestamp-type 0 0 nil value (RecordHeaders.)
                   (Optional/empty)))


(deftest record-transformer-test
  (testing "skips record with wrong timestamp type"
    (let [cmd {:op "create" :type "Patient" :id "0" :hash hash-patient-0}
          record (consumer-record 0 0 TimestampType/CREATE_TIME [cmd])]
      (is (empty? (into [] u/record-transformer [record])))))

  (testing "skips record with invalid transaction commands"
    (let [cmd {:op "create"}
          record (consumer-record 0 0 TimestampType/LOG_APPEND_TIME [cmd])]
      (is (empty? (into [] u/record-transformer [record])))))

  (testing "success"
    (let [cmd {:op "create" :type "Patient" :id "0" :hash hash-patient-0}
          record (consumer-record 0 0 TimestampType/LOG_APPEND_TIME [cmd])]
      (given (first (into [] u/record-transformer [record]))
        :t := 1
        :instant := (Instant/ofEpochSecond 0)
        :tx-cmds := [cmd]))))
