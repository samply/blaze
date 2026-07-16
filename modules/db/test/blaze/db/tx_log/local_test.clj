(ns blaze.db.tx-log.local-test
  (:require
   [blaze.async.comp :as ac]
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
   [blaze.fhir.test-util]
   [blaze.module.test-util :refer [given-failed-future given-failed-system with-system]]
   [blaze.test-util :as tu]
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
   [clojure.lang IDeref]
   [com.fasterxml.jackson.dataformat.cbor CBORFactory]
   [java.lang AutoCloseable]
   [java.time Instant]
   [java.util.concurrent CountDownLatch TimeUnit]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private cbor-object-mapper
  (j/object-mapper
   {:factory (CBORFactory.)
    :decode-key-fn true}))

(def patient-hash-0 (hash/generate {:fhir/type :fhir/Patient :id "0"}))
(def observation-hash-0 (hash/generate {:fhir/type :fhir/Observation :id "0"}))

(defn invalid-cbor-content
  "`0xA1` is the start of a map with one entry."
  []
  (byte-array [0xA1]))

(defn- empty-snapshot []
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

(defmethod ig/init-key ::failing-kv-store [_ _]
  (reify p/KvStore
    (-new-snapshot [_]
      (empty-snapshot))
    (-put [_ _]
      (throw (Exception. "put-error")))))

(defmethod ig/init-key ::recording-kv-store [_ {:keys [kv-store]}]
  (let [put-thread-names (atom [])]
    (reify
      p/KvStore
      (-new-snapshot [_]
        (p/-new-snapshot kv-store))
      (-put [_ entries]
        (swap! put-thread-names conj (.getName (Thread/currentThread)))
        (p/-put kv-store entries))
      IDeref
      (deref [_]
        @put-thread-names))))

(defmethod ig/init-key ::blocking-kv-store
  [_ {:keys [^CountDownLatch started-latch ^CountDownLatch blocking-latch]}]
  (reify p/KvStore
    (-new-snapshot [_]
      (empty-snapshot))
    (-put [_ _]
      (.countDown started-latch)
      (.await blocking-latch))))

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

(def recording-kv-store-config
  {::tx-log/local
   {:kv-store (ig/ref ::recording-kv-store)
    :clock (ig/ref :blaze.test/fixed-clock)}
   ::recording-kv-store
   {:kv-store (ig/ref :blaze.db/transaction-kv-store)}
   [::kv/mem :blaze.db/transaction-kv-store]
   {:column-families {}}
   :blaze.test/fixed-clock {}})

(defn- blocking-kv-store-config [started-latch blocking-latch]
  {::tx-log/local
   {:kv-store (ig/ref ::blocking-kv-store)
    :clock (ig/ref :blaze.test/fixed-clock)}
   ::blocking-kv-store
   {:started-latch started-latch
    :blocking-latch blocking-latch}
   :blaze.test/fixed-clock {}})

(deftest init-test
  (testing "nil config"
    (given-failed-system {::tx-log/local nil}
      :key := ::tx-log/local
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {::tx-log/local {}}
      :key := ::tx-log/local
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :kv-store))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))))

  (testing "missing clock"
    (given-failed-system (update config ::tx-log/local dissoc :clock)
      :key := ::tx-log/local
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock)))))

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

(deftest submit-executor-test
  (testing "the transaction data is stored on the single `local-tx-log` thread"
    (with-system [{tx-log ::tx-log/local
                   kv-store ::recording-kv-store}
                  recording-kv-store-config]
      @(tx-log/submit
        tx-log
        [{:op "create" :type "Patient" :id "0" :hash patient-hash-0}]
        nil)

      (is (= ["local-tx-log"] @kv-store)))))

(deftest submit-executor-shutdown-timeout-test
  (let [started-latch (CountDownLatch. 1)
        blocking-latch (CountDownLatch. 1)
        {tx-log ::tx-log/local :as system}
        (ig/init (blocking-kv-store-config started-latch blocking-latch))
        future (tx-log/submit
                tx-log
                [{:op "create" :type "Patient" :id "0" :hash patient-hash-0}]
                nil)]

    ;; wait until the submit task blocks in the kv-store
    (is (true? (.await started-latch 10 TimeUnit/SECONDS)))

    ;; halting runs into the 10 second termination timeout of the submit
    ;; executor
    (ig/halt! system)

    ;; the future isn't completed because the submit task is still blocked
    (is (false? (ac/done? future)))

    ;; unblock the submit task so that its thread can end
    (.countDown blocking-latch)))

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
