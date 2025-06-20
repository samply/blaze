(ns blaze.page-store.cassandra-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.byte-buffer :as bb]
   [blaze.cassandra :as cass]
   [blaze.cassandra-spec]
   [blaze.fhir.test-util]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.page-store :as page-store]
   [blaze.page-store.cassandra]
   [blaze.page-store.cassandra.codec :as codec]
   [blaze.page-store.cassandra.codec-spec]
   [blaze.page-store.cassandra.statement :as statement]
   [blaze.page-store.token-spec]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest init-test
  (testing "nil config"
    (given-failed-system {::page-store/cassandra nil}
      :key := ::page-store/cassandra
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "invalid contact-points"
    (given-failed-system {::page-store/cassandra {:contact-points ::invalid}}
      :key := ::page-store/cassandra
      :reason := ::ig/build-failed-spec
      [:value :contact-points] := ::invalid
      [:cause-data ::s/problems 0 :path] := [:contact-points]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(defn- prepare [session statement]
  (assert (= ::session session))
  (condp = statement
    statement/get-statement
    ::prepared-get-statement
    (statement/put-statement "TWO")
    ::prepared-put-statement))

(def clauses [["active" "true"]])
(def token "A6E4E6D1E2ADB75120717FE913FA5EBADDF0859588A657AFF71F270775B5FEC7")

(defn- bind [prepared-statement & params]
  (condp = prepared-statement
    ::prepared-get-statement
    (do (assert (= [token] params))
        ::bound-get-statement)
    ::prepared-put-statement
    (do (assert (= [token (bb/wrap (codec/encode clauses))] params))
        ::bound-put-statement)))

(defn- execute [session statement]
  (assert (= ::session session))
  (condp = statement
    ::bound-get-statement
    (ac/completed-future ::result-set)
    ::bound-put-statement
    (ac/completed-future ::result-set)))

(defn- close [session]
  (assert (= ::session session)))

(def config
  {::page-store/cassandra {}
   :blaze.test/fixed-rng {}})

(deftest get-test
  (testing "success"
    (with-redefs
     [cass/session (fn [_] ::session)
      cass/prepare prepare
      cass/bind bind
      cass/execute execute
      cass/first-row (fn [result-set]
                       (assert (= ::result-set result-set))
                       (codec/encode clauses))
      cass/close close]
      (with-system [{store ::page-store/cassandra} config]
        @(page-store/put! store clauses)

        (is (= clauses @(page-store/get store token)))))))

(deftest put-test
  (testing "success"
    (with-redefs
     [cass/session (fn [_] ::session)
      cass/prepare prepare
      cass/bind bind
      cass/execute execute
      cass/close close]
      (with-system [{store ::page-store/cassandra} config]
        (is (= token @(page-store/put! store clauses)))))))
