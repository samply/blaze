(ns blaze.db.tx-log.kafka.util-test
  (:require
   [blaze.db.tx-log.kafka.codec :as codec]
   [blaze.db.tx-log.kafka.util :as u]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.test-util]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [java.time Instant]
   [java.util Optional]
   [org.apache.kafka.clients.consumer ConsumerRecord]
   [org.apache.kafka.common.header.internals RecordHeader RecordHeaders]
   [org.apache.kafka.common.record TimestampType]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def hash-patient-0 (hash/generate {:fhir/type :fhir/Patient :id "0"}))

(defn consumer-record
  ([offset timestamp timestamp-type value]
   (consumer-record offset timestamp timestamp-type value (RecordHeaders.)))
  ([offset timestamp timestamp-type value headers]
   (ConsumerRecord. "tx" 0 ^long offset ^long timestamp
                    ^TimestampType timestamp-type 0 0 nil value
                    ^RecordHeaders headers (Optional/empty))))

(defn- permissions-headers [^bytes value]
  (let [headers [(RecordHeader. ^String codec/permissions-header value)]]
    (RecordHeaders. ^Iterable headers)))

(deftest record-transformer-test
  (testing "skips record with wrong timestamp type"
    (let [cmd {:op "create" :type "Patient" :id "0" :hash hash-patient-0}
          record (consumer-record 0 0 TimestampType/CREATE_TIME [cmd])]
      (is (empty? (into [] u/record-transformer [record])))))

  (testing "skips record with invalid transaction commands"
    (let [cmd {:op "create"}
          record (consumer-record 0 0 TimestampType/LOG_APPEND_TIME [cmd])]
      (is (empty? (into [] u/record-transformer [record])))))

  (testing "skips record with invalid permissions"
    (let [cmd {:op "create" :type "Patient" :id "0" :hash hash-patient-0}]
      (doseq [value [(byte-array [0xA1])
                     (.serialize codec/serializer "tx" {:write-access-control [1]})]]
        (let [record (consumer-record 0 0 TimestampType/LOG_APPEND_TIME [cmd]
                                      (permissions-headers value))]
          (is (empty? (into [] u/record-transformer [record])))))))

  (testing "success"
    (let [cmd {:op "create" :type "Patient" :id "0" :hash hash-patient-0}
          record (consumer-record 0 0 TimestampType/LOG_APPEND_TIME [cmd])]
      (given (first (into [] u/record-transformer [record]))
        :t := 1
        :instant := (Instant/ofEpochSecond 0)
        :tx-cmds := [cmd]
        :permissions := nil)

      (testing "a record without header has no permissions"
        (is (not (contains? (first (into [] u/record-transformer [record]))
                            :permissions))))))

  (testing "success with permissions"
    (let [cmd {:op "create" :type "Patient" :id "0" :hash hash-patient-0}
          permissions {:write-access-control #{"read-only"}}
          record (consumer-record 0 0 TimestampType/LOG_APPEND_TIME [cmd]
                                  (permissions-headers
                                   (codec/encode-permissions permissions)))]
      (given (first (into [] u/record-transformer [record]))
        :t := 1
        :tx-cmds := [cmd]
        :permissions := permissions))))
