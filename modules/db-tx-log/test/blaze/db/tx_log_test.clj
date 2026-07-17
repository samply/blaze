(ns blaze.db.tx-log-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.tx-log :as tx-log]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.test-util]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is]]
   [java-time.api :as time])
  (:import
   [java.time Instant]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def patient-hash-0 (hash/generate {:fhir/type :fhir/Patient :id "0"}))

(deftest submit-test
  (let [tx-log (reify tx-log/TxLog
                 (-submit [_ _ _]
                   (ac/completed-future 1)))]
    (is (= 1 @(tx-log/submit
               tx-log
               [{:op "create"
                 :type "Patient"
                 :id "0"
                 :hash patient-hash-0}]
               nil)))))

(deftest last-t-test
  (let [tx-log (reify tx-log/TxLog
                 (-last-t [_]
                   (ac/completed-future 161253)))]
    (is (= 161253 @(tx-log/last-t tx-log)))))

(def tx-data {:t 1
              :instant Instant/EPOCH
              :tx-cmds
              [{:op "create"
                :type "Patient"
                :id "0"
                :hash patient-hash-0}]})

(deftest poll-test
  (let [tx-log (reify tx-log/TxLog
                 (-poll [_ offset _]
                   (assert (= 1 offset))
                   [tx-data]))]
    (is (= [tx-data] (tx-log/poll! tx-log 1 (time/millis 100))))))
