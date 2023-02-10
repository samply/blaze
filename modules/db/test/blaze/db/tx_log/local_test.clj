(ns blaze.db.tx-log.local-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.byte-string :as bs]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem]
    [blaze.db.kv.mem-spec]
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store.kv :as rs-kv]
    [blaze.db.tx-log :as tx-log]
    [blaze.db.tx-log.local]
    [blaze.db.tx-log.local-spec]
    [blaze.db.tx-log.local.codec :as codec]
    [blaze.db.tx-log.local.codec-spec]
    [blaze.db.tx-log.spec]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.log]
    [blaze.test-util :as tu :refer [given-failed-future given-thrown with-system]]
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
(tu/init-fhir-specs)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def ^:private cbor-object-mapper
  (j/object-mapper
    {:factory (CBORFactory.)
     :decode-key-fn true
     :modules [bs/object-mapper-module]}))


(def patient-0 {:fhir/type :fhir/Patient :id "0"})
(def patient-hash-0 (hash/generate patient-0))
(def observation-0 {:fhir/type :fhir/Observation :id "0"})
(def observation-hash-0 (hash/generate observation-0))


(defn invalid-cbor-content
  "`0xA1` is the start of a map with one entry."
  []
  (byte-array [0xA1]))


(defmethod ig/init-key ::failing-kv-store [_ _]
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
            AutoCloseable
            (close [_])))
        AutoCloseable
        (close [_])))
    (-put [_ _ _]
      (throw (Exception. "put-error")))))


(def system
  {::tx-log/local
   {:kv-store (ig/ref :blaze.db/transaction-kv-store)
    :resource-store (ig/ref ::rs/kv)
    :clock (ig/ref :blaze.test/clock)}

   [::kv/mem :blaze.db/transaction-kv-store]
   {:column-families {}}

   ::rs/kv
   {:kv-store (ig/ref :blaze.db/resource-kv-store)
    :executor (ig/ref ::rs-kv/executor)}

   [::kv/mem :blaze.db/resource-kv-store]
   {:column-families {}}

   ::rs-kv/executor {}

   :blaze.test/clock {}})


(defn- assoc-kv-store-init-data [system init-data]
  (assoc-in system [[::kv/mem :blaze.db/transaction-kv-store] :init-data] init-data))


(defn- assoc-resource-store-init-data [system init-data]
  (assoc-in system [[::kv/mem :blaze.db/resource-kv-store] :init-data] init-data))


(def failing-kv-store-system
  (-> (assoc-in system [::tx-log/local :kv-store] (ig/ref ::failing-kv-store))
      (assoc ::failing-kv-store {})))


(defmethod ig/init-key ::resource-store-failing-on-put [_ _]
  (reify
    rs/ResourceStore
    (-put [_ _]
      (ac/completed-future
        {::anom/category ::anom/fault
         ::anom/message "resource-store-put-error"}))))


(def resource-store-failing-on-put-system
  (-> (assoc-in system [::tx-log/local :resource-store] (ig/ref ::resource-store-failing-on-put))
      (assoc ::resource-store-failing-on-put {})))


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {::tx-log/local nil})
      :key := ::tx-log/local
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::tx-log/local {}})
      :key := ::tx-log/local
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :kv-store))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :resource-store))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :clock)))))


(defn- write-cbor [x]
  (j/write-value-as-bytes x cbor-object-mapper))


(deftest tx-log-test
  (testing "an empty transaction log"
    (with-system [{tx-log ::tx-log/local} system]
      (testing "the last `t` is zero"
        (is (zero? @(tx-log/last-t tx-log))))

      (testing "has no transaction data"
        (with-open [queue (tx-log/new-queue tx-log 1)]
          (is (empty? (tx-log/poll! queue (time/millis 10))))))))

  (testing "an already filled transaction log"
    (with-system [{tx-log ::tx-log/local}
                  (-> system
                      (assoc-kv-store-init-data
                        [[(codec/encode-key 1)
                          (codec/encode-tx-data
                            (Instant/ofEpochSecond 0)
                            [{:op "create" :type "Patient" :id "0"
                              :hash patient-hash-0}
                             {:op "delete" :type "Patient" :id "1"}])]])
                      (assoc-resource-store-init-data
                        [[(hash/to-byte-array patient-hash-0)
                          (fhir-spec/unform-cbor patient-0)]]))]

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
            [:tx-cmds 0 :hash] := patient-hash-0
            [:tx-cmds 0 :resource ac/join] := patient-0
            [:tx-cmds 1 :op] := "delete"
            [:tx-cmds 1 :type] := "Patient"
            [:tx-cmds 1 :id] := "1"
            [:tx-cmds 1 :hash] := nil
            [:tx-cmds 1 :resource] := nil)))))

  (testing "with one submitted command in one transaction"
    (with-system [{tx-log ::tx-log/local} system]
      @(tx-log/submit
         tx-log
         [{:op "create" :type "Patient" :id "0" :resource patient-0}])

      (with-open [queue (tx-log/new-queue tx-log 1)]
        (given (first (tx-log/poll! queue (time/millis 10)))
          :t := 1
          :instant := (Instant/ofEpochSecond 0)
          [:tx-cmds 0 :op] := "create"
          [:tx-cmds 0 :type] := "Patient"
          [:tx-cmds 0 :id] := "0"
          [:tx-cmds 0 :hash] := patient-hash-0
          [:tx-cmds 0 :resource ac/join] := patient-0))))

  (testing "with two submitted commands in two transactions"
    (with-system [{tx-log ::tx-log/local} system]
      @(tx-log/submit
         tx-log
         [{:op "create" :type "Patient" :id "0" :resource patient-0}])
      @(tx-log/submit
         tx-log
         [{:op "create" :type "Observation" :id "0"
           :resource observation-0
           :refs [["Patient" "0"]]}])

      (with-open [queue (tx-log/new-queue tx-log 1)]
        (given (second (tx-log/poll! queue (time/millis 10)))
          :t := 2
          :instant := (Instant/ofEpochSecond 0)
          [:tx-cmds 0 :op] := "create"
          [:tx-cmds 0 :type] := "Observation"
          [:tx-cmds 0 :id] := "0"
          [:tx-cmds 0 :hash] := observation-hash-0
          [:tx-cmds 0 :resource ac/join] := observation-0
          [:tx-cmds 0 :refs] := [["Patient" "0"]]))))

  (testing "with one submitted command after opening the queue"
    (with-system [{tx-log ::tx-log/local} system]
      (with-open [queue (tx-log/new-queue tx-log 1)]
        @(tx-log/submit
           tx-log
           [{:op "create" :type "Patient" :id "0" :resource patient-0}])

        (given (first (tx-log/poll! queue (time/millis 10)))
          :t := 1
          :instant := (Instant/ofEpochSecond 0)
          [:tx-cmds 0 :op] := "create"
          [:tx-cmds 0 :type] := "Patient"
          [:tx-cmds 0 :id] := "0"
          [:tx-cmds 0 :hash] := patient-hash-0
          [:tx-cmds 0 :resource ac/join] := patient-0))))

  (testing "with invalid transaction data"
    (testing "with invalid key"
      (with-system [{tx-log ::tx-log/local}
                    (assoc-kv-store-init-data
                      system
                      [[(byte-array 0) (byte-array 0)]])]

        (testing "the invalid transaction data is ignored"
          (with-open [queue (tx-log/new-queue tx-log 1)]
            (is (empty? (tx-log/poll! queue (time/millis 10))))))))

    (testing "with invalid key followed by valid entry"
      (with-system [{tx-log ::tx-log/local}
                    (-> system
                        (assoc-kv-store-init-data
                          [[(byte-array 0) (byte-array 0)]
                           [(codec/encode-key 1)
                            (codec/encode-tx-data
                              (Instant/ofEpochSecond 0)
                              [{:op "create" :type "Patient" :id "0"
                                :hash patient-hash-0}])]])
                        (assoc-resource-store-init-data
                          [[(hash/to-byte-array patient-hash-0)
                            (fhir-spec/unform-cbor patient-0)]]))]

        (testing "the invalid transaction data is ignored"
          (with-open [queue (tx-log/new-queue tx-log 0)]
            (given (first (tx-log/poll! queue (time/millis 10)))
              :t := 1
              :instant := (Instant/ofEpochSecond 0)
              [:tx-cmds 0 :op] := "create"
              [:tx-cmds 0 :type] := "Patient"
              [:tx-cmds 0 :id] := "0"
              [:tx-cmds 0 :hash] := patient-hash-0
              [:tx-cmds 0 :resource ac/join] := patient-0)))))

    (testing "with two invalid keys followed by valid entry"
      (with-system [{tx-log ::tx-log/local}
                    (-> system
                        (assoc-kv-store-init-data
                          [[(byte-array 0) (byte-array 0)]
                           [(byte-array 1) (byte-array 0)]
                           [(codec/encode-key 1)
                            (codec/encode-tx-data
                              (Instant/ofEpochSecond 0)
                              [{:op "create" :type "Patient" :id "0"
                                :hash patient-hash-0}])]])
                        (assoc-resource-store-init-data
                          [[(hash/to-byte-array patient-hash-0)
                            (fhir-spec/unform-cbor patient-0)]]))]

        (testing "the invalid transaction data is ignored"
          (with-open [queue (tx-log/new-queue tx-log 0)]
            (given (first (tx-log/poll! queue (time/millis 10)))
              :t := 1
              :instant := (Instant/ofEpochSecond 0)
              [:tx-cmds 0 :op] := "create"
              [:tx-cmds 0 :type] := "Patient"
              [:tx-cmds 0 :id] := "0"
              [:tx-cmds 0 :hash] := patient-hash-0
              [:tx-cmds 0 :resource ac/join] := patient-0)))))

    (testing "with empty value"
      (with-system [{tx-log ::tx-log/local}
                    (assoc-kv-store-init-data
                      system
                      [[(byte-array Long/BYTES) (byte-array 0)]])]

        (testing "the invalid transaction data is ignored"
          (with-open [queue (tx-log/new-queue tx-log 1)]
            (is (empty? (tx-log/poll! queue (time/millis 10))))))))

    (testing "with invalid cbor value"
      (with-system [{tx-log ::tx-log/local}
                    (assoc-kv-store-init-data
                      system
                      [[(byte-array Long/BYTES) (invalid-cbor-content)]])]

        (testing "the invalid transaction data is ignored"
          (with-open [queue (tx-log/new-queue tx-log 1)]
            (is (empty? (tx-log/poll! queue (time/millis 10))))))))

    (testing "with invalid instant value"
      (with-system [{tx-log ::tx-log/local}
                    (assoc-kv-store-init-data
                      system
                      [[(byte-array Long/BYTES) (write-cbor {:instant ""})]])]

        (testing "the invalid transaction data is ignored"
          (with-open [queue (tx-log/new-queue tx-log 1)]
            (is (empty? (tx-log/poll! queue (time/millis 10))))))))

    (testing "with invalid tx-cmd value"
      (with-system [{tx-log ::tx-log/local}
                    (assoc-kv-store-init-data
                      system
                      [[(byte-array Long/BYTES) (write-cbor {:tx-cmds [{}]})]])]

        (testing "the invalid transaction data is ignored"
          (with-open [queue (tx-log/new-queue tx-log 1)]
            (is (empty? (tx-log/poll! queue (time/millis 10))))))))

    (testing "with failing kv-store"
      (with-system [{tx-log ::tx-log/local} failing-kv-store-system]
        (-> (given-failed-future
              (tx-log/submit
                tx-log
                [{:op "create" :type "Patient" :id "0" :resource patient-0}])
              ::anom/message := "put-error"))

        (with-open [queue (tx-log/new-queue tx-log 1)]
          (is (empty? (tx-log/poll! queue (time/millis 10)))))))

    (testing "with failing resource-store"
      (with-system [{tx-log ::tx-log/local} resource-store-failing-on-put-system]
        (-> (given-failed-future
              (tx-log/submit
                tx-log
                [{:op "create" :type "Patient" :id "0" :resource patient-0}])
              ::anom/category := ::anom/fault
              ::anom/message := "resource-store-put-error"))

        (with-open [queue (tx-log/new-queue tx-log 1)]
          (is (empty? (tx-log/poll! queue (time/millis 10)))))))))
