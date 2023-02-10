(ns blaze.db.tx-log.kafka.util-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.resource-store :as rs]
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
    [java.util Optional]
    [org.apache.kafka.clients.consumer ConsumerRecord]
    [org.apache.kafka.common.header.internals RecordHeaders]
    [org.apache.kafka.common.record TimestampType]))


(set! *warn-on-reflection* true)
(st/instrument)
(tu/init-fhir-specs)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def patient-0 {:fhir/type :fhir/Patient :id "0"})


(def hash-patient-0 (hash/generate patient-0))


(defn consumer-record [offset timestamp timestamp-type value]
  (ConsumerRecord. "tx" 0 ^long offset ^long timestamp
                   ^TimestampType timestamp-type 0 0 nil value (RecordHeaders.)
                   (Optional/empty)))


(def record-transformer
  (u/record-transformer
    (reify rs/ResourceStore
      (-get [_ hash]
        (assert (= hash-patient-0 hash))
        (ac/completed-future patient-0)))))


(deftest record-transformer-test
  (testing "skips record with wrong timestamp type"
    (let [cmd {:op "create" :type "Patient" :id "0" :hash hash-patient-0}
          record (consumer-record 0 0 TimestampType/CREATE_TIME [cmd])]
      (is (empty? (into [] record-transformer [record])))))

  (testing "skips record with invalid transaction commands"
    (let [cmd {:op "create"}
          record (consumer-record 0 0 TimestampType/LOG_APPEND_TIME [cmd])]
      (is (empty? (into [] record-transformer [record])))))

  (testing "success"
    (let [cmds [{:op "create" :type "Patient" :id "0" :hash hash-patient-0}
                {:op "delete" :type "Observation" :id "1"}]
          record (consumer-record 0 0 TimestampType/LOG_APPEND_TIME cmds)]
      (given (first (into [] record-transformer [record]))
        :t := 1
        :instant := (Instant/ofEpochSecond 0)
        [:tx-cmds 0 :op] := "create"
        [:tx-cmds 0 :type] := "Patient"
        [:tx-cmds 0 :id] := "0"
        [:tx-cmds 0 :hash] := hash-patient-0
        [:tx-cmds 0 :resource ac/join] := patient-0
        [:tx-cmds 1 :op] := "delete"
        [:tx-cmds 1 :type] := "Observation"
        [:tx-cmds 1 :id] := "1"))))
