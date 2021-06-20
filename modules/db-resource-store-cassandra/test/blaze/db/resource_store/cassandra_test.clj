(ns blaze.db.resource-store.cassandra-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.byte-string :as bs]
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store.cassandra :as cass]
    [blaze.db.resource-store.cassandra.statement :as statement]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.log]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [cuerdas.core :as str]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [com.datastax.oss.driver.api.core CqlSession DriverTimeoutException ConsistencyLevel]
    [com.datastax.oss.driver.api.core.cql PreparedStatement BoundStatement
                                          Statement Row AsyncResultSet SimpleStatement]
    [com.datastax.oss.driver.api.core.metadata Node EndPoint]
    [com.datastax.oss.driver.api.core.servererrors WriteTimeoutException WriteType]
    [java.net InetSocketAddress]
    [java.nio ByteBuffer]
    [java.util.concurrent CompletionStage])
  (:refer-clojure :exclude [hash]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn hash [s]
  (assert (= 1 (count s)))
  (bs/from-hex (str/repeat s 64)))


(defn system
  ([session]
   (system session {}))
  ([session config]
   (with-redefs
     [cass/session (fn [_ _] session)]
     (ig/init {::rs/cassandra config}))))


(def bound-get-statement (reify BoundStatement))


(defn row-with [idx bytes]
  (reify Row
    (^ByteBuffer getByteBuffer [_ ^int i]
      (when-not (= idx i)
        (throw (Error.)))
      (ByteBuffer/wrap bytes))))


(defn resultset-with [row]
  (reify AsyncResultSet
    (one [_]
      row)))


(defn prepared-statement-with [bind-values bound-statement]
  (reify PreparedStatement
    (bind [_ values]
      (when-not (= bind-values (vec values))
        (throw (Error.)))
      bound-statement)))


(defn invalid-content
  "`0xA1` is the start of a map with one entry."
  []
  (byte-array [0xA1]))


(deftest init-test
  (testing "invalid contact-points"
    (try
      (::rs/cassandra (system nil {:contact-points "localhost"}))
      (catch Exception e
        (given (ex-data e)
          :reason := ::ig/build-failed-spec
          :key := ::rs/cassandra
          [:value :contact-points] := "localhost"
          [:explain ::s/problems 0 :path] := [:contact-points]
          [:explain ::s/problems 0 :val] := "localhost")))))


(deftest get-test
  (testing "parsing error"
    (let [hash (hash "0")
          row (row-with 0 (invalid-content))
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(bs/hex hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                nil
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement statement]
              (when-not (= bound-get-statement statement)
                (throw (Error.)))
              (ac/completed-future (resultset-with row)))
            (close [_]))
          {store ::rs/cassandra :as system} (system session)]

      (try
        @(rs/get store hash)
        (catch Exception e
          (is (str/starts-with? (ex-message (ex-cause e))
                                "Error while parsing resource content")))
        (finally
          (ig/halt! system)))))

  (testing "not found"
    (let [hash (hash "0")
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(bs/hex hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                nil
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement statement]
              (when-not (= bound-get-statement statement)
                (throw (Error.)))
              (ac/completed-future (resultset-with nil)))
            (close [_]))
          {store ::rs/cassandra :as system} (system session)]

      (try
        (is (nil? @(rs/get store hash)))
        (finally
          (ig/halt! system)))))

  (testing "execute error"
    (let [hash (hash "0")
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(bs/hex hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                nil
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement _]
              (ac/failed-future (Exception. "msg-141754")))
            (close [_]))
          {store ::rs/cassandra :as system} (system session)]

      (try
        @(rs/get store hash)
        (catch Exception e
          (given (ex-cause e)
            ex-message := "msg-141754"
            [ex-data ::anom/category] := ::anom/fault
            [ex-data :blaze.resource/hash] := hash))
        (finally
          (ig/halt! system)))))

  (testing "DriverTimeoutException"
    (let [hash (hash "0")
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(bs/hex hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                nil
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement _]
              (ac/failed-future (DriverTimeoutException. "msg-115452")))
            (close [_]))
          {store ::rs/cassandra :as system} (system session)]

      (try
        @(rs/get store hash)
        (catch Exception e
          (given (ex-cause e)
            ex-message := "Cassandra msg-115452"
            [ex-data ::anom/category] := ::anom/busy
            [ex-data :blaze.resource/hash] := hash))
        (finally
          (ig/halt! system)))))

  (testing "success"
    (let [content {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate content)
          row (row-with 0 (fhir-spec/unform-cbor content))
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(bs/hex hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                nil
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement statement]
              (when-not (= bound-get-statement statement)
                (throw (Error.)))
              (ac/completed-future (resultset-with row)))
            (close [_]))
          {store ::rs/cassandra :as system} (system session)]

      (try
        (is (= content @(rs/get store hash)))
        (finally
          (ig/halt! system)))))

  (testing "success after one retry due to timeout"
    (let [content {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate content)
          row (row-with 0 (fhir-spec/unform-cbor content))
          throw-timeout? (volatile! true)
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(bs/hex hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                nil
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement statement]
              (if @throw-timeout?
                (do (vreset! throw-timeout? false)
                    (ac/failed-future (DriverTimeoutException. "foo")))
                (ac/completed-future (resultset-with row))))
            (close [_]))
          {store ::rs/cassandra :as system} (system session)]

      (try
        (is (= content @(rs/get store hash)))
        (finally
          (ig/halt! system))))))


(deftest multi-get-test
  (testing "not found"
    (let [hash (hash "0")
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(bs/hex hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                nil
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement statement]
              (when-not (= bound-get-statement statement)
                (throw (Error.)))
              (ac/completed-future (resultset-with nil)))
            (close [_]))
          {store ::rs/cassandra :as system} (system session)]

      (try
        (is (empty? @(rs/multi-get store [hash])))
        (finally
          (ig/halt! system)))))

  (testing "success"
    (let [content {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate content)
          row (row-with 0 (fhir-spec/unform-cbor content))
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(bs/hex hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                nil
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement statement]
              (when-not (= bound-get-statement statement)
                (throw (Error.)))
              (ac/completed-future (resultset-with row)))
            (close [_]))
          {store ::rs/cassandra :as system} (system session)]

      (try
        (is (= {hash content} @(rs/multi-get store [hash])))
        (finally
          (ig/halt! system))))))


(def bound-put-statement (reify BoundStatement))


(defn endpoint [host port]
  (reify EndPoint
    (resolve [_]
      (InetSocketAddress. ^String host ^int port))))


(defn node [endpoint]
  (reify Node
    (getEndPoint [_]
      endpoint)))


(deftest put-test
  (testing "execute error"
    (let [resource {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate resource)
          encoded-resource (ByteBuffer/wrap (fhir-spec/unform-cbor resource))
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(bs/hex hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                (prepared-statement-with
                  [(bs/hex hash) encoded-resource]
                  bound-put-statement)
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement _]
              (ac/failed-future (Exception. "error-150216")))
            (close [_]))
          {store ::rs/cassandra :as system} (system session)]

      (try
        @(rs/put store {hash resource})
        (catch Exception e
          (is (= "error-150216" (ex-message (ex-cause e)))))
        (finally
          (ig/halt! system)))))

  (testing "DriverTimeoutException"
    (let [resource {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate resource)
          encoded-resource (ByteBuffer/wrap (fhir-spec/unform-cbor resource))
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(bs/hex hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                (prepared-statement-with
                  [(bs/hex hash) encoded-resource]
                  bound-put-statement)
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement _]
              (ac/failed-future (DriverTimeoutException. "msg-123234")))
            (close [_]))
          {store ::rs/cassandra :as system} (system session)]

      (try
        @(rs/put store {hash resource})
        (catch Exception e
          (given (ex-cause e)
            ex-message := "Cassandra msg-123234"
            [ex-data ::anom/category] := ::anom/busy
            [ex-data :blaze.resource/hash] := hash
            [ex-data :fhir/type] := :fhir/Patient
            [ex-data :blaze.resource/id] := "0"))
        (finally
          (ig/halt! system)))))

  (testing "WriteTimeoutException"
    (let [resource {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate resource)
          encoded-resource (ByteBuffer/wrap (fhir-spec/unform-cbor resource))
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(bs/hex hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                (prepared-statement-with
                  [(bs/hex hash) encoded-resource]
                  bound-put-statement)
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement _]
              (ac/failed-future (WriteTimeoutException. nil ConsistencyLevel/TWO 1 2 WriteType/SIMPLE)))
            (close [_]))
          {store ::rs/cassandra :as system} (system session)]

      (try
        @(rs/put store {hash resource})
        (catch Exception e
          (given (ex-cause e)
            ex-message := "Cassandra timeout during SIMPLE write query at consistency TWO (2 replica were required but only 1 acknowledged the write)"
            [ex-data ::anom/category] := ::anom/busy
            [ex-data :blaze.resource/hash] := hash
            [ex-data :fhir/type] := :fhir/Patient
            [ex-data :blaze.resource/id] := "0"))
        (finally
          (ig/halt! system)))))

  (testing "success"
    (let [resource {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate resource)
          encoded-resource (ByteBuffer/wrap (fhir-spec/unform-cbor resource))
          session
          (reify CqlSession
            (^PreparedStatement prepare [_ ^SimpleStatement statement]
              (cond
                (= statement/get-statement statement)
                (prepared-statement-with [(bs/hex hash)] bound-get-statement)
                (= (statement/put-statement "TWO") statement)
                (prepared-statement-with
                  [(bs/hex hash) encoded-resource]
                  bound-put-statement)
                :else
                (throw (Error.))))
            (^CompletionStage executeAsync [_ ^Statement _]
              (ac/completed-future nil))
            (close [_]))
          {store ::rs/cassandra :as system} (system session)]

      (try
        (is (nil? @(rs/put store {hash resource})))
        (finally
          (ig/halt! system))))))
