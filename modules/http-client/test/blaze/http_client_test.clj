(ns blaze.http-client-test
  (:require
   [blaze.http-client]
   [blaze.http-client.spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.core.protocols :refer [Datafiable]]
   [clojure.datafy :refer [datafy]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [java.net.http HttpClient]
   [java.util Optional]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(extend-protocol Datafiable
  Optional
  (datafy [optional]
    (when (.isPresent optional)
      (datafy (.get optional))))
  HttpClient
  (datafy [client]
    {:connect-timeout (datafy (.connectTimeout client))}))

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze/http-client nil})
      :key := :blaze/http-client
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "invalid caches"
    (given-thrown (ig/init {:blaze/http-client {:connect-timeout ::invalid}})
      :key := :blaze/http-client
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `pos-int?
      [:explain ::s/problems 0 :val] := ::invalid)))

(deftest http-client-test
  (testing "without options"
    (with-system [{:blaze/keys [http-client]} {:blaze/http-client {}}]
      (given (datafy http-client)
        :connect-timeout := (time/millis 5000))))

  (testing "with 2 seconds connect timeout"
    (with-system [{:blaze/keys [http-client]} {:blaze/http-client {:connect-timeout 2000}}]
      (given (datafy http-client)
        :connect-timeout := (time/millis 2000)))))

(deftest spec-test
  (with-system [{:blaze/keys [http-client]} {:blaze/http-client {}}]
    (is (s/valid? :blaze/http-client http-client))))
