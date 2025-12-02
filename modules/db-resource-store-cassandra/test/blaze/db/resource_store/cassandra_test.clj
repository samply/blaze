(ns blaze.db.resource-store.cassandra-test
  (:refer-clojure :exclude [hash])
  (:require
   [blaze.async.comp :as ac]
   [blaze.byte-buffer :as bb]
   [blaze.cassandra :as cass]
   [blaze.cassandra-spec]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store.cassandra]
   [blaze.db.resource-store.cassandra.statement :as statement]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.writing-context]
   [blaze.module.test-util :as mtu :refer [given-failed-future given-failed-system with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [com.datastax.oss.driver.api.core
    ConsistencyLevel CqlSession DriverTimeoutException]
   [com.datastax.oss.driver.api.core.cql
    AsyncResultSet BoundStatement PreparedStatement Row SimpleStatement Statement]
   [com.datastax.oss.driver.api.core.metadata EndPoint Node]
   [com.datastax.oss.driver.api.core.servererrors WriteTimeoutException WriteType]
   [java.net InetSocketAddress]
   [java.nio ByteBuffer]
   [java.util.concurrent CompletionStage]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn hash [s]
  (assert (= 1 (count s)))
  (hash/from-hex (str/join (repeat 64 s))))

(def bound-get-statement (reify BoundStatement))

(defn row-with [idx bytes]
  (reify Row
    (^ByteBuffer getByteBuffer [_ ^int i]
      (assert (= idx i))
      (bb/wrap bytes))))

(defn resultset-with [row]
  (reify AsyncResultSet
    (one [_]
      row)))

(defn prepared-statement-with [bind-values bound-statement]
  (reify PreparedStatement
    (bind [_ values]
      (assert (= bind-values (vec values)))
      bound-statement)))

(defn invalid-content
  "`0xA1` is the start of a map with one entry."
  []
  (byte-array [0xA1]))

(def ^:private writing-context
  (:blaze.fhir/writing-context
   (ig/init
    {:blaze.fhir/writing-context
     {:structure-definition-repo structure-definition-repo}})))

(defn- write-cbor [value]
  (fhir-spec/write-cbor writing-context value))

(def ^:private config
  {::rs/cassandra
   {:parsing-context (ig/ref :blaze.fhir.parsing-context/resource-store)
    :writing-context (ig/ref :blaze.fhir/writing-context)}
   [:blaze.fhir/parsing-context :blaze.fhir.parsing-context/resource-store]
   {:structure-definition-repo structure-definition-repo
    :fail-on-unknown-property false
    :include-summary-only true
    :use-regex false}
   :blaze.fhir/writing-context
   {:structure-definition-repo structure-definition-repo}})

(deftest init-test
  (testing "nil config"
    (given-failed-system {::rs/cassandra nil}
      :key := ::rs/cassandra
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {::rs/cassandra {}}
      :key := ::rs/cassandra
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :parsing-context))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :writing-context))))

  (testing "invalid contact-points"
    (given-failed-system (assoc-in config [::rs/cassandra :contact-points] ::invalid)
      :key := ::rs/cassandra
      :reason := ::ig/build-failed-spec
      [:value :contact-points] := ::invalid
      [:cause-data ::s/problems 0 :path] := [:contact-points]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest get-test
  (testing "parsing error"
    (let [hash (hash "0")
          row (row-with 0 (invalid-content))
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(str hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                nil
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement statement]
              (assert (= bound-get-statement statement))
              (ac/completed-future (resultset-with row)))
            (close [_]))]

      (with-redefs [cass/session (fn [_] session)]
        (with-system [{store ::rs/cassandra} config]
          (given-failed-future (rs/get store [:fhir/Patient hash :complete])
            ::anom/message :# "Error while parsing resource content with hash `0000000000000000000000000000000000000000000000000000000000000000`:(.|\\s)*"
            :blaze.resource/hash := hash)))))

  (testing "not found"
    (let [hash (hash "0")
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(str hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                nil
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement statement]
              (assert (= bound-get-statement statement))
              (ac/completed-future (resultset-with nil)))
            (close [_]))]

      (with-redefs [cass/session (fn [_] session)]
        (with-system [{store ::rs/cassandra} config]
          (is (nil? @(rs/get store [:fhir/Patient hash :complete])))))))

  (testing "execute error"
    (let [hash (hash "0")
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(str hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                nil
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement _]
              (ac/failed-future (Exception. "msg-141754")))
            (close [_]))]

      (with-redefs [cass/session (fn [_] session)]
        (with-system [{store ::rs/cassandra} config]
          (given-failed-future (rs/get store [:fhir/Patient hash :complete])
            ::anom/category := ::anom/fault
            ::anom/message := "msg-141754"
            :blaze.resource/hash := hash)))))

  (testing "DriverTimeoutException"
    (let [hash (hash "0")
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(str hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                nil
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement _]
              (ac/failed-future (DriverTimeoutException. "msg-115452")))
            (close [_]))]

      (with-redefs [cass/session (fn [_] session)]
        (with-system [{store ::rs/cassandra} config]
          (given-failed-future (rs/get store [:fhir/Patient hash :complete])
            ::anom/category := ::anom/busy
            ::anom/message := "Cassandra msg-115452"
            :blaze.resource/hash := hash)))))

  (testing "success"
    (let [content {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate content)
          row (row-with 0 (write-cbor content))
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(str hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                nil
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement statement]
              (assert (= bound-get-statement statement))
              (ac/completed-future (resultset-with row)))
            (close [_]))]

      (with-redefs [cass/session (fn [_] session)]
        (with-system [{store ::rs/cassandra} config]
          (given @(mtu/assoc-thread-name (rs/get store [:fhir/Patient hash :complete]))
            [meta :thread-name] :? mtu/common-pool-thread?
            :fhir/type := :fhir/Patient
            :id := "0")))))

  (testing "success after one retry due to timeout"
    (let [content {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate content)
          row (row-with 0 (write-cbor content))
          throw-timeout? (volatile! true)
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(str hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                nil
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement _]
              (if @throw-timeout?
                (do (vreset! throw-timeout? false)
                    (ac/failed-future (DriverTimeoutException. "foo")))
                (ac/completed-future (resultset-with row))))
            (close [_]))]

      (with-redefs [cass/session (fn [_] session)]
        (with-system [{store ::rs/cassandra} config]
          (testing "content matches"
            (given @(mtu/assoc-thread-name (rs/get store [:fhir/Patient hash :complete]))
              [meta :thread-name] :? mtu/common-pool-thread?
              identity := content)))))))

(deftest multi-get-test
  (testing "not found"
    (let [hash (hash "0")
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(str hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                nil
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement statement]
              (assert (= bound-get-statement statement))
              (ac/completed-future (resultset-with nil)))
            (close [_]))]

      (with-redefs [cass/session (fn [_] session)]
        (with-system [{store ::rs/cassandra} config]
          (testing "result is empty"
            (given @(mtu/assoc-thread-name (rs/multi-get store [[:fhir/Patient hash :complete]]))
              [meta :thread-name] :? mtu/common-pool-thread?
              identity :? empty?))))))

  (testing "success"
    (let [content {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate content)
          row (row-with 0 (write-cbor content))
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(str hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                nil
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement statement]
              (assert (= bound-get-statement statement))
              (ac/completed-future (resultset-with row)))
            (close [_]))]

      (with-redefs [cass/session (fn [_] session)]
        (with-system [{store ::rs/cassandra} config]
          (given @(mtu/assoc-thread-name (rs/multi-get store [[:fhir/Patient hash :complete]]))
            [meta :thread-name] :? mtu/common-pool-thread?
            identity := {[:fhir/Patient hash :complete] content}))))))

(def bound-put-statement (reify BoundStatement))

(defn endpoint [host port]
  (reify EndPoint
    (resolve [_]
      (InetSocketAddress. ^String host (int port)))))

(defn node [endpoint]
  (reify Node
    (getEndPoint [_]
      endpoint)))

(deftest put-test
  (testing "execute error"
    (let [resource {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate resource)
          encoded-resource (bb/wrap (write-cbor resource))
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(str hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                (prepared-statement-with
                 [(str hash) encoded-resource]
                 bound-put-statement)
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement _]
              (ac/failed-future (Exception. "error-150216")))
            (close [_]))]

      (with-redefs [cass/session (fn [_] session)]
        (with-system [{store ::rs/cassandra} config]
          (given-failed-future (rs/put! store {hash resource})
            ::anom/message := "error-150216")))))

  (testing "DriverTimeoutException"
    (let [resource {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate resource)
          encoded-resource (bb/wrap (write-cbor resource))
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(str hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                (prepared-statement-with
                 [(str hash) encoded-resource]
                 bound-put-statement)
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement _]
              (ac/failed-future (DriverTimeoutException. "msg-123234")))
            (close [_]))]

      (with-redefs [cass/session (fn [_] session)]
        (with-system [{store ::rs/cassandra} config]
          (given-failed-future (rs/put! store {hash resource})
            ::anom/category := ::anom/busy
            ::anom/message := "Cassandra msg-123234"
            :blaze.resource/hash := hash
            :fhir/type := :fhir/Patient
            :blaze.resource/id := "0")))))

  (testing "WriteTimeoutException"
    (let [resource {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate resource)
          encoded-resource (bb/wrap (write-cbor resource))
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(str hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                (prepared-statement-with
                 [(str hash) encoded-resource]
                 bound-put-statement)
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement _]
              (ac/failed-future (WriteTimeoutException. nil ConsistencyLevel/TWO 1 2 WriteType/SIMPLE)))
            (close [_]))]

      (with-redefs [cass/session (fn [_] session)]
        (with-system [{store ::rs/cassandra} config]
          (given-failed-future (rs/put! store {hash resource})
            ::anom/category := ::anom/busy
            ::anom/message := "Cassandra timeout during SIMPLE write query at consistency TWO (2 replica were required but only 1 acknowledged the write)"
            :blaze.resource/hash := hash
            :fhir/type := :fhir/Patient
            :blaze.resource/id := "0")))))

  (testing "success"
    (let [resource {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate resource)
          encoded-resource (bb/wrap (write-cbor resource))
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(str hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                (prepared-statement-with
                 [(str hash) encoded-resource]
                 bound-put-statement)
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement _]
              (ac/completed-future nil))
            (close [_]))]

      (with-redefs [cass/session (fn [_] session)]
        (with-system [{store ::rs/cassandra} config]
          (is (nil? @(rs/put! store {hash resource}))))))))
