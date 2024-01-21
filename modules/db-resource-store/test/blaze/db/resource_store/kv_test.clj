(ns blaze.db.resource-store.kv-test
  (:refer-clojure :exclude [hash])
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store-spec]
   [blaze.db.resource-store.kv :as rs-kv]
   [blaze.db.resource-store.kv-spec]
   [blaze.executors :as ex]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.test-util :refer [given-failed-future]]
   [blaze.metrics.spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [jsonista.core :as j]
   [taoensso.timbre :as log])
  (:import
   [com.fasterxml.jackson.dataformat.cbor CBORFactory]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn- hash
  ([]
   (hash "0"))
  ([s]
   (assert (= 1 (count s)))
   (hash/from-hex (str/join (repeat 64 s)))))

(defn- invalid-content
  "`0xA1` is the start of a map with one entry."
  []
  (byte-array [0xA1]))

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {::rs/kv nil})
      :key := ::rs/kv
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::rs/kv {}})
      :key := ::rs/kv
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :kv-store))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :executor))))

  (testing "invalid kv-store"
    (given-thrown (ig/init {::rs/kv {:kv-store ::invalid}})
      :key := ::rs/kv
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :executor))
      [:explain ::s/problems 1 :pred] := `kv/store?
      [:explain ::s/problems 1 :val] := ::invalid))

  (testing "invalid executor"
    (given-thrown (ig/init {::rs/kv {:executor ::invalid}})
      :key := ::rs/kv
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :kv-store))
      [:explain ::s/problems 1 :pred] := `ex/executor?
      [:explain ::s/problems 1 :val] := ::invalid)))

(deftest executor-init-test
  (testing "nil config"
    (given-thrown (ig/init {::rs-kv/executor nil})
      :key := ::rs-kv/executor
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "invalid num-threads"
    (given-thrown (ig/init {::rs-kv/executor {:num-threads ::invalid}})
      :key := ::rs-kv/executor
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `nat-int?
      [:explain ::s/problems 0 :val] := ::invalid)))

(deftest resource-bytes-collector-init-test
  (with-system [{collector ::rs-kv/resource-bytes} {::rs-kv/resource-bytes {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(deftest duration-seconds-collector-init-test
  (with-system [{collector ::rs-kv/duration-seconds} {::rs-kv/duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(def ^:private system
  {::rs/kv
   {:kv-store (ig/ref ::kv/mem)
    :executor (ig/ref ::rs-kv/executor)}
   ::kv/mem {:column-families {}}
   ::rs-kv/executor {}})

(defmethod ig/init-key ::failing-kv-store [_ {:keys [msg] :as config}]
  (reify kv/KvStore
    (-get [_ _ hash]
      (when (or (nil? (:hash config))
                (= (hash/from-byte-buffer! (bb/wrap hash)) (:hash config)))
        (throw (Exception. ^String msg))))))

(defn- failing-kv-store-system
  ([msg]
   (failing-kv-store-system msg nil))
  ([msg hash]
   {::failing-kv-store {:msg msg :hash hash}
    ::rs-kv/executor {}
    ::rs/kv
    {:kv-store (ig/ref ::failing-kv-store)
     :executor (ig/ref ::rs-kv/executor)}}))

(def ^:private cbor-object-mapper
  (j/object-mapper
   {:factory (CBORFactory.)
    :decode-key-fn true}))

(def ^:private error-msg "msg-154312")

(defn- put! [kv-store hash content]
  (kv/put! kv-store [[:default (hash/to-byte-array hash) (fhir-spec/unform-cbor content)]]))

(deftest get-test
  (testing "success"
    (let [content {:fhir/type :fhir/Patient :id "0"}]
      (with-system [{store ::rs/kv kv-store ::kv/mem} system]
        (put! kv-store (hash) content)

        (is (= content @(rs/get store (hash)))))))

  (testing "parsing error"
    (with-system [{store ::rs/kv kv-store ::kv/mem} system]
      (kv/put! kv-store [[:default (hash/to-byte-array (hash)) (invalid-content)]])

      (given-failed-future (rs/get store (hash))
        ::anom/category := ::anom/incorrect
        ::anom/message :# "Error while parsing resource content(.|\\s)*")))

  (testing "conforming error"
    (with-system [{store ::rs/kv kv-store ::kv/mem} system]
      (kv/put! kv-store [[:default (hash/to-byte-array (hash)) (j/write-value-as-bytes {} cbor-object-mapper)]])

      (given-failed-future (rs/get store (hash))
        ::anom/category := ::anom/fault
        ::anom/message := (format "Error while conforming resource content with hash `%s`." (hash)))))

  (testing "not-found"
    (with-system [{store ::rs/kv} system]
      (is (nil? @(rs/get store (hash))))))

  (testing "error"
    (with-system [{store ::rs/kv} (failing-kv-store-system error-msg)]
      (given-failed-future (rs/get store (hash))
        ::anom/category := ::anom/fault
        ::anom/message := error-msg))))

(deftest multi-get-test
  (testing "success"
    (testing "with one hash"
      (let [content {:fhir/type :fhir/Patient :id "0"}]
        (with-system [{store ::rs/kv kv-store ::kv/mem} system]
          (put! kv-store (hash) content)

          (is (= {(hash) content} @(rs/multi-get store [(hash)]))))))

    (testing "with two hashes"
      (let [content-0 {:fhir/type :fhir/Patient :id "0"}
            content-1 {:fhir/type :fhir/Patient :id "1"}]
        (with-system [{store ::rs/kv kv-store ::kv/mem} system]
          (put! kv-store (hash "0") content-0)
          (put! kv-store (hash "1") content-1)

          (is (= {(hash "0") content-0 (hash "1") content-1}
                 @(rs/multi-get store [(hash "0") (hash "1")])))))))

  (testing "parsing error"
    (let [hash (hash)]
      (with-system [{store ::rs/kv kv-store ::kv/mem} system]
        (kv/put! kv-store [[:default (hash/to-byte-array hash) (invalid-content)]])

        (given-failed-future (rs/multi-get store [hash])
          ::anom/category := ::anom/incorrect
          ::anom/message :# "Error while parsing resource content(.|\\s)*"))))

  (testing "not-found"
    (with-system [{store ::rs/kv} system]
      (is (= {} @(rs/multi-get store [(hash)])))))

  (testing "error"
    (testing "with one hash"
      (with-system [{store ::rs/kv} (failing-kv-store-system error-msg)]
        (given-failed-future (rs/multi-get store [(hash)])
          ::anom/category := ::anom/fault
          ::anom/message := error-msg)))

    (testing "with two hashes"
      (testing "failing on both"
        (with-system [{store ::rs/kv} (failing-kv-store-system error-msg)]
          (given-failed-future (rs/multi-get store [(hash "0") (hash "1")])
            ::anom/category := ::anom/fault
            ::anom/message := error-msg)))

      (testing "failing on first"
        (with-system [{store ::rs/kv} (failing-kv-store-system error-msg (hash "0"))]
          (given-failed-future (rs/multi-get store [(hash "0") (hash "1")])
            ::anom/category := ::anom/fault
            ::anom/message := error-msg)))

      (testing "failing on second"
        (with-system [{store ::rs/kv} (failing-kv-store-system error-msg (hash "1"))]
          (given-failed-future (rs/multi-get store [(hash "0") (hash "1")])
            ::anom/category := ::anom/fault
            ::anom/message := error-msg))))))

(deftest put-test
  (let [content {:fhir/type :fhir/Patient :id "0"}]
    (with-system [{store ::rs/kv} system]
      @(rs/put! store {(hash) content})

      (is (= content @(rs/get store (hash)))))))

(deftest executor-shutdown-timeout-test
  (let [{::rs-kv/keys [executor] :as system} (ig/init {::rs-kv/executor {}})]

    ;; will produce a timeout, because the function runs 11 seconds
    (ex/execute! executor #(Thread/sleep 11000))

    ;; ensure that the function is called before the scheduler is halted
    (Thread/sleep 100)

    (ig/halt! system)

    ;; the scheduler is shut down
    (is (ex/shutdown? executor))

    ;; but it isn't terminated yet
    (is (not (ex/terminated? executor)))))
