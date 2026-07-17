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
   [java.time Instant]))

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

(defmethod ig/init-key ::blocking-kv-store [_ {:keys [kv-store]}]
  (let [put-called (promise)
        release-put (promise)
        put-sizes (atom [])]
    (with-meta
      (reify
        p/KvStore
        (-new-snapshot [_]
          (p/-new-snapshot kv-store))
        (-put [_ entries]
          (swap! put-sizes conj (count entries))
          (p/-put kv-store entries)
          (deliver put-called true)
          @release-put))
      {:put-called put-called :release-put release-put
       :put-sizes put-sizes})))

(defmethod ig/init-key ::failing-once-kv-store [_ {:keys [kv-store]}]
  (let [failed? (atom false)]
    (reify
      p/KvStore
      (-new-snapshot [_]
        (p/-new-snapshot kv-store))
      (-put [_ entries]
        (if (compare-and-set! failed? false true)
          (throw (Exception. "put-error"))
          (p/-put kv-store entries))))))

(defmethod ig/init-key ::snapshot-counting-kv-store [_ {:keys [kv-store]}]
  (let [snapshot-count (atom 0)]
    (reify
      p/KvStore
      (-new-snapshot [_]
        (swap! snapshot-count inc)
        (p/-new-snapshot kv-store))
      (-put [_ entries]
        (p/-put kv-store entries))
      IDeref
      (deref [_]
        @snapshot-count))))

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

(def blocking-kv-store-config
  {::tx-log/local
   {:kv-store (ig/ref ::blocking-kv-store)
    :clock (ig/ref :blaze.test/fixed-clock)}
   ::blocking-kv-store
   {:kv-store (ig/ref :blaze.db/transaction-kv-store)}
   [::kv/mem :blaze.db/transaction-kv-store]
   {:column-families {}}
   :blaze.test/fixed-clock {}})

(def failing-once-kv-store-config
  {::tx-log/local
   {:kv-store (ig/ref ::failing-once-kv-store)
    :clock (ig/ref :blaze.test/fixed-clock)}
   ::failing-once-kv-store
   {:kv-store (ig/ref :blaze.db/transaction-kv-store)}
   [::kv/mem :blaze.db/transaction-kv-store]
   {:column-families {}}
   :blaze.test/fixed-clock {}})

(def snapshot-counting-kv-store-config
  {::tx-log/local
   {:kv-store (ig/ref ::snapshot-counting-kv-store)
    :clock (ig/ref :blaze.test/fixed-clock)}
   ::snapshot-counting-kv-store
   {:kv-store (ig/ref :blaze.db/transaction-kv-store)}
   [::kv/mem :blaze.db/transaction-kv-store]
   {:column-families {}}
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
        (is (empty? (tx-log/poll! tx-log 1 (time/millis 10)))))))

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
        (given (first (tx-log/poll! tx-log 1 (time/millis 10)))
          :t := 1
          :instant := (Instant/ofEpochSecond 0)
          [:tx-cmds 0 :op] := "create"
          [:tx-cmds 0 :type] := "Patient"
          [:tx-cmds 0 :id] := "0"
          [:tx-cmds 0 :hash] := patient-hash-0))))

  (testing "with one submitted command in one transaction"
    (with-system [{tx-log ::tx-log/local} config]
      @(tx-log/submit
        tx-log
        [{:op "create" :type "Patient" :id "0" :hash patient-hash-0}]
        nil)

      (given (first (tx-log/poll! tx-log 1 (time/millis 10)))
        :t := 1
        :instant := (Instant/ofEpochSecond 0)
        [:tx-cmds 0 :op] := "create"
        [:tx-cmds 0 :type] := "Patient"
        [:tx-cmds 0 :id] := "0"
        [:tx-cmds 0 :hash] := patient-hash-0)))

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

      (given (second (tx-log/poll! tx-log 1 (time/millis 10)))
        :t := 2
        :instant := (Instant/ofEpochSecond 0)
        [:tx-cmds 0 :op] := "create"
        [:tx-cmds 0 :type] := "Observation"
        [:tx-cmds 0 :id] := "0"
        [:tx-cmds 0 :hash] := observation-hash-0
        [:tx-cmds 0 :refs] := [["Patient" "0"]])))

  (testing "with local payload"
    (with-system [{tx-log ::tx-log/local} config]
      @(tx-log/submit
        tx-log
        [{:op "create" :type "Patient" :id "0" :hash patient-hash-0}]
        ::payload)

      (given (first (tx-log/poll! tx-log 1 (time/millis 10)))
        :local-payload := ::payload)))

  (testing "with invalid transaction data"
    (testing "with invalid key"
      (with-system [{tx-log ::tx-log/local
                     kv-store [::kv/mem :blaze.db/transaction-kv-store]}
                    config]
        (kv/put! kv-store [[:default (byte-array 0) (byte-array 0)]])

        (testing "the invalid transaction data is ignored"
          (is (empty? (tx-log/poll! tx-log 1 (time/millis 10)))))))

    (testing "with invalid key followed by valid entry"
      (with-system [{tx-log ::tx-log/local
                     kv-store [::kv/mem :blaze.db/transaction-kv-store]}
                    config]
        (kv/put! kv-store [[:default (byte-array 0) (byte-array 0)]])
        (kv/put! kv-store [(codec/encode-entry 1 (Instant/ofEpochSecond 0)
                                               [{:op "create" :type "Patient"
                                                 :id "0" :hash patient-hash-0}])])

        (testing "the invalid transaction data is ignored"
          (given (first (tx-log/poll! tx-log 0 (time/millis 10)))
            :t := 1
            :instant := (Instant/ofEpochSecond 0)
            [:tx-cmds 0 :op] := "create"
            [:tx-cmds 0 :type] := "Patient"
            [:tx-cmds 0 :id] := "0"
            [:tx-cmds 0 :hash] := patient-hash-0))))

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
          (given (first (tx-log/poll! tx-log 0 (time/millis 10)))
            :t := 1
            :instant := (Instant/ofEpochSecond 0)
            [:tx-cmds 0 :op] := "create"
            [:tx-cmds 0 :type] := "Patient"
            [:tx-cmds 0 :id] := "0"
            [:tx-cmds 0 :hash] := patient-hash-0))))

    (testing "with empty value"
      (with-system [{tx-log ::tx-log/local
                     kv-store [::kv/mem :blaze.db/transaction-kv-store]}
                    config]
        (kv/put! kv-store [[:default (byte-array Long/BYTES) (byte-array 0)]])

        (testing "the invalid transaction data is ignored"
          (is (empty? (tx-log/poll! tx-log 1 (time/millis 10)))))))

    (testing "with invalid cbor value"
      (with-system [{tx-log ::tx-log/local
                     kv-store [::kv/mem :blaze.db/transaction-kv-store]}
                    config]
        (kv/put! kv-store [[:default (byte-array Long/BYTES) (invalid-cbor-content)]])

        (testing "the invalid transaction data is ignored"
          (is (empty? (tx-log/poll! tx-log 1 (time/millis 10)))))))

    (testing "with invalid instant value"
      (with-system [{tx-log ::tx-log/local
                     kv-store [::kv/mem :blaze.db/transaction-kv-store]}
                    config]
        (kv/put! kv-store [[:default (byte-array Long/BYTES) (write-cbor {:instant ""})]])

        (testing "the invalid transaction data is ignored"
          (is (empty? (tx-log/poll! tx-log 1 (time/millis 10)))))))

    (testing "with invalid tx-cmd value"
      (with-system [{tx-log ::tx-log/local
                     kv-store [::kv/mem :blaze.db/transaction-kv-store]}
                    config]
        (kv/put! kv-store [[:default (byte-array Long/BYTES) (write-cbor {:tx-cmds [{}]})]])

        (testing "the invalid transaction data is ignored"
          (is (empty? (tx-log/poll! tx-log 1 (time/millis 10)))))))

    (testing "with failing kv-store"
      (let [tx-cmds [{:op "create" :type "Patient" :id "0" :hash patient-hash-0}]]
        (with-system [{tx-log ::tx-log/local} failing-kv-store-system]
          (given-failed-future (tx-log/submit tx-log tx-cmds nil)
            ::anom/message := "put-error")

          (is (empty? (tx-log/poll! tx-log 1 (time/millis 10)))))))))

(deftest poll-test
  (testing "polling the same offset again returns the same transaction data"
    (with-system [{tx-log ::tx-log/local} config]
      @(tx-log/submit
        tx-log
        [{:op "create" :type "Patient" :id "0" :hash patient-hash-0}]
        nil)

      (is (= (tx-log/poll! tx-log 1 (time/millis 10))
             (tx-log/poll! tx-log 1 (time/millis 10))))

      (given (first (tx-log/poll! tx-log 1 (time/millis 10)))
        :t := 1
        :instant := (Instant/ofEpochSecond 0)
        [:tx-cmds 0 :op] := "create"
        [:tx-cmds 0 :type] := "Patient"
        [:tx-cmds 0 :id] := "0"
        [:tx-cmds 0 :hash] := patient-hash-0)))

  (testing "a poller is woken up by an incoming transaction"
    (with-system [{tx-log ::tx-log/local} config]
      (let [tx-data (ac/supply-async
                     #(loop [tx-data nil]
                        (if (seq tx-data)
                          (first tx-data)
                          (recur (tx-log/poll! tx-log 1 (time/millis 100))))))]

        @(tx-log/submit
          tx-log
          [{:op "create" :type "Patient" :id "0" :hash patient-hash-0}]
          nil)

        (given @tx-data
          :t := 1
          :instant := (Instant/ofEpochSecond 0)
          [:tx-cmds 0 :op] := "create"
          [:tx-cmds 0 :type] := "Patient"
          [:tx-cmds 0 :id] := "0"
          [:tx-cmds 0 :hash] := patient-hash-0))))

  (testing "polling while a submit stores its transaction data waits instead
            of reading the not yet published transaction data from storage"
    (with-system [{tx-log ::tx-log/local
                   kv-store ::blocking-kv-store}
                  blocking-kv-store-config]
      (let [{:keys [put-called release-put]} (meta kv-store)
            t (tx-log/submit
               tx-log
               [{:op "create" :type "Patient" :id "0" :hash patient-hash-0}]
               ::payload)]
        (is (true? (deref put-called 1000 nil)))

        (testing "the transaction data is stored but not yet published, so
                  polling waits instead of returning it without the local
                  payload"
          (is (empty? (tx-log/poll! tx-log 1 (time/millis 10)))))

        (deliver release-put nil)
        (is (= 1 @t))

        (testing "after the submit has finished, polling returns the
                  transaction data including the local payload"
          (given (first (tx-log/poll! tx-log 1 (time/millis 10)))
            :t := 1
            :local-payload := ::payload)))))

  (testing "polling beyond the last submitted transaction doesn't access
            storage"
    (with-system [{tx-log ::tx-log/local
                   kv-store ::snapshot-counting-kv-store}
                  snapshot-counting-kv-store-config]
      @(tx-log/submit
        tx-log
        [{:op "create" :type "Patient" :id "0" :hash patient-hash-0}]
        nil)

      (given (first (tx-log/poll! tx-log 1 (time/millis 10)))
        :t := 1)

      (let [snapshot-count @kv-store]
        (is (empty? (tx-log/poll! tx-log 2 (time/millis 10))))
        (is (= snapshot-count @kv-store)))))

  (testing "polling releases acknowledged transaction data from the buffer"
    (with-system [{tx-log ::tx-log/local} config]
      (dotimes [_ 2]
        @(tx-log/submit
          tx-log
          [{:op "create" :type "Patient" :id "0" :hash patient-hash-0}]
          ::payload))

      (testing "before acknowledgment, old transaction data comes from the
                buffer with the local payload"
        (given (first (tx-log/poll! tx-log 1 (time/millis 10)))
          :t := 1
          :local-payload := ::payload))

      (testing "polling with offset 2 acknowledges the transaction data below"
        (given (first (tx-log/poll! tx-log 2 (time/millis 10)))
          :t := 2
          :local-payload := ::payload))

      (testing "afterwards the released transaction data comes from storage
                without the local payload"
        (given (first (tx-log/poll! tx-log 1 (time/millis 10)))
          :t := 1
          :local-payload := nil)))))

(deftest submit-batch-test
  (testing "transaction data of multiple submits is stored in one batch"
    (with-system [{tx-log ::tx-log/local
                   kv-store ::blocking-kv-store}
                  blocking-kv-store-config]
      (let [{:keys [put-called release-put put-sizes]} (meta kv-store)
            ;; the first submit blocks the storer thread in the kv-store
            future-1 (tx-log/submit
                      tx-log
                      [{:op "create" :type "Patient" :id "0"
                        :hash patient-hash-0}]
                      nil)]
        (is (true? (deref put-called 1000 nil)))

        ;; two more submits are buffered while the storer thread is blocked
        (let [future-2 (tx-log/submit
                        tx-log
                        [{:op "create" :type "Patient" :id "1"
                          :hash patient-hash-0}]
                        nil)
              future-3 (tx-log/submit
                        tx-log
                        [{:op "create" :type "Patient" :id "2"
                          :hash patient-hash-0}]
                        nil)]
          (deliver release-put nil)
          (is (= 1 @future-1))
          (is (= 2 @future-2))
          (is (= 3 @future-3)))

        (testing "the buffered transaction data was stored with a single put"
          (is (= [1 2] @put-sizes)))))))

(deftest submit-busy-test
  (testing "submits are rejected with a busy anomaly while the buffer is full"
    (with-system [{tx-log ::tx-log/local
                   kv-store ::blocking-kv-store}
                  blocking-kv-store-config]
      (let [{:keys [put-called release-put]} (meta kv-store)]
        ;; the first submit blocks the storer thread in the kv-store
        (tx-log/submit
         tx-log
         [{:op "create" :type "Patient" :id "0" :hash patient-hash-0}]
         nil)
        (is (true? (deref put-called 1000 nil)))

        ;; fill the buffer up to its capacity of 1024 entries
        (dotimes [_ 1022]
          (tx-log/submit
           tx-log
           [{:op "create" :type "Patient" :id "0" :hash patient-hash-0}]
           nil))

        (let [future-1024 (tx-log/submit
                           tx-log
                           [{:op "create" :type "Patient" :id "0"
                             :hash patient-hash-0}]
                           nil)
              rejected-future (tx-log/submit
                               tx-log
                               [{:op "create" :type "Patient" :id "0"
                                 :hash patient-hash-0}]
                               nil)]
          (if (ac/done? rejected-future)
            (given-failed-future rejected-future
              ::anom/category := ::anom/busy
              ::anom/message := "The transaction log buffer with a capacity of 1024 transactions is full. Please try again later.")
            (is false "expected the submit future to be already completed with a busy anomaly"))

          ;; unblock the storer thread
          (deliver release-put nil)
          (is (= 1024 @future-1024))

          (testing "acknowledging the stored transaction data frees the buffer"
            (is (empty? (tx-log/poll! tx-log 1025 (time/millis 10))))
            (is (= 1025 @(tx-log/submit
                          tx-log
                          [{:op "create" :type "Patient" :id "0"
                            :hash patient-hash-0}]
                          nil)))))))))

(deftest submit-failure-gap-test
  (testing "a failed submit leaves a gap in `t`"
    (with-system [{tx-log ::tx-log/local} failing-once-kv-store-config]
      (given-failed-future
       (tx-log/submit
        tx-log
        [{:op "create" :type "Patient" :id "0" :hash patient-hash-0}]
        nil)
        ::anom/message := "put-error")

      (is (= 2 @(tx-log/submit
                 tx-log
                 [{:op "create" :type "Patient" :id "1" :hash patient-hash-0}]
                 nil)))

      (testing "the last `t` is the last assigned `t`"
        (is (= 2 @(tx-log/last-t tx-log))))

      (testing "only the successful transaction is returned"
        (let [tx-data (tx-log/poll! tx-log 1 (time/millis 10))]
          (is (= 1 (count tx-data)))
          (given (first tx-data)
            :t := 2
            [:tx-cmds 0 :id] := "1"))))))
