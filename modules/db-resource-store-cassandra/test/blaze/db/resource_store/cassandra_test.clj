(ns blaze.db.resource-store.cassandra-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.byte-string :as bs]
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store.cassandra :as cass]
    [blaze.db.resource-store.cassandra-spec]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.log]
    [cheshire.core :as cheshire]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cuerdas.core :as str]
    [taoensso.timbre :as log])
  (:import
    [com.datastax.oss.driver.api.core CqlSession]
    [com.datastax.oss.driver.api.core.cql PreparedStatement BoundStatement Statement Row AsyncResultSet]
    [com.datastax.oss.driver.api.core.metadata Node EndPoint]
    [java.net InetSocketAddress]
    [java.nio ByteBuffer]
    [java.util.concurrent CompletionStage])
  (:refer-clojure :exclude [get hash]))


(defn fixture [f]
  (st/instrument)
  (log/with-level :trace (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn hash [s]
  (assert (= 1 (count s)))
  (bs/from-hex (str/repeat s 64)))


(defn store [session get-statement put-statement]
  (cass/->CassandraResourceStore session get-statement put-statement))


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


(deftest get
  (testing "parsing error"
    (let [hash (hash "0")
          row (row-with 0 (invalid-content))
          session
          (reify CqlSession
            (^CompletionStage executeAsync [_ ^Statement statement]
              (when-not (= bound-get-statement statement)
                (throw (Error.)))
              (ac/completed-future (resultset-with row))))
          get-statement
          (prepared-statement-with [(str hash)] bound-get-statement)
          store (store session get-statement nil)]

      (try
        @(rs/get store hash)
        (catch Exception e
          (is (str/starts-with? (ex-message (ex-cause e))
                                "Error while parsing resource content"))))))

  (testing "not found"
    (let [hash (hash "0")
          session
          (reify CqlSession
            (^CompletionStage executeAsync [_ ^Statement statement]
              (when-not (= bound-get-statement statement)
                (throw (Error.)))
              (ac/completed-future (resultset-with nil))))
          get-statement
          (prepared-statement-with [(str hash)] bound-get-statement)
          store (store session get-statement nil)]

      (is (nil? @(rs/get store hash)))))

  (testing "execute error"
    (let [hash (hash "0")
          session
          (reify CqlSession
            (^CompletionStage executeAsync [_ ^Statement _]
              (ac/failed-future (Exception. "msg-141754"))))
          get-statement
          (prepared-statement-with [(str hash)] bound-get-statement)
          store (store session get-statement nil)]

      (try
        @(rs/get store hash)
        (catch Exception e
          (is (= "msg-141754" (ex-message (ex-cause e))))))))

  (testing "success"
    (let [content {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate content)
          row (row-with 0 (cheshire/generate-cbor (fhir-spec/unform-cbor content)))
          session
          (reify CqlSession
            (^CompletionStage executeAsync [_ ^Statement statement]
              (when-not (= bound-get-statement statement)
                (throw (Error.)))
              (ac/completed-future (resultset-with row))))
          get-statement
          (prepared-statement-with [(str hash)] bound-get-statement)
          store (store session get-statement nil)]

      (is (= content @(rs/get store hash))))))


(deftest multi-get
  (testing "not found"
    (let [hash (hash "0")
          session
          (reify CqlSession
            (^CompletionStage executeAsync [_ ^Statement statement]
              (when-not (= bound-get-statement statement)
                (throw (Error.)))
              (ac/completed-future (resultset-with nil))))
          get-statement
          (prepared-statement-with [(str hash)] bound-get-statement)
          store (store session get-statement nil)]

      (is (empty? @(rs/multi-get store [hash])))))

  (testing "success"
    (let [content {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate content)
          row (row-with 0 (cheshire/generate-cbor (fhir-spec/unform-cbor content)))
          session
          (reify CqlSession
            (^CompletionStage executeAsync [_ ^Statement statement]
              (when-not (= bound-get-statement statement)
                (throw (Error.)))
              (ac/completed-future (resultset-with row))))
          get-statement
          (prepared-statement-with [(str hash)] bound-get-statement)
          store (store session get-statement nil)]

      (is (= {hash content} @(rs/multi-get store [hash]))))))


(def bound-put-statement (reify BoundStatement))


(defn endpoint [host port]
  (reify EndPoint
    (resolve [_]
      (InetSocketAddress. ^String host ^int port))))


(defn node [endpoint]
  (reify Node
    (getEndPoint [_]
      endpoint)))


(deftest put
  (testing "execute error"
    (let [resource {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate resource)
          encoded-resource (ByteBuffer/wrap (cheshire/generate-cbor (fhir-spec/unform-cbor resource)))
          session
          (reify CqlSession
            (^CompletionStage executeAsync [_ ^Statement _]
              (ac/failed-future (Exception. "error-150216"))))
          put-statement
          (prepared-statement-with
            [(str hash) encoded-resource]
            bound-put-statement)
          store (store session nil put-statement)]

      (try
        @(rs/put store {hash resource})
        (catch Exception e
          (is (= "error-150216" (ex-message (ex-cause e))))))))

  (testing "success"
    (let [resource {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate resource)
          encoded-resource (ByteBuffer/wrap (cheshire/generate-cbor (fhir-spec/unform-cbor resource)))
          session
          (reify CqlSession
            (^CompletionStage executeAsync [_ ^Statement _]
              (ac/completed-future nil)))
          put-statement
          (prepared-statement-with
            [(str hash) encoded-resource]
            bound-put-statement)
          store (store session nil put-statement)]

      (is (nil? @(rs/put store {hash resource}))))))
