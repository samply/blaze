(ns blaze.http-client-test
  (:require
    [blaze.http-client]
    [clojure.core.protocols :refer [Datafiable]]
    [clojure.datafy :refer [datafy]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [integrant.core :as ig]
    [java-time :as jt]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [java.net.http HttpClient]
    [java.util Optional]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn- http-client [opts]
  (-> {:blaze/http-client opts} ig/init :blaze/http-client))


(extend-protocol Datafiable
  Optional
  (datafy [optional]
    (when (.isPresent optional)
      (datafy (.get optional))))
  HttpClient
  (datafy [client]
    {:connect-timeout (datafy (.connectTimeout client))}))


(deftest http-client-test
  (testing "without options"
    (given (datafy (http-client {}))
      :connect-timeout := (jt/millis 5000)))

  (testing "with 2 seconds connect timeout"
    (given (datafy (http-client {:connect-timeout 2000}))
      :connect-timeout := (jt/millis 2000)))

  (testing "fails with string connect timeout"
    (is (thrown? Exception (http-client {:connect-timeout "2000"})))))
