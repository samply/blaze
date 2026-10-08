(ns blaze.page-store.backing-store.cassandra-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.byte-buffer :as bb]
   [blaze.cassandra :as cass]
   [blaze.cassandra-spec]
   [blaze.fhir.test-util]
   [blaze.metrics.spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.page-store :as-alias page-store]
   [blaze.page-store.backing-store :as backing-store]
   [blaze.page-store.backing-store-spec]
   [blaze.page-store.backing-store.cassandra :as ps-c]
   [blaze.page-store.backing-store.cassandra.codec :as codec]
   [blaze.page-store.backing-store.cassandra.codec-spec]
   [blaze.page-store.backing-store.cassandra.statement :as statement]
   [blaze.test-util :as tu :refer [given-failed-future]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(declare prepare close config)

(deftest init-test
  (testing "nil config"
    (given-failed-system {::backing-store/cassandra nil}
      :key := ::backing-store/cassandra
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "invalid contact-points"
    (given-failed-system {::backing-store/cassandra {:contact-points ::invalid}}
      :key := ::backing-store/cassandra
      :reason := ::ig/build-failed-spec
      [:value :contact-points] := ::invalid
      [:cause-data ::s/problems 0 :path] := [:contact-points]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "is a backing store"
    (with-redefs
     [cass/session (fn [_] ::session)
      cass/prepare prepare
      cass/close close]
      (with-system [{store ::backing-store/cassandra} config]
        (is (s/valid? ::page-store/backing-store store))))))

(deftest duration-seconds-collector-init-test
  (with-system [{collector ::ps-c/duration-seconds} {::ps-c/duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(deftest resource-bytes-collector-init-test
  (with-system [{collector ::ps-c/resource-bytes} {::ps-c/resource-bytes {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(defn- prepare [session statement]
  (assert (= ::session session))
  (condp = statement
    statement/get-statement
    ::prepared-get-statement
    statement/get-quorum-statement
    ::prepared-get-quorum-statement
    (statement/put-statement "TWO")
    ::prepared-put-statement))

(def clauses [["active" "true"]])
(def token (str/join (repeat 64 "A")))

(defn- bind [prepared-statement & params]
  (condp = prepared-statement
    ::prepared-get-statement
    (do (assert (= [token] params))
        ::bound-get-statement)
    ::prepared-get-quorum-statement
    (do (assert (= [token] params))
        ::bound-get-quorum-statement)
    ::prepared-put-statement
    (do (assert (= [token (bb/wrap (codec/encode clauses))] params))
        ::bound-put-statement)))

(defn- execute [session statement]
  (assert (= ::session session))
  (condp = statement
    ::bound-get-statement
    (ac/completed-future ::result-set)
    ::bound-get-quorum-statement
    (ac/completed-future ::quorum-result-set)
    ::bound-put-statement
    (ac/completed-future ::result-set)))

(defn- close [session]
  (assert (= ::session session)))

(def config
  {::backing-store/cassandra {}
   :blaze.test/fixed-rng {}})

(deftest get-test
  (testing "not found"
    (with-redefs
     [cass/session (fn [_] ::session)
      cass/prepare prepare
      cass/bind bind
      cass/execute execute
      cass/first-row (fn [_] (ba/not-found))
      cass/close close]
      (with-system [{store ::backing-store/cassandra} config]
        (given-failed-future (backing-store/get store token)
          ::anom/category := ::anom/not-found
          ::anom/message := (format "Clauses of token `%s` not found." token)
          ::page-store/token := token))))

  (testing "success after not-found escalation to QUORUM"
    (with-redefs
     [cass/session (fn [_] ::session)
      cass/prepare prepare
      cass/bind bind
      cass/execute execute
      cass/first-row (fn [result-set]
                       (if (= ::result-set result-set)
                         (ba/not-found)
                         (codec/encode clauses)))
      cass/close close]
      (with-system [{store ::backing-store/cassandra} config]
        (is (= clauses @(backing-store/get store token))))))

  (testing "execute error"
    (with-redefs
     [cass/session (fn [_] ::session)
      cass/prepare prepare
      cass/bind bind
      cass/execute (fn [_ _] (ac/completed-future (ba/fault "msg-141754")))
      cass/close close]
      (with-system [{store ::backing-store/cassandra} config]
        (given-failed-future (backing-store/get store token)
          ::anom/category := ::anom/fault
          ::anom/message := "msg-141754"))))

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
      (with-system [{store ::backing-store/cassandra} config]
        @(backing-store/put! store token clauses)

        (is (= clauses @(backing-store/get store token)))))))

(deftest put-test
  (testing "execute error"
    (with-redefs
     [cass/session (fn [_] ::session)
      cass/prepare prepare
      cass/bind bind
      cass/execute (fn [_ _] (ac/completed-future (ba/fault "msg-150216")))
      cass/close close]
      (with-system [{store ::backing-store/cassandra} config]
        (given-failed-future (backing-store/put! store token clauses)
          ::anom/category := ::anom/fault
          ::anom/message := "msg-150216"))))

  (testing "success"
    (with-redefs
     [cass/session (fn [_] ::session)
      cass/prepare prepare
      cass/bind bind
      cass/execute execute
      cass/close close]
      (with-system [{store ::backing-store/cassandra} config]
        (is (nil? @(backing-store/put! store token clauses)))))))
