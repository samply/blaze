(ns blaze.db.tx-log.kafka.util-test
  (:require
    [blaze.db.tx-log.kafka.util :as u]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant]
    [org.apache.kafka.clients.consumer ConsumerRecord]
    [org.apache.kafka.common.record TimestampType]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def hash-patient-0 (hash/generate {:fhir/type :fhir/Patient :id "0"}))


(defn consumer-record [offset timestamp timestamp-type value]
  (ConsumerRecord. "tx" 0 offset timestamp timestamp-type 0 0 0 nil value))


(deftest record-transformer-test
  (testing "skips record with wrong timestamp type"
    (let [cmd {:op "create" :type "Patient" :id "0" :hash hash-patient-0}]
      (is (empty? (into [] u/record-transformer [(consumer-record 0 0 TimestampType/CREATE_TIME [cmd])])))))

  (testing "skips record with invalid transaction commands"
    (is (empty? (into [] u/record-transformer [(consumer-record 0 0 TimestampType/LOG_APPEND_TIME [{:op "create"}])]))))

  (testing "success"
    (let [cmd {:op "create" :type "Patient" :id "0" :hash hash-patient-0}]
      (given (first (into [] u/record-transformer [(consumer-record 0 0 TimestampType/LOG_APPEND_TIME [cmd])]))
        :t := 1
        :instant := (Instant/ofEpochSecond 0)
        :tx-cmds := [cmd]))))
