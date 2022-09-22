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
    [blaze.fhir.spec :as fhir-spec]
    [blaze.log]
    [blaze.test-util :as tu :refer [given-failed-future given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [cuerdas.core :as c-str]
    [integrant.core :as ig]
    [jsonista.core :as j]
    [taoensso.timbre :as log])
  (:import
    [com.datastax.oss.driver.api.core CqlSession DriverTimeoutException ConsistencyLevel]
    [com.datastax.oss.driver.api.core.cql PreparedStatement BoundStatement
                                          Statement Row AsyncResultSet SimpleStatement]
    [com.datastax.oss.driver.api.core.metadata Node EndPoint]
    [com.datastax.oss.driver.api.core.servererrors WriteTimeoutException WriteType]
    [com.fasterxml.jackson.dataformat.cbor CBORFactory]
    [java.net InetSocketAddress]
    [java.nio ByteBuffer]
    #_{:clj-kondo/ignore [:unused-import]}
    [java.util.concurrent CompletionStage]))


(set! *warn-on-reflection* true)
(st/instrument)
(tu/init-fhir-specs)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn hash [s]
  (assert (= 1 (count s)))
  (hash/from-hex (c-str/repeat s 64)))


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


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {::rs/cassandra nil})
      :key := ::rs/cassandra
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "invalid contact-points"
    (given-thrown (ig/init {::rs/cassandra {:contact-points ::invalid}})
      :key := ::rs/cassandra
      :reason := ::ig/build-failed-spec
      [:value :contact-points] := ::invalid
      [:explain ::s/problems 0 :path] := [:contact-points]
      [:explain ::s/problems 0 :val] := ::invalid)))


(def cbor-object-mapper
  (j/object-mapper
    {:factory (CBORFactory.)
     :decode-key-fn true}))


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
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (given-failed-future (rs/get store hash)
            ::anom/message :# "Error while parsing resource content with hash `0000000000000000000000000000000000000000000000000000000000000000`:(.|\\s)*"
            :blaze.resource/hash := hash)))))

  (testing "conforming error"
    (let [hash (hash "0")
          row (row-with 0 (j/write-value-as-bytes {} cbor-object-mapper))
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
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (given-failed-future (rs/get store hash)
            ::anom/message := "Error while conforming resource content with hash `0000000000000000000000000000000000000000000000000000000000000000`."
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
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (is (nil? @(rs/get store hash)))))))

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
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (given-failed-future (rs/get store hash)
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
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (given-failed-future (rs/get store hash)
            ::anom/category := ::anom/busy
            ::anom/message := "Cassandra msg-115452"
            :blaze.resource/hash := hash)))))

  (testing "success"
    (let [content {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate content)
          row (row-with 0 (fhir-spec/unform-cbor content))
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
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (is (= {hash content} @(rs/multi-get store [hash]))))))))


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
          encoded-resource (bb/wrap (fhir-spec/unform-cbor resource))
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
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (given-failed-future (rs/put! store {hash resource})
            ::anom/message := "error-150216")))))

  (testing "DriverTimeoutException"
    (let [resource {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate resource)
          encoded-resource (bb/wrap (fhir-spec/unform-cbor resource))
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
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (given-failed-future (rs/put! store {hash resource})
            ::anom/category := ::anom/busy
            ::anom/message := "Cassandra msg-123234"
            :blaze.resource/hash := hash
            :fhir/type := :fhir/Patient
            :blaze.resource/id := "0")))))

  (testing "WriteTimeoutException"
    (let [resource {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate resource)
          encoded-resource (bb/wrap (fhir-spec/unform-cbor resource))
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
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (given-failed-future (rs/put! store {hash resource})
            ::anom/category := ::anom/busy
            ::anom/message := "Cassandra timeout during SIMPLE write query at consistency TWO (2 replica were required but only 1 acknowledged the write)"
            :blaze.resource/hash := hash
            :fhir/type := :fhir/Patient
            :blaze.resource/id := "0")))))

  (testing "success"
    (let [resource {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate resource)
          encoded-resource (bb/wrap (fhir-spec/unform-cbor resource))
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
        (with-system [{store ::rs/cassandra} {::rs/cassandra {}}]
          (is (nil? @(rs/put! store {hash resource}))))))))
