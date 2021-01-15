(ns blaze.db.tx-log.local-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem :refer [new-mem-kv-store]]
    [blaze.db.kv.mem-spec]
    [blaze.db.tx-log :as tx-log]
    [blaze.db.tx-log.local :as local :refer [new-local-tx-log]]
    [blaze.db.tx-log.local-spec]
    [blaze.db.tx-log.spec]
    [blaze.executors :as ex]
    [blaze.fhir.hash :as hash]
    [cheshire.core :as cheshire]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [java-time :as jt]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [java.io Closeable]
    [java.time Clock Instant ZoneId]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def clock (Clock/fixed Instant/EPOCH (ZoneId/of "UTC")))
(def executor (ex/single-thread-executor "local-tx-log"))
(def patient-hash-0 (hash/generate {:fhir/type :fhir/Patient :id "0"}))
(def observation-hash-0 (hash/generate {:fhir/type :fhir/Observation :id "0"}))


(defn invalid-cbor-content
  "`0xA1` is the start of a map with one entry."
  []
  (byte-array [0xA1]))


(defn new-failing-kv-store []
  (reify kv/KvStore
    (-new-snapshot [_]
      (reify
        kv/KvSnapshot
        (-new-iterator [_]
          (reify
            kv/KvIterator
            (-seek-to-last [_])
            (-seek [_ _])
            (-valid [_] false)
            Closeable
            (close [_])))
        Closeable
        (close [_])))
    (-put [_ _ _]
      (throw (Exception. "put-error")))))


(defn- tx-log [kv-store]
  (-> (ig/init
        {:blaze.db.tx-log/local
         {:kv-store kv-store}})
      (:blaze.db.tx-log/local)))


(deftest init-test
  (is (s/valid? :blaze.db/tx-log (tx-log (new-mem-kv-store)))))


(deftest tx-log-test
  (testing "an empty transaction log has no transaction data"
    (let [tx-log (new-local-tx-log (new-mem-kv-store) clock executor)
                queue (tx-log/new-queue tx-log 1)]
      (is (empty? (tx-log/poll queue (jt/millis 10))))))

  (testing "with one submitted command in one transaction"
    (let [tx-log (new-local-tx-log (new-mem-kv-store) clock executor)]
      @(tx-log/submit tx-log [{:op "create" :type "Patient" :id "0"
                               :hash patient-hash-0}])

      (with-open [queue (tx-log/new-queue tx-log 1)]
        (given (first (tx-log/poll queue (jt/millis 10)))
          :t := 1
          :instant := (Instant/ofEpochSecond 0)
          [:tx-cmds 0 :op] := "create"
          [:tx-cmds 0 :type] := "Patient"
          [:tx-cmds 0 :id] := "0"
          [:tx-cmds 0 :hash] := patient-hash-0))))

  (testing "with two submitted commands in two transactions"
    (let [tx-log (new-local-tx-log (new-mem-kv-store) clock executor)]
      @(tx-log/submit tx-log [{:op "create" :type "Patient" :id "0"
                               :hash patient-hash-0}])
      @(tx-log/submit tx-log [{:op "create" :type "Observation" :id "0"
                               :hash observation-hash-0
                               :refs [["Patient" "0"]]}])

      (with-open [queue (tx-log/new-queue tx-log 1)]
        (given (second (tx-log/poll queue (jt/millis 10)))
          :t := 2
          :instant := (Instant/ofEpochSecond 0)
          [:tx-cmds 0 :op] := "create"
          [:tx-cmds 0 :type] := "Observation"
          [:tx-cmds 0 :id] := "0"
          [:tx-cmds 0 :hash] := observation-hash-0
          [:tx-cmds 0 :refs] := [["Patient" "0"]]))))

  (testing "with invalid transaction data"
    (testing "with invalid key"
      (let [kv-store (new-mem-kv-store)]
        (kv/put! kv-store (byte-array 0) (byte-array 0))

        (let [tx-log (new-local-tx-log kv-store clock executor)]

          (testing "the invalid transaction data is ignored"
            (with-open [queue (tx-log/new-queue tx-log 1)]
              (is (empty? (tx-log/poll queue (jt/millis 10)))))))))

    (testing "with invalid key followed by valid entry"
      (let [kv-store (new-mem-kv-store)]
        (kv/put! kv-store (byte-array 0) (byte-array 0))
        (kv/put! kv-store (local/encode-key 1) (local/encode-tx-data
                                              (Instant/ofEpochSecond 0)
                                              [{:op "create" :type "Patient" :id "0"
                                                :hash patient-hash-0}]))

        (let [tx-log (new-local-tx-log kv-store clock executor)]

          (testing "the invalid transaction data is ignored"
            (with-open [queue (tx-log/new-queue tx-log 0)]
              (given (first (tx-log/poll queue (jt/millis 10)))
                :t := 1
                :instant := (Instant/ofEpochSecond 0)
                [:tx-cmds 0 :op] := "create"
                [:tx-cmds 0 :type] := "Patient"
                [:tx-cmds 0 :id] := "0"
                [:tx-cmds 0 :hash] := patient-hash-0))))))

    (testing "with two invalid keys followed by valid entry"
      (let [kv-store (new-mem-kv-store)]
        (kv/put! kv-store (byte-array 0) (byte-array 0))
        (kv/put! kv-store (byte-array 1) (byte-array 0))
        (kv/put! kv-store (local/encode-key 1) (local/encode-tx-data
                                              (Instant/ofEpochSecond 0)
                                              [{:op "create" :type "Patient" :id "0"
                                                :hash patient-hash-0}]))

        (let [tx-log (new-local-tx-log kv-store clock executor)]

          (testing "the invalid transaction data is ignored"
            (with-open [queue (tx-log/new-queue tx-log 0)]
              (given (first (tx-log/poll queue (jt/millis 10)))
                :t := 1
                :instant := (Instant/ofEpochSecond 0)
                [:tx-cmds 0 :op] := "create"
                [:tx-cmds 0 :type] := "Patient"
                [:tx-cmds 0 :id] := "0"
                [:tx-cmds 0 :hash] := patient-hash-0))))))

    (testing "with empty value"
      (let [kv-store (new-mem-kv-store)]
        (kv/put! kv-store (byte-array Long/BYTES) (byte-array 0))

        (let [tx-log (new-local-tx-log kv-store clock executor)]

          (testing "the invalid transaction data is ignored"
            (with-open [queue (tx-log/new-queue tx-log 1)]
              (is (empty? (tx-log/poll queue (jt/millis 10)))))))))

    (testing "with invalid cbor value"
      (let [kv-store (new-mem-kv-store)]
        (kv/put! kv-store (byte-array Long/BYTES) (invalid-cbor-content))

        (let [tx-log (new-local-tx-log kv-store clock executor)]

          (testing "the invalid transaction data is ignored"
            (with-open [queue (tx-log/new-queue tx-log 1)]
              (is (empty? (tx-log/poll queue (jt/millis 10)))))))))

    (testing "with invalid instant value"
      (let [kv-store (new-mem-kv-store)]
        (kv/put! kv-store (byte-array Long/BYTES) (cheshire/generate-cbor {:instant ""}))

        (let [tx-log (new-local-tx-log kv-store clock executor)]

          (testing "the invalid transaction data is ignored"
            (with-open [queue (tx-log/new-queue tx-log 1)]
              (is (empty? (tx-log/poll queue (jt/millis 10)))))))))

    (testing "with invalid tx-cmd value"
      (let [kv-store (new-mem-kv-store)]
        (kv/put! kv-store (byte-array Long/BYTES) (cheshire/generate-cbor {:tx-cmds [{}]}))

        (let [tx-log (new-local-tx-log kv-store clock executor)]

          (testing "the invalid transaction data is ignored"
            (with-open [queue (tx-log/new-queue tx-log 1)]
              (is (empty? (tx-log/poll queue (jt/millis 10)))))))))

    (testing "with failing kv-store"
      (let [tx-log (new-local-tx-log (new-failing-kv-store) clock executor)]
        (-> (let [result @(-> (tx-log/submit tx-log [{:op "create" :type "Patient"
                                                      :id "0" :hash patient-hash-0}])
                              (ac/exceptionally (comp ex-message ex-cause)))]
              (is (= "put-error" result))))

        (with-open [queue (tx-log/new-queue tx-log 1)]
          (is (empty? (tx-log/poll queue (jt/millis 10)))))))))
