(ns blaze.db.tx-log.local-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.db.kv.protocols :as p]
   [blaze.db.tx-log :as tx-log]
   [blaze.db.tx-log.local :as local]
   [blaze.db.tx-log.local-spec]
   [blaze.db.tx-log.local.codec :as codec]
   [blaze.db.tx-log.spec]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.test-util]
   [blaze.metrics.core :as metrics]
   [blaze.metrics.spec]
   [blaze.module.test-util :as mtu :refer [given-failed-system with-system]]
   [blaze.test-util :as tu :refer [given-failed-future]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [java-time.api :as time]
   [jsonista.core :as j]
   [juxt.iota :refer [given]]
   [prometheus.alpha :as prom]
   [taoensso.timbre :as log])
  (:import
   [clojure.lang IDeref]
   [com.fasterxml.jackson.dataformat.cbor CBORFactory]
   [java.lang AutoCloseable]
   [java.time Instant]
   [java.util.concurrent TimeUnit]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private cbor-object-mapper
  (j/object-mapper
   {:factory (CBORFactory.)
    :decode-key-fn true}))

(defn- patient [id]
  {:fhir/type :fhir/Patient :id id})

(defn- create-patient-cmd [id]
  {:op "create" :type "Patient" :id id :hash (hash/generate (patient id))})

(def patient-hash-0 (hash/generate (patient "0")))
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

(deftest duration-seconds-collector-init-test
  (with-system [{collector ::local/duration-seconds} {::local/duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(deftest store-batch-entries-collector-init-test
  (with-system [{collector ::local/store-batch-entries} {::local/store-batch-entries {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(defn- buffer-collector-config [config]
  (assoc config ::local/buffer-collector
         {:tx-logs {::tx-log/local (ig/ref ::tx-log/local)}}))

(deftest buffer-collector-init-test
  (testing "nil config"
    (given-failed-system {::local/buffer-collector nil}
      :key := ::local/buffer-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing tx-logs"
    (given-failed-system {::local/buffer-collector {}}
      :key := ::local/buffer-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :tx-logs))))

  (testing "invalid tx-logs"
    (given-failed-system {::local/buffer-collector {:tx-logs ::invalid}}
      :key := ::local/buffer-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::local/tx-logs]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "is a collector"
    (with-system [{collector ::local/buffer-collector} (buffer-collector-config config)]
      (is (s/valid? :blaze.metrics/collector collector)))))

(defn- buffer-entries
  "Returns a map from the state label to the number of buffer entries of the
  node `main` collected by `collector`."
  [collector]
  (into
   {}
   (map (fn [{[_ state] :label-values :keys [value]}] [state value]))
   (:samples (first (metrics/collect collector)))))

(deftest buffer-collector-test
  (testing "the buffer of an empty transaction log is empty"
    (with-system [{collector ::local/buffer-collector} (buffer-collector-config config)]
      (given (metrics/collect collector)
        count := 1
        [0 :name] := "blaze_db_tx_log_buffer_entries"
        [0 :type] := :gauge
        [0 :samples count] := 2)

      (is (= {"unstored" 0.0 "retained" 0.0} (buffer-entries collector)))))

  (testing "transaction data waiting to be stored is unstored"
    (with-system [{tx-log ::tx-log/local
                   kv-store ::blocking-kv-store
                   collector ::local/buffer-collector}
                  (buffer-collector-config blocking-kv-store-config)]
      (let [{:keys [put-called release-put]} (meta kv-store)
            ;; the first submit blocks the storer thread in the kv-store
            future-1 (tx-log/submit tx-log [(create-patient-cmd "0")] nil)]
        (is (true? (deref put-called 1000 nil)))

        ;; one more submit is buffered while the storer thread is blocked
        (let [future-2 (tx-log/submit tx-log [(create-patient-cmd "1")] nil)]
          (is (= {"unstored" 2.0 "retained" 0.0} (buffer-entries collector)))

          (deliver release-put nil)

          (is (= 1 (deref future-1 1000 ::timeout)))
          (is (= 2 (deref future-2 1000 ::timeout)))))))

  (testing "stored transaction data is retained until the poller acknowledges it"
    (with-system [{tx-log ::tx-log/local
                   collector ::local/buffer-collector}
                  (buffer-collector-config config)]
      (is (= 1 (deref (tx-log/submit tx-log [(create-patient-cmd "0")] nil)
                      1000 ::timeout)))

      (is (= {"unstored" 0.0 "retained" 1.0} (buffer-entries collector)))

      (testing "polling the transaction data doesn't acknowledge it"
        (given (tx-log/poll! tx-log 1 (time/millis 10))
          [0 :t] := 1)

        (is (= {"unstored" 0.0 "retained" 1.0} (buffer-entries collector))))

      (testing "polling with the next offset acknowledges it"
        (is (empty? (tx-log/poll! tx-log 2 (time/millis 10))))

        (is (= {"unstored" 0.0 "retained" 0.0} (buffer-entries collector)))))))

(defn- write-cbor [x]
  (j/write-value-as-bytes x cbor-object-mapper))

(defn- durations
  "Returns the number of durations observed under the node label `main` and the
  op label `op`.

  The last bucket of a Prometheus histogram is the +Inf bucket that counts all
  observations."
  [op]
  (peek (:histogram/buckets (prom/get local/duration-seconds "main" op))))

(defn- submit-durations []
  (durations "submit"))

(defn- store-round-durations []
  (durations "store-round"))

(defn- stored-batches
  "Returns the number of stored batches observed.

  The last bucket of a Prometheus histogram is the +Inf bucket that counts all
  observations."
  []
  (peek (:histogram/buckets (prom/get local/store-batch-entries "main"))))

(defn- store-batch-entries-sum
  "Returns the sum of the number of entries of all stored batches observed."
  []
  (:histogram/sum (prom/get local/store-batch-entries "main")))

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
                      [(create-patient-cmd "0")
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
      @(tx-log/submit tx-log [(create-patient-cmd "0")] nil)

      (given (first (tx-log/poll! tx-log 1 (time/millis 10)))
        :t := 1
        :instant := (Instant/ofEpochSecond 0)
        [:tx-cmds 0 :op] := "create"
        [:tx-cmds 0 :type] := "Patient"
        [:tx-cmds 0 :id] := "0"
        [:tx-cmds 0 :hash] := patient-hash-0)))

  (testing "with two submitted commands in two transactions"
    (with-system [{tx-log ::tx-log/local} config]
      @(tx-log/submit tx-log [(create-patient-cmd "0")] nil)
      @(tx-log/submit tx-log
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
      @(tx-log/submit tx-log [(create-patient-cmd "0")] ::payload)

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
                                               [(create-patient-cmd "0")])])

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
                                               [(create-patient-cmd "0")])])

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
      (let [tx-cmds [(create-patient-cmd "0")]]
        (with-system [{tx-log ::tx-log/local} failing-kv-store-system]
          (given-failed-future (tx-log/submit tx-log tx-cmds nil)
            ::anom/message := "put-error")

          (is (empty? (tx-log/poll! tx-log 1 (time/millis 10)))))))))

(deftest poll-test
  (testing "polling the same offset again returns the same transaction data"
    (with-system [{tx-log ::tx-log/local} config]
      @(tx-log/submit tx-log [(create-patient-cmd "0")] nil)

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

        @(tx-log/submit tx-log [(create-patient-cmd "0")] nil)

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
            t (tx-log/submit tx-log [(create-patient-cmd "0")] ::payload)]
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
      @(tx-log/submit tx-log [(create-patient-cmd "0")] nil)

      (given (first (tx-log/poll! tx-log 1 (time/millis 10)))
        :t := 1)

      (let [snapshot-count @kv-store]
        (is (empty? (tx-log/poll! tx-log 2 (time/millis 10))))
        (is (= snapshot-count @kv-store)))))

  (testing "polling releases acknowledged transaction data from the buffer"
    (with-system [{tx-log ::tx-log/local} config]
      (dotimes [_ 2]
        @(tx-log/submit tx-log [(create-patient-cmd "0")] ::payload))

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

(deftest poll-retains-unstored-test
  (testing "polling with an offset ahead of the stored transaction data retains
            the unstored transaction data in the buffer"
    (with-system [{tx-log ::tx-log/local
                   kv-store ::blocking-kv-store}
                  blocking-kv-store-config]
      (let [{:keys [put-called release-put]} (meta kv-store)
            ;; the first submit blocks the storer thread in the kv-store
            future-1 (tx-log/submit tx-log [(create-patient-cmd "0")] nil)]
        (is (true? (deref put-called 1000 nil)))

        ;; two more submits are buffered while the storer thread is blocked
        (let [future-2 (tx-log/submit tx-log [(create-patient-cmd "1")] nil)
              future-3 (tx-log/submit tx-log [(create-patient-cmd "2")] nil)]

          ;; the poller acknowledges an offset beyond the stored transaction
          ;; data, which must not release the still unstored entries 2 and 3
          (is (empty? (tx-log/poll! tx-log 4 (time/millis 10))))

          (deliver release-put nil)

          (testing "the still unstored transaction data is stored afterwards"
            (is (= 1 (deref future-1 1000 ::timeout)))
            (is (= 2 (deref future-2 1000 ::timeout)))
            (is (= 3 (deref future-3 1000 ::timeout))))

          (testing "and can be polled"
            (given (tx-log/poll! tx-log 2 (time/millis 10))
              [0 :t] := 2
              [1 :t] := 3)))))))

(deftest submit-batch-test
  (testing "transaction data of multiple submits is stored in one batch"
    (with-system [{tx-log ::tx-log/local
                   kv-store ::blocking-kv-store}
                  blocking-kv-store-config]
      (let [{:keys [put-called release-put put-sizes]} (meta kv-store)
            ;; the first submit blocks the storer thread in the kv-store
            future-1 (tx-log/submit tx-log [(create-patient-cmd "0")] nil)]
        (is (true? (deref put-called 1000 nil)))

        ;; two more submits are buffered while the storer thread is blocked
        (let [future-2 (tx-log/submit tx-log [(create-patient-cmd "1")] nil)
              future-3 (tx-log/submit tx-log [(create-patient-cmd "2")] nil)]
          (deliver release-put nil)
          (is (= 1 @future-1))
          (is (= 2 @future-2))
          (is (= 3 @future-3)))

        (testing "the buffered transaction data was stored with a single put"
          (is (= [1 2] @put-sizes)))))))

(deftest store-batch-entries-test
  (testing "the number of entries of each stored batch is observed"
    (with-system [{tx-log ::tx-log/local
                   kv-store ::blocking-kv-store}
                  blocking-kv-store-config]
      (let [{:keys [put-called release-put]} (meta kv-store)
            batches (stored-batches)
            entries (store-batch-entries-sum)
            ;; the first submit blocks the storer thread in the kv-store
            future-1 (tx-log/submit tx-log [(create-patient-cmd "0")] nil)]
        (is (true? (deref put-called 1000 nil)))

        ;; two more submits are buffered while the storer thread is blocked
        (let [future-2 (tx-log/submit tx-log [(create-patient-cmd "1")] nil)
              future-3 (tx-log/submit tx-log [(create-patient-cmd "2")] nil)]
          (deliver release-put nil)
          (is (= 1 @future-1))
          (is (= 2 @future-2))
          (is (= 3 @future-3)))

        (testing "one batch with one entry and one batch with two entries were
                  stored"
          (is (= (+ batches 2) (stored-batches)))
          (is (= (+ entries 3.0) (store-batch-entries-sum)))))))

  (testing "entries of a failed batch aren't observed"
    (with-system [{tx-log ::tx-log/local} failing-kv-store-system]
      (let [batches (stored-batches)]
        (given-failed-future (tx-log/submit tx-log [(create-patient-cmd "0")] nil)
          ::anom/category := ::anom/fault
          ::anom/message := "put-error")

        (is (= batches (stored-batches)))))))

(deftest store-round-duration-test
  (testing "the duration of one round of the store loop is observed"
    (let [durations (store-round-durations)]
      (with-system [{tx-log ::tx-log/local} config]
        (is (= 1 @(tx-log/submit tx-log [(create-patient-cmd "0")] nil))))

      ;; the halted system waited for the storer thread to exit, so its round
      ;; is observed by now
      (is (= (inc durations) (store-round-durations)))))

  (testing "a round that only waits for transaction data isn't observed"
    (let [durations (store-round-durations)]
      (with-system [{_ ::tx-log/local} config])

      (is (= durations (store-round-durations))))))

(deftest submit-duration-test
  (testing "the duration of an accepted submit is observed until its
            transaction data is stored"
    (with-system [{tx-log ::tx-log/local} config]
      (let [durations (submit-durations)]
        (is (= 1 @(tx-log/submit tx-log [(create-patient-cmd "0")] nil)))

        (is (= (inc durations) (submit-durations)))))))

(deftest store-loop-error-test
  (testing "an unexpected error while storing doesn't kill the store loop"
    (with-system [{tx-log ::tx-log/local} config]
      (testing "the affected submit completes exceptionally instead of hanging
                forever"
        (with-redefs [local/store-entries!
                      (fn [_ _ _] (throw (Error. "store-error")))]
          (given-failed-future (-> (tx-log/submit tx-log [(create-patient-cmd "0")] nil)
                                   (ac/or-timeout! 10 TimeUnit/SECONDS))
            ::anom/category := ::anom/fault
            ::anom/message := "store-error")))

      (testing "the store loop still stores subsequent transaction data"
        (is (= 2 @(-> (tx-log/submit tx-log [(create-patient-cmd "1")] nil)
                      (ac/or-timeout! 10 TimeUnit/SECONDS))))

        (given (first (tx-log/poll! tx-log 1 (time/millis 10)))
          :t := 2
          [:tx-cmds 0 :id] := "1")))))

(deftest submit-after-close-test
  (testing "submits are rejected with an unavailable anomaly after the
            transaction log is closed"
    (with-system [{tx-log ::tx-log/local} config]
      (.close ^AutoCloseable tx-log)

      (let [durations (submit-durations)
            rejected-future (tx-log/submit tx-log [(create-patient-cmd "0")] nil)]
        (if (ac/done? rejected-future)
          (given-failed-future rejected-future
            ::anom/category := ::anom/unavailable
            ::anom/message := "The transaction log is closed.")
          (is false "expected the submit future to be already completed with an unavailable anomaly"))

        (testing "the rejection isn't observed as submit duration"
          (is (= durations (submit-durations))))))))

(defn- submit-until-rejected
  "Submits transaction data until one submit is rejected, returning the futures
  of all accepted submits.

  A rejected submit completes immediately while an accepted one stays pending
  as long as the storer thread is blocked."
  [tx-log]
  (loop [futures []]
    (let [future (tx-log/submit tx-log [(create-patient-cmd "0")] nil)]
      (if (ac/done? future)
        futures
        (recur (conj futures future))))))

(deftest close-drain-test
  (testing "closing stores all transaction data buffered before the close"
    (with-system [{tx-log ::tx-log/local
                   kv-store ::blocking-kv-store}
                  blocking-kv-store-config]
      (let [{:keys [put-called release-put]} (meta kv-store)
            ;; the first submit blocks the storer thread in the kv-store
            future-1 (tx-log/submit tx-log [(create-patient-cmd "0")] nil)]
        (is (true? (deref put-called 1000 nil)))

        ;; two more submits are buffered while the storer thread is blocked
        (let [future-2 (tx-log/submit tx-log [(create-patient-cmd "1")] nil)
              future-3 (tx-log/submit tx-log [(create-patient-cmd "2")] nil)
              ;; the close blocks until the storer thread has terminated
              closing (ac/supply-async #(.close ^AutoCloseable tx-log))
              ;; submitting until a submit is rejected ensures that the close
              ;; has marked the transaction log as closed while the buffered
              ;; transaction data is still unstored
              futures (into [future-1 future-2 future-3]
                            (submit-until-rejected tx-log))]

          (testing "the transaction log is closed while transaction data is
                    still buffered"
            (given-failed-future (tx-log/submit tx-log [(create-patient-cmd "3")] nil)
              ::anom/category := ::anom/unavailable)
            (is (not-any? ac/done? futures)))

          ;; unblock the storer thread
          (deliver release-put nil)
          (is (nil? (deref closing 10000 ::timeout)))

          (testing "the buffered transaction data is stored before the close
                    returns, so the last `t` can be polled right away. The
                    futures of the submitters aren't done necessarily, because
                    they are completed asynchronously"
            (given (first (tx-log/poll! tx-log (count futures) (time/millis 10)))
              :t := (count futures)))

          (testing "all buffered submits complete with their `t`"
            (is (= (range 1 (inc (count futures)))
                   (mapv #(deref % 1000 ::timeout) futures)))))))))

(deftest close-idle-test
  (testing "closing wakes up the store loop waiting on the store signal"
    (with-system [{tx-log ::tx-log/local} config]
      ;; once the transaction data of the submit is stored, the store loop has
      ;; nothing left to do and waits on the store signal
      (is (= 1 @(tx-log/submit tx-log [(create-patient-cmd "0")] nil)))

      (testing "the close returns instead of waiting on the storer thread that
                waits on the store signal forever"
        (is (nil? (deref (ac/supply-async #(.close ^AutoCloseable tx-log))
                         10000 ::timeout)))))))

(deftest submit-completion-thread-test
  (testing "the future of a submit doesn't complete on the storer thread, so
            that the continuations of the submitter don't delay storing the
            transaction data of all other submitters"
    (with-system [{tx-log ::tx-log/local
                   kv-store ::blocking-kv-store}
                  blocking-kv-store-config]
      (let [{:keys [put-called release-put]} (meta kv-store)
            ;; the storer thread is blocked in the kv-store, so the continuation
            ;; is registered before the future of the submit is completed
            future (tx-log/submit tx-log [(create-patient-cmd "0")] nil)]
        (is (true? (deref put-called 1000 nil)))

        (let [thread-name (mtu/thread-name future)]
          (deliver release-put nil)

          (is (mtu/common-pool-thread? (deref thread-name 1000 "timeout")))))))

  (testing "the future of a failed submit doesn't complete on the storer thread
            either, because the store loop keeps running after a failure"
    (with-system [{tx-log ::tx-log/local} config]
      (let [store-called (promise)
            release-store (promise)]
        (with-redefs [local/store-entries!
                      (fn [_ _ _]
                        (deliver store-called true)
                        @release-store
                        (throw (Error. "store-error")))]
          ;; the storer thread is blocked in `store-entries!`, so the
          ;; continuation is registered before the future of the submit is
          ;; completed
          (let [future (tx-log/submit tx-log [(create-patient-cmd "0")] nil)]
            (is (true? (deref store-called 1000 nil)))

            (let [thread-name (mtu/thread-name future)]
              (deliver release-store nil)

              (is (mtu/common-pool-thread? (deref thread-name 1000 "timeout"))))))))))

(deftest submit-failure-gap-test
  (testing "a failed submit leaves a gap in `t`"
    (with-system [{tx-log ::tx-log/local} failing-once-kv-store-config]
      (given-failed-future (tx-log/submit tx-log [(create-patient-cmd "0")] nil)
        ::anom/message := "put-error")

      (is (= 2 @(tx-log/submit tx-log [(create-patient-cmd "1")] nil)))

      (testing "the last `t` is the last assigned `t`"
        (is (= 2 @(tx-log/last-t tx-log))))

      (testing "only the successful transaction is returned"
        (let [tx-data (tx-log/poll! tx-log 1 (time/millis 10))]
          (is (= 1 (count tx-data)))
          (given (first tx-data)
            :t := 2
            [:tx-cmds 0 :id] := "1")))))

  (testing "polling the gap waits for the timeout instead of returning no
            transaction data right away, because otherwise the poller would
            spin as long as no further transaction data is stored"
    (with-system [{tx-log ::tx-log/local} failing-once-kv-store-config]
      (given-failed-future (tx-log/submit tx-log [(create-patient-cmd "0")] nil)
        ::anom/message := "put-error")

      (testing "polling actually waits the timeout before returning no data"
        (let [start (System/nanoTime)]
          (is (empty? (tx-log/poll! tx-log 1 (time/millis 100))))
          (is (< 1e8 (- (System/nanoTime) start)))))

      (testing "the waiting poller is woken up by the next successfully stored
                transaction data"
        (let [tx-data (ac/supply-async #(tx-log/poll! tx-log 1 (time/seconds 10)))]
          (is (= 2 @(tx-log/submit tx-log [(create-patient-cmd "1")] nil)))

          (given (deref tx-data 10000 ::timeout)
            count := 1
            [0 :t] := 2))))))
