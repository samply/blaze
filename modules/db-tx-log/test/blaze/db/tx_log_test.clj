(ns blaze.db.tx-log-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.tx-log :as tx-log]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [is deftest]]
    [java-time.api :as time])
  (:import
    [java.lang AutoCloseable]
    [java.time Instant]))


(set! *warn-on-reflection* true)
(st/instrument)
(tu/init-fhir-specs)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def patient-hash-0 (hash/generate {:fhir/type :fhir/Patient :id "0"}))


(deftest submit-test
  (let [tx-log (reify tx-log/TxLog
                 (-submit [_ _]
                   (ac/completed-future 1)))]
    (is (= 1 @(tx-log/submit
                tx-log
                [{:op "create"
                  :type "Patient"
                  :id "0"
                  :hash patient-hash-0}])))))


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


(deftest new-queue-test
  (let [tx-log (reify tx-log/TxLog
                 (-new-queue [_ _]
                   (reify
                     tx-log/Queue
                     (-poll [_ _]
                       [tx-data])
                     AutoCloseable
                     (close [_]))))]
    (with-open [queue (tx-log/new-queue tx-log 1)]
      (is (= [tx-data] (tx-log/poll! queue (time/millis 100)))))))
