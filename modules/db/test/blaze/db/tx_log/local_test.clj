(ns blaze.db.tx-log.local-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.byte-string :as bs]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.db.kv.protocols :as p]
   [blaze.db.tx-log :as tx-log]
   [blaze.db.tx-log.local]
   [blaze.db.tx-log.local-spec]
   [blaze.db.tx-log.local.codec :as codec]
   [blaze.db.tx-log.spec]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.log]
   [blaze.module.test-util :refer [given-failed-future with-system]]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [java-time.api :as time]
   [jsonista.core :as j]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [com.fasterxml.jackson.dataformat.cbor CBORFactory]
   [java.lang AutoCloseable]
   [java.time Instant]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private cbor-object-mapper
  (j/object-mapper
   {:factory (CBORFactory.)
    :decode-key-fn true
    :modules [bs/object-mapper-module]}))

(def patient-hash-0 (hash/generate {:fhir/type :fhir/Patient :id "0"}))
(def observation-hash-0 (hash/generate {:fhir/type :fhir/Observation :id "0"}))

(defn invalid-cbor-content
  "`0xA1` is the start of a map with one entry."
  []
  (byte-array [0xA1]))

(defmethod ig/init-key ::failing-kv-store [_ _]
  (reify p/KvStore
    (-new-snapshot [_]
      (reify
        p/KvSnapshot
        (-new-iterator [_ _]
          (reify
            p/KvIterator
            (-seek-to-last [_])
            (-seek [_ _])
            (-valid [_] false)
            AutoCloseable
            (close [_])))
        AutoCloseable
        (close [_])))
    (-put [_ _]
      (throw (Exception. "put-error")))))

(def config
  {::tx-log/local
   {:kv-store (ig/ref :blaze.db/transaction-kv-store)
    :clock (ig/ref :blaze.test/fixed-clock)}
   [::kv/mem :blaze.db/transaction-kv-store]
   {:column-families {}}
   :blaze.test/fixed-clock {}})

(defn- assoc-kv-store-init-data [system init-data]
  (assoc-in system [[::kv/mem :blaze.db/transaction-kv-store] :init-data] init-data))

(def failing-kv-store-system
  {::tx-log/local
   {:kv-store (ig/ref ::failing-kv-store)
    :clock (ig/ref :blaze.test/fixed-clock)}
   ::failing-kv-store {}
   :blaze.test/fixed-clock {}})

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {::tx-log/local nil})
      :key := ::tx-log/local
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::tx-log/local {}})
      :key := ::tx-log/local
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :kv-store))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock)))))

(defn- write-cbor [x]
  (j/write-value-as-bytes x cbor-object-mapper))

(deftest tx-log-test
  (testing "an empty transaction log"
    (with-system [{tx-log ::tx-log/local} config]
      (testing "the last `t` is zero"
        (is (zero? @(tx-log/last-t tx-log))))

      (testing "has no transaction data"
        (with-open [queue (tx-log/new-queue tx-log 1)]
          (is (empty? (tx-log/poll! queue (time/millis 10))))))))

  (testing "an already filled transaction log"
    (with-system [{tx-log ::tx-log/local}
                  (assoc-kv-store-init-data
                   config
                   [[:default
                     (codec/encode-key 1)
                     (codec/encode-tx-data
                      (Instant/ofEpochSecond 0)
                      [{:op "create" :type "Patient" :id "0"
                        :hash patient-hash-0}
                       {:op "delete" :type "Patient" :id "1"}])]])]

      (testing "the last `t` is one"
        (is (= 1 @(tx-log/last-t tx-log))))

      (testing "has transaction data"
        (with-open [queue (tx-log/new-queue tx-log 1)]
          (given (first (tx-log/poll! queue (time/millis 10)))
            :t := 1
            :instant := (Instant/ofEpochSecond 0)
            [:tx-cmds 0 :op] := "create"
            [:tx-cmds 0 :type] := "Patient"
            [:tx-cmds 0 :id] := "0"
            [:tx-cmds 0 :hash] := patient-hash-0)))))

  (testing "with one submitted command in one transaction"
    (with-system [{tx-log ::tx-log/local} config]
      @(tx-log/submit
        tx-log
        [{:op "create" :type "Patient" :id "0" :hash patient-hash-0}]
        nil)

      (with-open [queue (tx-log/new-queue tx-log 1)]
        (given (first (tx-log/poll! queue (time/millis 10)))
          :t := 1
          :instant := (Instant/ofEpochSecond 0)
          [:tx-cmds 0 :op] := "create"
          [:tx-cmds 0 :type] := "Patient"
          [:tx-cmds 0 :id] := "0"
          [:tx-cmds 0 :hash] := patient-hash-0))))

  (testing "with two submitted commands in two transactions"
    (with-system [{tx-log ::tx-log/local} config]
      @(tx-log/submit
        tx-log
        [{:op "create" :type "Patient" :id "0" :hash patient-hash-0}]
        nil)
      @(tx-log/submit
        tx-log
        [{:op "create" :type "Observation" :id "0"
          :hash observation-hash-0
          :refs [["Patient" "0"]]}]
        nil)

      (with-open [queue (tx-log/new-queue tx-log 1)]
        (given (second (tx-log/poll! queue (time/millis 10)))
          :t := 2
          :instant := (Instant/ofEpochSecond 0)
          [:tx-cmds 0 :op] := "create"
          [:tx-cmds 0 :type] := "Observation"
          [:tx-cmds 0 :id] := "0"
          [:tx-cmds 0 :hash] := observation-hash-0
          [:tx-cmds 0 :refs] := [["Patient" "0"]]))))

  (testing "with local payload"
    (with-system [{tx-log ::tx-log/local} config]
      (with-open [queue (tx-log/new-queue tx-log 1)]
        @(tx-log/submit
          tx-log
          [{:op "create" :type "Patient" :id "0" :hash patient-hash-0}]
          ::payload)

        (given (first (tx-log/poll! queue (time/millis 10)))
          :local-payload := ::payload))))

  (testing "with invalid transaction data"
    (testing "with invalid key"
      (with-system [{tx-log ::tx-log/local
                     kv-store [::kv/mem :blaze.db/transaction-kv-store]}
                    config]
        (kv/put! kv-store [[:default (byte-array 0) (byte-array 0)]])

        (testing "the invalid transaction data is ignored"
          (with-open [queue (tx-log/new-queue tx-log 1)]
            (is (empty? (tx-log/poll! queue (time/millis 10))))))))

    (testing "with invalid key followed by valid entry"
      (with-system [{tx-log ::tx-log/local
                     kv-store [::kv/mem :blaze.db/transaction-kv-store]}
                    config]
        (kv/put! kv-store [[:default (byte-array 0) (byte-array 0)]])
        (kv/put! kv-store [(codec/encode-entry 1 (Instant/ofEpochSecond 0)
                                               [{:op "create" :type "Patient"
                                                 :id "0" :hash patient-hash-0}])])

        (testing "the invalid transaction data is ignored"
          (with-open [queue (tx-log/new-queue tx-log 0)]
            (given (first (tx-log/poll! queue (time/millis 10)))
              :t := 1
              :instant := (Instant/ofEpochSecond 0)
              [:tx-cmds 0 :op] := "create"
              [:tx-cmds 0 :type] := "Patient"
              [:tx-cmds 0 :id] := "0"
              [:tx-cmds 0 :hash] := patient-hash-0)))))

    (testing "with two invalid keys followed by valid entry"
      (with-system [{tx-log ::tx-log/local
                     kv-store [::kv/mem :blaze.db/transaction-kv-store]}
                    config]
        (kv/put! kv-store [[:default (byte-array 0) (byte-array 0)]])
        (kv/put! kv-store [[:default (byte-array 1) (byte-array 0)]])
        (kv/put! kv-store [(codec/encode-entry 1 (Instant/ofEpochSecond 0)
                                               [{:op "create" :type "Patient"
                                                 :id "0" :hash patient-hash-0}])])

        (testing "the invalid transaction data is ignored"
          (with-open [queue (tx-log/new-queue tx-log 0)]
            (given (first (tx-log/poll! queue (time/millis 10)))
              :t := 1
              :instant := (Instant/ofEpochSecond 0)
              [:tx-cmds 0 :op] := "create"
              [:tx-cmds 0 :type] := "Patient"
              [:tx-cmds 0 :id] := "0"
              [:tx-cmds 0 :hash] := patient-hash-0)))))

    (testing "with empty value"
      (with-system [{tx-log ::tx-log/local
                     kv-store [::kv/mem :blaze.db/transaction-kv-store]}
                    config]
        (kv/put! kv-store [[:default (byte-array Long/BYTES) (byte-array 0)]])

        (testing "the invalid transaction data is ignored"
          (with-open [queue (tx-log/new-queue tx-log 1)]
            (is (empty? (tx-log/poll! queue (time/millis 10))))))))

    (testing "with invalid cbor value"
      (with-system [{tx-log ::tx-log/local
                     kv-store [::kv/mem :blaze.db/transaction-kv-store]}
                    config]
        (kv/put! kv-store [[:default (byte-array Long/BYTES) (invalid-cbor-content)]])

        (testing "the invalid transaction data is ignored"
          (with-open [queue (tx-log/new-queue tx-log 1)]
            (is (empty? (tx-log/poll! queue (time/millis 10))))))))

    (testing "with invalid instant value"
      (with-system [{tx-log ::tx-log/local
                     kv-store [::kv/mem :blaze.db/transaction-kv-store]}
                    config]
        (kv/put! kv-store [[:default (byte-array Long/BYTES) (write-cbor {:instant ""})]])

        (testing "the invalid transaction data is ignored"
          (with-open [queue (tx-log/new-queue tx-log 1)]
            (is (empty? (tx-log/poll! queue (time/millis 10))))))))

    (testing "with invalid tx-cmd value"
      (with-system [{tx-log ::tx-log/local
                     kv-store [::kv/mem :blaze.db/transaction-kv-store]}
                    config]
        (kv/put! kv-store [[:default (byte-array Long/BYTES) (write-cbor {:tx-cmds [{}]})]])

        (testing "the invalid transaction data is ignored"
          (with-open [queue (tx-log/new-queue tx-log 1)]
            (is (empty? (tx-log/poll! queue (time/millis 10))))))))

    (testing "with failing kv-store"
      (let [tx-cmds [{:op "create" :type "Patient" :id "0" :hash patient-hash-0}]]
        (with-system [{tx-log ::tx-log/local} failing-kv-store-system]
          (given-failed-future (tx-log/submit tx-log tx-cmds nil)
            ::anom/message := "put-error")

          (with-open [queue (tx-log/new-queue tx-log 1)]
            (is (empty? (tx-log/poll! queue (time/millis 10))))))))))

(deftest new-queue-test
  (testing "it is possible to open two queues"
    (testing "with one existing transaction"
      (with-system [{tx-log ::tx-log/local}
                    (assoc-kv-store-init-data
                     config
                     [[:default
                       (codec/encode-key 1)
                       (codec/encode-tx-data
                        (Instant/ofEpochSecond 0)
                        [{:op "create" :type "Patient" :id "0"
                          :hash patient-hash-0}])]])]

        (let [fn #(with-open [queue (tx-log/new-queue tx-log 0)]
                    (->> (fn [] (tx-log/poll! queue (time/millis 10)))
                         (repeatedly 10)
                         (flatten)
                         (first)))
              tx-data-1 (ac/supply-async fn)
              tx-data-2 (ac/supply-async fn)]

          (is (= @tx-data-1 @tx-data-2))

          (given @tx-data-1
            :t := 1
            :instant := (Instant/ofEpochSecond 0)
            [:tx-cmds 0 :op] := "create"
            [:tx-cmds 0 :type] := "Patient"
            [:tx-cmds 0 :id] := "0"
            [:tx-cmds 0 :hash] := patient-hash-0))))

    (testing "with one incoming incoming transaction"
      (with-system [{tx-log ::tx-log/local} config]

        (let [fn #(with-open [queue (tx-log/new-queue tx-log 1)]
                    (loop [tx-data nil]
                      (if (seq tx-data)
                        (first tx-data)
                        (recur (tx-log/poll! queue (time/millis 100))))))
              tx-data-1 (ac/supply-async fn)
              tx-data-2 (ac/supply-async fn)]

          @(tx-log/submit
            tx-log
            [{:op "create" :type "Patient" :id "0" :hash patient-hash-0}]
            nil)

          (is (= @tx-data-1 @tx-data-2))

          (given @tx-data-1
            :t := 1
            :instant := (Instant/ofEpochSecond 0)
            [:tx-cmds 0 :op] := "create"
            [:tx-cmds 0 :type] := "Patient"
            [:tx-cmds 0 :id] := "0"
            [:tx-cmds 0 :hash] := patient-hash-0))))))
