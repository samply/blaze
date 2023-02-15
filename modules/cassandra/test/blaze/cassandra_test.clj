(ns blaze.cassandra-test
  (:require
    [blaze.anomaly :as ba]
    [blaze.async.comp :as ac]
    [blaze.byte-buffer :as bb]
    [blaze.cassandra :as cass]
    [blaze.cassandra-spec]
    [blaze.test-util :as tu :refer [satisfies-prop]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]])
  (:import
    [com.datastax.oss.driver.api.core
     ConsistencyLevel CqlSession DriverTimeoutException
     RequestThrottlingException]
    [com.datastax.oss.driver.api.core.cql
     AsyncResultSet BoundStatement PreparedStatement Row SimpleStatement
     Statement]
    [com.datastax.oss.driver.api.core.servererrors
     WriteTimeoutException WriteType]
    [java.nio ByteBuffer]
    [java.util.concurrent CompletionStage]))


(set! *warn-on-reflection* true)
(st/instrument)


(test/use-fixtures :each tu/fixture)


(deftest prepare-test
  (let [given-statement (reify SimpleStatement)
        prepared-statement (reify PreparedStatement)
        session
        (reify CqlSession
          (^PreparedStatement prepare [_ ^SimpleStatement statement]
            (assert (= given-statement statement))
            prepared-statement))]
    (is (= prepared-statement (cass/prepare session given-statement)))))


(deftest bind-test
  (testing "bind nothing"
    (let [bound-statement (reify BoundStatement)
          statement
          (reify PreparedStatement
            (bind [_ values]
              (assert (empty? values))
              bound-statement))]
      (is (= bound-statement (cass/bind statement)))))

  (testing "bind one value"
    (let [bound-statement (reify BoundStatement)
          statement
          (reify PreparedStatement
            (bind [_ values]
              (assert (= [::value] (vec values)))
              bound-statement))]
      (is (= bound-statement (cass/bind statement ::value)))))

  (testing "bind two values"
    (let [bound-statement (reify BoundStatement)
          statement
          (reify PreparedStatement
            (bind [_ values]
              (assert (= [::v1 ::v2] (vec values)))
              bound-statement))]
      (is (= bound-statement (cass/bind statement ::v1 ::v2))))))


(deftest execute-test
  (let [given-statement (reify Statement)
        result-set (reify AsyncResultSet)
        session
        (reify CqlSession
          (^CompletionStage executeAsync [_ ^Statement statement]
            (assert (= given-statement statement))
            (ac/completed-future result-set)))]
    (is (= result-set @(cass/execute session given-statement)))))


(defn row-with [idx bytes]
  (reify Row
    (^ByteBuffer getByteBuffer [_ ^int i]
      (assert (= idx i))
      (bb/wrap (byte-array bytes)))))


(defn resultset-with [row]
  (reify AsyncResultSet
    (one [_]
      row)))


(deftest first-row-test
  (testing "with one row"
    (satisfies-prop 100
      (prop/for-all [bytes (gen/vector gen/byte)]
        (= bytes (vec (cass/first-row (resultset-with (row-with 0 bytes))))))))

  (testing "with no row"
    (given (cass/first-row (resultset-with nil))
      ::anom/category := ::anom/not-found)))


(deftest format-config-test
  (testing "empty config"
    (is (= "" (cass/format-config {}))))

  (testing "normal value"
    (is (= "foo = bar" (cass/format-config {:foo "bar"}))))

  (testing "nil value is ignored"
    (is (= "" (cass/format-config {:foo nil}))))

  (testing "passwords are hidden"
    (is (= "password = [hidden]" (cass/format-config {:password "secret"})))))


(deftest close-test
  (let [state (atom ::open)
        session
        (reify CqlSession
          (close [_]
            (reset! state ::closed)))]
    (cass/close session)
    (is (= ::closed @state))))


(deftest anomaly-test
  (testing "DriverTimeoutException"
    (given (ba/anomaly (DriverTimeoutException. "msg-162625"))
      ::anom/category := ::anom/busy
      ::anom/message := "Cassandra msg-162625"))

  (testing "WriteTimeoutException"
    (given (ba/anomaly (WriteTimeoutException. nil ConsistencyLevel/TWO 1 2 WriteType/SIMPLE))
      ::anom/category := ::anom/busy
      ::anom/message := "Cassandra timeout during SIMPLE write query at consistency TWO (2 replica were required but only 1 acknowledged the write)"))

  (testing "RequestThrottlingException"
    (given (ba/anomaly (RequestThrottlingException. "msg-163725"))
      ::anom/category := ::anom/busy
      ::anom/message := "Cassandra msg-163725")))
