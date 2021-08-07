(ns blaze.db.resource-store.cassandra-test
  (:refer-clojure :exclude [hash])
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
    [blaze.test-util :refer [given-thrown with-system]]
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
    [java.util.concurrent CompletionStage]))


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
  (testing "nil config"
    (given-thrown (ig/init {::rs/cassandra nil})
      :key := ::rs/cassandra
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "invalid contact-points"
    (given-thrown (ig/init {::rs/cassandra {:contact-points "localhost"}})
      :key := ::rs/cassandra
      :reason := ::ig/build-failed-spec
      [:value :contact-points] := "localhost"
      [:explain ::s/problems 0 :path] := [:contact-points]
      [:explain ::s/problems 0 :val] := "localhost")))


(defn- catch-cause [stage]
  (ac/exceptionally stage ex-cause))


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
            (close [_]))]

      (with-redefs [cass/session (fn [_ _] session)]
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (given @(catch-cause (rs/get store hash))
            ex-message :# "Error while parsing resource content(.|\\s)*"
            [ex-data ::anom/message] :# "Error while parsing resource content(.|\\s)*")))))

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
            (close [_]))]

      (with-redefs [cass/session (fn [_ _] session)]
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (is (nil? @(rs/get store hash)))))))

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
            (close [_]))]

      (with-redefs [cass/session (fn [_ _] session)]
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (given @(catch-cause (rs/get store hash))
            ex-message := "msg-141754"
            [ex-data ::anom/category] := ::anom/fault
            [ex-data :blaze.resource/hash] := hash)))))

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
            (close [_]))]

      (with-redefs [cass/session (fn [_ _] session)]
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (given @(catch-cause (rs/get store hash))
            ex-message := "Cassandra msg-115452"
            [ex-data ::anom/category] := ::anom/busy
            [ex-data :blaze.resource/hash] := hash)))))

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
            (close [_]))]

      (with-redefs [cass/session (fn [_ _] session)]
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (is (= content @(rs/get store hash)))))))

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
            (^CompletionStage executeAsync [_ ^Statement _]
              (if @throw-timeout?
                (do (vreset! throw-timeout? false)
                    (ac/failed-future (DriverTimeoutException. "foo")))
                (ac/completed-future (resultset-with row))))
            (close [_]))]

      (with-redefs [cass/session (fn [_ _] session)]
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (is (= content @(rs/get store hash))))))))


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
            (close [_]))]

      (with-redefs [cass/session (fn [_ _] session)]
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (is (empty? @(rs/multi-get store [hash])))))))

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
            (close [_]))]

      (with-redefs [cass/session (fn [_ _] session)]
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (is (= {hash content} @(rs/multi-get store [hash]))))))))


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
            (close [_]))]

      (with-redefs [cass/session (fn [_ _] session)]
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (given @(catch-cause (rs/put! store {hash resource}))
            ex-message := "error-150216")))))

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
            (close [_]))]

      (with-redefs [cass/session (fn [_ _] session)]
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (given @(catch-cause (rs/put! store {hash resource}))
            ex-message := "Cassandra msg-123234"
            [ex-data ::anom/category] := ::anom/busy
            [ex-data :blaze.resource/hash] := hash
            [ex-data :fhir/type] := :fhir/Patient
            [ex-data :blaze.resource/id] := "0")))))

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
            (close [_]))]

      (with-redefs [cass/session (fn [_ _] session)]
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (given @(catch-cause (rs/put! store {hash resource}))
            ex-message := "Cassandra timeout during SIMPLE write query at consistency TWO (2 replica were required but only 1 acknowledged the write)"
            [ex-data ::anom/category] := ::anom/busy
            [ex-data :blaze.resource/hash] := hash
            [ex-data :fhir/type] := :fhir/Patient
            [ex-data :blaze.resource/id] := "0")))))

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
            (close [_]))]

      (with-redefs [cass/session (fn [_ _] session)]
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (is (nil? @(rs/put! store {hash resource}))))))))
