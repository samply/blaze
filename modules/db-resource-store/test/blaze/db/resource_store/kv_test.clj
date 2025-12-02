(ns blaze.db.resource-store.kv-test
  (:refer-clojure :exclude [hash])
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.db.kv.protocols :as kv-p]
   [blaze.db.kv.spec]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store-spec]
   [blaze.db.resource-store.kv :as rs-kv]
   [blaze.db.resource-store.kv-spec]
   [blaze.db.resource-store.kv.spec]
   [blaze.executors :as ex]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.writing-context]
   [blaze.metrics.spec]
   [blaze.module.test-util :as mtu :refer [given-failed-future given-failed-system with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

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

(def ^:private config
  {::rs/kv
   {:kv-store (ig/ref ::kv/mem)
    :parsing-context (ig/ref :blaze.fhir.parsing-context/resource-store)
    :writing-context (ig/ref :blaze.fhir/writing-context)
    :executor (ig/ref ::rs-kv/executor)}
   ::kv/mem {:column-families {}}
   [:blaze.fhir/parsing-context :blaze.fhir.parsing-context/resource-store]
   {:structure-definition-repo structure-definition-repo
    :fail-on-unknown-property false
    :include-summary-only true
    :use-regex false}
   :blaze.fhir/writing-context
   {:structure-definition-repo structure-definition-repo}
   ::rs-kv/executor {}})

(deftest init-test
  (testing "nil config"
    (given-failed-system {::rs/kv nil}
      :key := ::rs/kv
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {::rs/kv {}}
      :key := ::rs/kv
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :kv-store))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :parsing-context))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :writing-context))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :executor))))

  (testing "invalid kv-store"
    (given-failed-system (assoc-in config [::rs/kv :kv-store] ::invalid)
      :key := ::rs/kv
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/kv-store]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid parsing-context"
    (given-failed-system (assoc-in config [::rs/kv :parsing-context] ::invalid)
      :key := ::rs/kv
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.fhir/parsing-context]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid writing-context"
    (given-failed-system (assoc-in config [::rs/kv :writing-context] ::invalid)
      :key := ::rs/kv
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.fhir/writing-context]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid executor"
    (given-failed-system (assoc-in config [::rs/kv :executor] ::invalid)
      :key := ::rs/kv
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::rs-kv/executor]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest executor-init-test
  (testing "nil config"
    (given-failed-system {::rs-kv/executor nil}
      :key := ::rs-kv/executor
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "invalid num-threads"
    (given-failed-system {::rs-kv/executor {:num-threads ::invalid}}
      :key := ::rs-kv/executor
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::rs-kv/num-threads]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest resource-bytes-collector-init-test
  (with-system [{collector ::rs-kv/resource-bytes} {::rs-kv/resource-bytes {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(deftest duration-seconds-collector-init-test
  (with-system [{collector ::rs-kv/duration-seconds} {::rs-kv/duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(defmethod ig/init-key ::failing-kv-store [_ {:keys [msg] :as config}]
  (reify kv-p/KvStore
    (-get [_ _ hash]
      (when (or (nil? (:hash config))
                (= (hash/from-byte-buffer! (bb/wrap hash)) (:hash config)))
        (throw (Exception. ^String msg))))))

(defn- failing-kv-store-config
  ([msg]
   (failing-kv-store-config msg nil))
  ([msg hash]
   {::rs/kv
    {:kv-store (ig/ref ::failing-kv-store)
     :parsing-context (ig/ref :blaze.fhir.parsing-context/resource-store)
     :writing-context (ig/ref :blaze.fhir/writing-context)
     :executor (ig/ref ::rs-kv/executor)}
    ::failing-kv-store {:msg msg :hash hash}
    [:blaze.fhir/parsing-context :blaze.fhir.parsing-context/resource-store]
    {:structure-definition-repo structure-definition-repo
     :fail-on-unknown-property false
     :include-summary-only true
     :use-regex false}
    :blaze.fhir/writing-context
    {:structure-definition-repo structure-definition-repo}
    ::rs-kv/executor {}}))

(def ^:private error-msg "msg-154312")

(defn- put! [kv-store writing-context hash content]
  (kv/put! kv-store [[:default (hash/to-byte-array hash) (fhir-spec/write-cbor writing-context content)]]))

(deftest get-test
  (testing "success"
    (with-system [{store ::rs/kv kv-store ::kv/mem :blaze.fhir/keys [writing-context]} config]
      (put! kv-store writing-context (hash) {:fhir/type :fhir/Patient :id "0"})

      (given @(mtu/assoc-thread-name (rs/get store [:fhir/Patient (hash) :complete]))
        [meta :thread-name] :? mtu/common-pool-thread?
        :fhir/type := :fhir/Patient
        :id := "0")))

  (testing "parsing error"
    (with-system [{store ::rs/kv kv-store ::kv/mem} config]
      (kv/put! kv-store [[:default (hash/to-byte-array (hash)) (invalid-content)]])

      (given-failed-future (rs/get store [:fhir/Patient (hash) :complete])
        ::anom/category := ::anom/incorrect
        ::anom/message :# "Error while parsing resource content(.|\\s)*")))

  (testing "not-found"
    (with-system [{store ::rs/kv} config]
      (is (nil? @(rs/get store [:fhir/Patient (hash) :complete])))))

  (testing "error"
    (with-system [{store ::rs/kv} (failing-kv-store-config error-msg)]
      (given-failed-future (rs/get store [:fhir/Patient (hash) :complete])
        ::anom/category := ::anom/fault
        ::anom/message := error-msg))))

(deftest multi-get-test
  (testing "success"
    (testing "with one hash"
      (let [content {:fhir/type :fhir/Patient :id "0"}]
        (with-system [{store ::rs/kv kv-store ::kv/mem :blaze.fhir/keys [writing-context]} config]
          (put! kv-store writing-context (hash) content)

          (given @(mtu/assoc-thread-name (rs/multi-get store [[:fhir/Patient (hash) :complete]]))
            identity := {[:fhir/Patient (hash) :complete] content}))))

    (testing "with two hashes"
      (let [content-0 {:fhir/type :fhir/Patient :id "0"}
            content-1 {:fhir/type :fhir/Patient :id "1"}]
        (with-system [{store ::rs/kv kv-store ::kv/mem :blaze.fhir/keys [writing-context]} config]
          (put! kv-store writing-context (hash "0") content-0)
          (put! kv-store writing-context (hash "1") content-1)

          (testing "content matches"
            (given @(mtu/assoc-thread-name (rs/multi-get store [[:fhir/Patient (hash "0") :complete]
                                                                [:fhir/Patient (hash "1") :complete]]))
              [meta :thread-name] :? mtu/common-pool-thread?
              identity := {[:fhir/Patient (hash "0") :complete] content-0
                           [:fhir/Patient (hash "1") :complete] content-1}))))))

  (testing "parsing error"
    (let [hash (hash)]
      (with-system [{store ::rs/kv kv-store ::kv/mem} config]
        (kv/put! kv-store [[:default (hash/to-byte-array hash) (invalid-content)]])

        (given-failed-future (rs/multi-get store [[:fhir/Patient hash :complete]])
          ::anom/category := ::anom/incorrect
          ::anom/message :# "Error while parsing resource content(.|\\s)*"))))

  (testing "not-found"
    (with-system [{store ::rs/kv} config]

      (testing "result is empty"
        (given @(mtu/assoc-thread-name (rs/multi-get store [[:fhir/Patient (hash) :complete]]))
          [meta :thread-name] :? mtu/common-pool-thread?
          identity :? empty?))))

  (testing "error"
    (testing "with one hash"
      (with-system [{store ::rs/kv} (failing-kv-store-config error-msg)]
        (given-failed-future (rs/multi-get store [[:fhir/Patient (hash) :complete]])
          ::anom/category := ::anom/fault
          ::anom/message := error-msg)))

    (testing "with two hashes"
      (testing "failing on both"
        (with-system [{store ::rs/kv} (failing-kv-store-config error-msg)]
          (given-failed-future (rs/multi-get store [[:fhir/Patient (hash "0") :complete]
                                                    [:fhir/Patient (hash "1") :complete]])
            ::anom/category := ::anom/fault
            ::anom/message := error-msg)))

      (testing "failing on first"
        (with-system [{store ::rs/kv} (failing-kv-store-config error-msg (hash "0"))]
          (given-failed-future (rs/multi-get store [[:fhir/Patient (hash "0") :complete]
                                                    [:fhir/Patient (hash "1") :complete]])
            ::anom/category := ::anom/fault
            ::anom/message := error-msg)))

      (testing "failing on second"
        (with-system [{store ::rs/kv} (failing-kv-store-config error-msg (hash "1"))]
          (given-failed-future (rs/multi-get store [[:fhir/Patient (hash "0") :complete]
                                                    [:fhir/Patient (hash "1") :complete]])
            ::anom/category := ::anom/fault
            ::anom/message := error-msg))))))

(deftest put-test
  (let [content {:fhir/type :fhir/Patient :id "0"}]
    (with-system [{store ::rs/kv} config]
      @(rs/put! store {(hash) content})

      (is (= content @(rs/get store [:fhir/Patient (hash) :complete]))))))

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
